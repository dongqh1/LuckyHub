package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.lottery.entity.LotteryDrawOrder;
import com.dongqh.luckyhub.lottery.enums.DrawOrderStatus;
import com.dongqh.luckyhub.lottery.lock.DrawLockService;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawOrderMapper;
import com.dongqh.luckyhub.lottery.model.NewDrawOrder;
import com.dongqh.luckyhub.lottery.model.ReconciliationResult;
import com.dongqh.luckyhub.lottery.quota.DrawQuotaKeys;
import com.dongqh.luckyhub.lottery.quota.DrawQuotaService;
import com.dongqh.luckyhub.lottery.quota.QuotaReservationRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "luckyhub.lottery.reconciliation-enabled=false",
        "spring.data.redis.database=15"
})
class LotteryReconciliationServiceTests {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long USER_ID = 880013L;
    private static final long ACTIVITY_ID = 990013L;

    @Autowired private LotteryReconciliationService service;
    @Autowired private DrawOrderLifecycleService lifecycleService;
    @Autowired private LotteryDrawOrderMapper orderMapper;
    @Autowired private DrawQuotaService quotaService;
    @Autowired private DrawLockService lockService;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    private final Set<String> requestIds = new LinkedHashSet<>();
    private final Set<String> quotaKeys = new LinkedHashSet<>();
    private final Map<String, LocalDate> drawDates = new LinkedHashMap<>();

    @AfterEach
    void cleanUp() {
        for (String requestId : requestIds) {
            jdbcTemplate.update("DELETE FROM message_outbox WHERE aggregate_id IN "
                    + "(SELECT CAST(id AS CHAR) FROM lottery_draw_order WHERE request_id = ?)", requestId);
            jdbcTemplate.update("DELETE FROM lottery_draw_order WHERE request_id = ?", requestId);
            redisTemplate.delete(DrawQuotaKeys.reservation(requestId));
            redisTemplate.opsForZSet().remove(DrawQuotaKeys.reservationTimeouts(), requestId);
        }
        redisTemplate.delete(quotaKeys);
        quotaKeys.clear();
        drawDates.clear();
        requestIds.clear();
    }

    @Test
    void confirmsSuccessfulOrderAndRemovesTimeoutMember() {
        String requestId = reserveDue();
        LotteryDrawOrder order = createOrder(requestId, LocalDateTime.now().minusMinutes(3));
        jdbcTemplate.update("UPDATE lottery_draw_order SET status = 'SUCCESS' WHERE id = ?", order.getId());

        ReconciliationResult result = service.reconcileExpiredReservations(Instant.now());

        assertThat(status(requestId)).isEqualTo("CONFIRMED");
        assertThat(score(requestId)).isNull();
        assertThat(result.confirmed()).isEqualTo(1);
    }

    @Test
    void releasesFailedAndMissingOrdersWithoutStoppingTheBatch() {
        String failed = reserveDue();
        LotteryDrawOrder failedOrder = createOrder(failed, LocalDateTime.now().minusMinutes(3));
        jdbcTemplate.update("UPDATE lottery_draw_order SET status = 'FAILED' WHERE id = ?", failedOrder.getId());
        String missing = reserveDue();

        ReconciliationResult result = service.reconcileExpiredReservations(Instant.now());

        assertThat(status(failed)).isEqualTo("RELEASED");
        assertThat(status(missing)).isEqualTo("RELEASED");
        assertThat(result.released()).isEqualTo(2);
    }

    @Test
    void freshProcessingOrderIsLeftProcessingAndRescheduledToItsRealDeadline() {
        String requestId = reserveDue();
        LotteryDrawOrder order = createOrder(requestId, LocalDateTime.now());

        ReconciliationResult result = service.reconcileExpiredReservations(Instant.now());

        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(DrawOrderStatus.PROCESSING);
        assertThat(status(requestId)).isEqualTo("RESERVED");
        assertThat(score(requestId)).isGreaterThan(Instant.now().toEpochMilli());
        assertThat(result.deferred()).isEqualTo(1);
    }

