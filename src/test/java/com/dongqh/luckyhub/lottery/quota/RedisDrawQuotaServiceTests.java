package com.dongqh.luckyhub.lottery.quota;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.lottery.config.LotteryProperties;
import com.dongqh.luckyhub.lottery.enums.LotteryErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RedisDrawQuotaServiceTests {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Instant FIXED_NOW = Instant.parse("2026-07-31T15:59:00Z");

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private LotteryProperties properties;

    private final Set<String> reservationKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<String> quotaKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<String> requestIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private RedisDrawQuotaService service;

    @BeforeEach
    void setUp() {
        service = new RedisDrawQuotaService(
                redisTemplate,
                properties,
                Clock.fixed(FIXED_NOW, SHANGHAI)
        );
    }

    @AfterEach
    void cleanOnlyExplicitTestKeys() {
        if (!reservationKeys.isEmpty()) {
            redisTemplate.delete(reservationKeys);
        }
        if (!quotaKeys.isEmpty()) {
            redisTemplate.delete(quotaKeys);
        }
        if (!requestIds.isEmpty()) {
            redisTemplate.opsForZSet().remove(DrawQuotaKeys.reservationTimeouts(), requestIds.toArray());
        }
    }

    @Test
    void reservesSingleAndTenDrawsAtomically() {
        long activityId = uniqueLong();
        long userId = uniqueLong();

        QuotaReservationResult single = reserve(activityId, userId, 1, 20);
        QuotaReservationResult ten = reserve(activityId, userId, 10, 20);

        assertThat(single.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(single.duplicate()).isFalse();
        assertThat(ten.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(redisTemplate.opsForValue().get(quotaKey(activityId, userId)))
                .isEqualTo("11");
    }

    @Test
    void rejectsWholeTenDrawWhenRemainingQuotaIsInsufficient() {
        long activityId = uniqueLong();
        long userId = uniqueLong();
        reserve(activityId, userId, 1, 10);

        assertThatThrownBy(() -> reserve(activityId, userId, 10, 10))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(LotteryErrorCode.TEN_DRAW_QUOTA_EXCEEDED);
        assertThat(redisTemplate.opsForValue().get(quotaKey(activityId, userId)))
                .isEqualTo("1");
    }

    @Test
    void duplicateRequestDoesNotConsumeQuotaAndDifferentParametersConflict() {
        long activityId = uniqueLong();
        long userId = uniqueLong();
        String requestId = requestId();
        QuotaReservationRequest original = request(requestId, activityId, userId, 1, 5);

        QuotaReservationResult first = service.reserve(original);
        QuotaReservationResult duplicate = service.reserve(original);

        assertThat(first.duplicate()).isFalse();
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(redisTemplate.opsForValue().get(quotaKey(activityId, userId))).isEqualTo("1");
        assertThatThrownBy(() -> service.reserve(request(requestId, activityId, userId, 10, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(LotteryErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    void duplicateAfterShanghaiMidnightReturnsOriginalDrawDateWithoutUsingNewDayQuota() {
        long activityId = uniqueLong();
        long userId = uniqueLong();
        String requestId = requestId();
        QuotaReservationRequest original = request(requestId, activityId, userId, 1, 5);
        service.reserve(original);
        RedisDrawQuotaService nextDayService = new RedisDrawQuotaService(
                redisTemplate,
                properties,
                Clock.fixed(Instant.parse("2026-07-31T16:01:00Z"), SHANGHAI)
        );

        QuotaReservationResult duplicate = nextDayService.reserve(original);

        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.drawDate()).isEqualTo(LocalDate.of(2026, 7, 31));
        String nextDayQuota = DrawQuotaKeys.quota(activityId, userId, LocalDate.of(2026, 8, 1));
        quotaKeys.add(nextDayQuota);
        assertThat(redisTemplate.hasKey(nextDayQuota)).isFalse();
    }

    @Test
    void changedDailyLimitStillReturnsDuplicateFromOriginalReservation() {
        long activityId = uniqueLong();
        long userId = uniqueLong();
        String requestId = requestId();
        service.reserve(request(requestId, activityId, userId, 1, 5));
        RedisDrawQuotaService nextDayService = new RedisDrawQuotaService(
                redisTemplate,
                properties,
                Clock.fixed(Instant.parse("2026-07-31T16:01:00Z"), SHANGHAI)
        );

        QuotaReservationResult duplicate = nextDayService.reserve(
                request(requestId, activityId, userId, 1, 99));

        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.drawDate()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(redisTemplate.opsForValue().get(quotaKey(activityId, userId))).isEqualTo("1");
        String nextDayQuota = DrawQuotaKeys.quota(activityId, userId, LocalDate.of(2026, 8, 1));
        quotaKeys.add(nextDayQuota);
        assertThat(redisTemplate.hasKey(nextDayQuota)).isFalse();
    }

    @Test
    void storesShanghaiDrawDateMetadataTimeoutIndexAndDateAnchoredTtl() {
        long activityId = uniqueLong();
        long userId = uniqueLong();
        String requestId = requestId();

        service.reserve(request(requestId, activityId, userId, 1, 5));

        String reservationKey = DrawQuotaKeys.reservation(requestId);
        Map<Object, Object> reservation = redisTemplate.opsForHash().entries(reservationKey);
        assertThat(reservation).containsEntry("userId", Long.toString(userId))
                .containsEntry("activityId", Long.toString(activityId))
                .containsEntry("drawCount", "1")
                .containsEntry("drawDate", "20260731")
                .containsEntry("status", "RESERVED")
                .containsEntry("createdAt", Long.toString(FIXED_NOW.toEpochMilli()));
        assertThat(redisTemplate.opsForZSet().score(DrawQuotaKeys.reservationTimeouts(), requestId))
                .isEqualTo((double) FIXED_NOW.plus(properties.processingTimeout()).toEpochMilli());

        Long reservationTtl = redisTemplate.getExpire(reservationKey, TimeUnit.MILLISECONDS);
        Long quotaTtl = redisTemplate.getExpire(quotaKey(activityId, userId), TimeUnit.MILLISECONDS);
        long expiresAt = LocalDate.of(2026, 7, 31).atStartOfDay(SHANGHAI).toInstant()
                .plus(properties.reservationRetention()).toEpochMilli();
        long expected = expiresAt - Instant.now().toEpochMilli();
        assertThat(reservationTtl).isBetween(expected - 5_000, expected + 1_000);
        assertThat(quotaTtl).isBetween(expected - 5_000, expected + 1_000);
    }

    @Test
    void confirmAndReleaseAreIdempotentAndRemoveTimeoutMembership() {
        long activityId = uniqueLong();
        long userId = uniqueLong();
        String confirmedId = requestId();
        String releasedId = requestId();
        service.reserve(request(confirmedId, activityId, userId, 1, 20));
        service.reserve(request(releasedId, activityId, userId, 10, 20));

        service.confirm(confirmedId);
        service.confirm(confirmedId);
        service.release(confirmedId);
        service.release(releasedId);
        service.release(releasedId);

        assertThat(status(confirmedId)).isEqualTo("CONFIRMED");
        assertThat(status(releasedId)).isEqualTo("RELEASED");
        assertThat(redisTemplate.opsForValue().get(quotaKey(activityId, userId))).isEqualTo("1");
        assertThat(redisTemplate.opsForZSet().score(DrawQuotaKeys.reservationTimeouts(), confirmedId)).isNull();
        assertThat(redisTemplate.opsForZSet().score(DrawQuotaKeys.reservationTimeouts(), releasedId)).isNull();
    }

    @Test
    void releaseRejectsReservationHashWithMissingIdentityWithoutChangingStateOrTimeout() {
        String requestId = requestId();
        String reservationKey = trackReservation(requestId);
        redisTemplate.opsForHash().putAll(reservationKey, Map.of(
                "activityId", "123",
                "userId", "456",
                "drawCount", "1",
                "status", "RESERVED"
        ));
        redisTemplate.opsForZSet().add(DrawQuotaKeys.reservationTimeouts(), requestId, 123D);

        assertThatThrownBy(() -> service.release(requestId))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(LotteryErrorCode.DRAW_QUOTA_UNAVAILABLE);
        assertThat(status(requestId)).isEqualTo("RESERVED");
        assertThat(redisTemplate.opsForZSet().score(DrawQuotaKeys.reservationTimeouts(), requestId))
                .isEqualTo(123D);
    }

    @Test
    void confirmRejectsReservationHashWithMissingIdentityWithoutChangingStateOrTimeout() {
        String requestId = requestId();
        String reservationKey = trackReservation(requestId);
        redisTemplate.opsForHash().putAll(reservationKey, Map.of(
                "activityId", "123",
                "drawCount", "1",
                "drawDate", "20260731",
                "status", "RESERVED"
        ));
        redisTemplate.opsForZSet().add(DrawQuotaKeys.reservationTimeouts(), requestId, 456D);

        assertThatThrownBy(() -> service.confirm(requestId))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(LotteryErrorCode.DRAW_QUOTA_UNAVAILABLE);
        assertThat(status(requestId)).isEqualTo("RESERVED");
        assertThat(redisTemplate.opsForZSet().score(DrawQuotaKeys.reservationTimeouts(), requestId))
                .isEqualTo(456D);
    }

    @Test
    void releaseNeverMakesQuotaNegative() {
        long activityId = uniqueLong();
        long userId = uniqueLong();
        String requestId = requestId();
        service.reserve(request(requestId, activityId, userId, 10, 10));
        redisTemplate.opsForValue().set(quotaKey(activityId, userId), "3");

        service.release(requestId);

        assertThat(redisTemplate.opsForValue().get(quotaKey(activityId, userId))).isEqualTo("0");
    }

    @Test
    void oneHundredConcurrentReservationsNeverExceedDailyLimit() throws Exception {
        long activityId = uniqueLong();
        long userId = uniqueLong();
        int dailyLimit = 37;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 100; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        reserve(activityId, userId, 1, dailyLimit);
                        successes.incrementAndGet();
                    } catch (BusinessException error) {
                        assertThat(error.getErrorCode()).isEqualTo(LotteryErrorCode.DAILY_QUOTA_EXCEEDED);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(successes).hasValue(dailyLimit);
        assertThat(redisTemplate.opsForValue().get(quotaKey(activityId, userId)))
                .isEqualTo(Integer.toString(dailyLimit));
    }

    private QuotaReservationResult reserve(long activityId, long userId, int count, int limit) {
        return service.reserve(request(requestId(), activityId, userId, count, limit));
    }

    private QuotaReservationRequest request(String requestId, long activityId, long userId, int count, int limit) {
        reservationKeys.add(DrawQuotaKeys.reservation(requestId));
        quotaKeys.add(quotaKey(activityId, userId));
        requestIds.add(requestId);
        return new QuotaReservationRequest(requestId, activityId, userId, count, limit);
    }

    private String requestId() {
        return "task6-test-" + UUID.randomUUID();
    }

    private String trackReservation(String requestId) {
        String reservationKey = DrawQuotaKeys.reservation(requestId);
        reservationKeys.add(reservationKey);
        requestIds.add(requestId);
        return reservationKey;
    }

    private String quotaKey(long activityId, long userId) {
        return DrawQuotaKeys.quota(activityId, userId, LocalDate.of(2026, 7, 31));
    }

    private String status(String requestId) {
        return (String) redisTemplate.opsForHash().get(DrawQuotaKeys.reservation(requestId), "status");
    }

    private long uniqueLong() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits());
    }
}