    @Test
    void expiredProcessingOrderBecomesFailedAndAppendsReleaseEventAtomically() {
        String requestId = reserveDue();
        LotteryDrawOrder order = createOrder(requestId, LocalDateTime.now().minusMinutes(3));

        ReconciliationResult result = service.reconcileExpiredReservations(Instant.now());

        LotteryDrawOrder stored = orderMapper.selectById(order.getId());
        assertThat(stored.getStatus()).isEqualTo(DrawOrderStatus.FAILED);
        assertThat(stored.getFailReason()).isEqualTo("PROCESSING_TIMEOUT");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM message_outbox
                WHERE aggregate_id = ? AND event_type = 'DRAW_RELEASE_REQUESTED'
                """, Integer.class, Long.toString(order.getId()))).isOne();
        assertThat(status(requestId)).isEqualTo("RESERVED");
        assertThat(result.timedOut()).isEqualTo(1);
    }

    @Test
    void concurrentSuccessWinnerIsReloadedAndConfirmedInsteadOfOverwritten() throws Exception {
        String requestId = reserveDue();
        LotteryDrawOrder order = createOrder(requestId, LocalDateTime.now().minusMinutes(3));
        CountDownLatch reconciliationReachedTransition = new CountDownLatch(1);
        CountDownLatch successCommitted = new CountDownLatch(1);
        DrawOrderLifecycleService controlledLifecycle = new DrawOrderLifecycleService() {
            @Override public LotteryDrawOrder createProcessing(NewDrawOrder command) { throw new UnsupportedOperationException(); }
            @Override public void markFailed(long orderId, String safeReason) { throw new UnsupportedOperationException(); }
            @Override public void markFailedAndRequestRelease(LotteryDrawOrder candidate, String reason,
                                                               LocalDateTime occurredAt) {
                reconciliationReachedTransition.countDown();
                await(successCommitted);
                lifecycleService.markFailedAndRequestRelease(candidate, reason, occurredAt);
            }
        };
        LotteryReconciliationService controlled = new LotteryReconciliationServiceImpl(
                redisTemplate, orderMapper, controlledLifecycle, quotaService, lockService,
                Duration.ofMinutes(2), ZONE, 100);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ReconciliationResult> reconciliation = executor.submit(
                    () -> controlled.reconcileExpiredReservations(Instant.now()));
            assertThat(reconciliationReachedTransition.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Integer> success = executor.submit(() -> {
                int updated = orderMapper.markSuccessIfProcessing(order.getId(), LocalDateTime.now());
                successCommitted.countDown();
                return updated;
            });

            assertThat(success.get(5, TimeUnit.SECONDS)).isOne();
            ReconciliationResult result = reconciliation.get(5, TimeUnit.SECONDS);
            assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(DrawOrderStatus.SUCCESS);
            assertThat(status(requestId)).isEqualTo("CONFIRMED");
            assertThat(result.confirmed()).isOne();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM message_outbox WHERE aggregate_id = ?",
                    Integer.class, Long.toString(order.getId()))).isZero();
        } finally {
            successCommitted.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void readsOnlyConfiguredBatchFromTimeoutSortedSet() {
        String first = reserveDue();
        String second = reserveDue();
        String third = reserveDue();
        LotteryReconciliationService bounded = new LotteryReconciliationServiceImpl(
                redisTemplate, orderMapper, lifecycleService, quotaService, lockService,
                Duration.ofMinutes(2), ZONE, 2);

        ReconciliationResult result = bounded.reconcileExpiredReservations(Instant.now());

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(java.util.List.of(status(first), status(second), status(third)))
                .containsExactlyInAnyOrder("RESERVED", "RELEASED", "RELEASED");
        assertThat(requestIds.stream().filter(id -> score(id) != null)).hasSize(1);
    }

    @Test
    void oneDamagedReservationDoesNotPreventTheNextRequestFromBeingReleased() {
        String damaged = reserveDue();
        String healthy = reserveDue();
        redisTemplate.opsForHash().delete(DrawQuotaKeys.reservation(damaged), "activityId");
        redisTemplate.opsForZSet().add(DrawQuotaKeys.reservationTimeouts(), damaged,
                Instant.now().minusSeconds(2).toEpochMilli());

        ReconciliationResult result = service.reconcileExpiredReservations(Instant.now());

        assertThat(result.failed()).isOne();
        assertThat(result.released()).isOne();
        assertThat(status(damaged)).isEqualTo("RESERVED");
        assertThat(score(damaged)).isGreaterThan(Instant.now().toEpochMilli());
        assertThat(status(healthy)).isEqualTo("RELEASED");
    }

    @Test
    void concurrentWorkersConfirmTheSameReservationIdempotently() throws Exception {
        String requestId = reserveDue();
        LotteryDrawOrder order = createOrder(requestId, LocalDateTime.now().minusMinutes(3));
        jdbcTemplate.update("UPDATE lottery_draw_order SET status = 'SUCCESS' WHERE id = ?", order.getId());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<ReconciliationResult> first = executor.submit(() -> {
                await(start);
                return service.reconcileExpiredReservations(Instant.now());
            });
            Future<ReconciliationResult> second = executor.submit(() -> {
                await(start);
                return service.reconcileExpiredReservations(Instant.now());
            });
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            assertThat(status(requestId)).isEqualTo("CONFIRMED");
            assertThat(score(requestId)).isNull();
            assertThat(quotaValue(requestId)).isEqualTo("1");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void reconciliationWaitsForTheCreatorLockAndCannotReleaseBetweenReserveAndOrderCreation()
            throws Exception {
        String requestId = newRequestId();
        CountDownLatch reservedWhileLocked = new CountDownLatch(1);
        CountDownLatch reconciliationRequestedLock = new CountDownLatch(1);
        CountDownLatch allowCreatorToFinish = new CountDownLatch(1);
        DrawLockService observableLock = new DrawLockService() {
            @Override
            public <T> T execute(long activityId, long userId, java.util.function.Supplier<T> action) {
                reconciliationRequestedLock.countDown();
                return lockService.execute(activityId, userId, action);
            }
        };
        LotteryReconciliationService controlled = new LotteryReconciliationServiceImpl(
                redisTemplate, orderMapper, lifecycleService, quotaService, observableLock,
                Duration.ofMillis(50), ZONE, 100);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> creator = executor.submit(() -> lockService.execute(ACTIVITY_ID, USER_ID, () -> {
                reserveExistingRequestDue(requestId);
                reservedWhileLocked.countDown();
                await(allowCreatorToFinish);
                LotteryDrawOrder order = createOrder(requestId, LocalDateTime.now());
                assertThat(orderMapper.markSuccessIfProcessing(order.getId(), LocalDateTime.now())).isOne();
                return null;
            }));
            assertThat(reservedWhileLocked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<ReconciliationResult> reconciliation = executor.submit(
                    () -> controlled.reconcileExpiredReservations(Instant.now()));
            assertThat(reconciliationRequestedLock.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100);
            assertThat(status(requestId)).isEqualTo("RESERVED");
            allowCreatorToFinish.countDown();

            creator.get(5, TimeUnit.SECONDS);
            assertThat(reconciliation.get(5, TimeUnit.SECONDS).confirmed()).isOne();
            assertThat(status(requestId)).isEqualTo("CONFIRMED");
        } finally {
            allowCreatorToFinish.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void reservationWhoseCreatorCrashesAfterUnlockIsReleased() {
        String requestId = newRequestId();
        lockService.execute(ACTIVITY_ID, USER_ID, () -> {
            reserveExistingRequestDue(requestId);
            return null;
        });

        service.reconcileExpiredReservations(Instant.now());

        assertThat(status(requestId)).isEqualTo("RELEASED");
    }

    @Test
    void reservationAndOrderIdentityMismatchIsDeferredWithoutChangingQuota() {
        String requestId = reserveDue();
        createOrder(requestId, LocalDateTime.now().minusMinutes(3));
        redisTemplate.opsForHash().put(DrawQuotaKeys.reservation(requestId), "drawCount", "10");

        ReconciliationResult result = service.reconcileExpiredReservations(Instant.now());

        assertThat(result.failed()).isOne();
        assertThat(status(requestId)).isEqualTo("RESERVED");
        assertThat(quotaValue(requestId)).isEqualTo("1");
        assertThat(score(requestId)).isGreaterThan(Instant.now().toEpochMilli());
    }

    @Test
    void malformedReservedIdentityIsQuarantinedButMalformedTerminalIdentityOnlyCleansTimeout() {
        String reserved = reserveDue();
        redisTemplate.opsForHash().put(DrawQuotaKeys.reservation(reserved), "drawDate", "bad-date");
        String terminal = reserveDue();
        quotaService.confirm(terminal);
        redisTemplate.opsForHash().delete(DrawQuotaKeys.reservation(terminal), "userId");
        redisTemplate.opsForZSet().add(DrawQuotaKeys.reservationTimeouts(), terminal,
                Instant.now().minusSeconds(1).toEpochMilli());

        ReconciliationResult result = service.reconcileExpiredReservations(Instant.now());

        assertThat(result.failed()).isOne();
        assertThat(status(reserved)).isEqualTo("RESERVED");
        assertThat(score(reserved)).isGreaterThan(Instant.now().toEpochMilli());
        assertThat(status(terminal)).isEqualTo("CONFIRMED");
        assertThat(score(terminal)).isNull();
        assertThat(quotaValue(terminal)).isEqualTo("2");
    }

    @Test
    void everyMalformedReservedIdentityFieldIsDeferredWithoutTouchingQuota() {
        String badRequest = reserveDue();
        redisTemplate.opsForHash().put(
                DrawQuotaKeys.reservation(badRequest), "requestId", UUID.randomUUID().toString());
        String badActivity = reserveDue();
        redisTemplate.opsForHash().put(DrawQuotaKeys.reservation(badActivity), "activityId", "0");
        String badUser = reserveDue();
        redisTemplate.opsForHash().put(DrawQuotaKeys.reservation(badUser), "userId", "not-a-number");
        String badCount = reserveDue();
        redisTemplate.opsForHash().put(DrawQuotaKeys.reservation(badCount), "drawCount", "2");
        String badDate = reserveDue();
        redisTemplate.opsForHash().put(DrawQuotaKeys.reservation(badDate), "drawDate", "2026-99-99");

        ReconciliationResult result = service.reconcileExpiredReservations(Instant.now());

        assertThat(result.failed()).isEqualTo(5);
        assertThat(java.util.List.of(badRequest, badActivity, badUser, badCount, badDate))
                .allSatisfy(requestId -> {
                    assertThat(status(requestId)).isEqualTo("RESERVED");
                    assertThat(score(requestId)).isGreaterThan(Instant.now().toEpochMilli());
                });
        assertThat(quotaValue(badDate)).isEqualTo("5");
    }

    private String reserveDue() {
        String requestId = newRequestId();
        reserveExistingRequestDue(requestId);
        return requestId;
    }

    private String newRequestId() {
        String requestId = UUID.randomUUID().toString();
        requestIds.add(requestId);
        return requestId;
    }

    private void reserveExistingRequestDue(String requestId) {
        var reservation = quotaService.reserve(
                new QuotaReservationRequest(requestId, ACTIVITY_ID, USER_ID, 1, 100));
        drawDates.put(requestId, reservation.drawDate());
        quotaKeys.add(DrawQuotaKeys.quota(ACTIVITY_ID, USER_ID, reservation.drawDate()));
        redisTemplate.opsForZSet().add(DrawQuotaKeys.reservationTimeouts(), requestId,
                Instant.now().minusSeconds(1).toEpochMilli());
    }

    private LotteryDrawOrder createOrder(String requestId, LocalDateTime createdAt) {
        LotteryDrawOrder order = lifecycleService.createProcessing(new NewDrawOrder(
                requestId, USER_ID, ACTIVITY_ID, 1, drawDates.get(requestId)));
        jdbcTemplate.update("UPDATE lottery_draw_order SET created_at = ? WHERE id = ?", createdAt, order.getId());
        return orderMapper.selectById(order.getId());
    }

    private String status(String requestId) {
        Object value = redisTemplate.opsForHash().get(DrawQuotaKeys.reservation(requestId), "status");
        return value == null ? null : value.toString();
    }

    private Double score(String requestId) {
        return redisTemplate.opsForZSet().score(DrawQuotaKeys.reservationTimeouts(), requestId);
    }

    private String quotaValue(String requestId) {
        return redisTemplate.opsForValue().get(
                DrawQuotaKeys.quota(ACTIVITY_ID, USER_ID, drawDates.get(requestId)));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for concurrent transition");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}
