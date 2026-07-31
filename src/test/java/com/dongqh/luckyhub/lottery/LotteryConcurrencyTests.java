package com.dongqh.luckyhub.lottery;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.lottery.algorithm.DrawRandomSource;
import com.dongqh.luckyhub.lottery.dto.DrawCommand;
import com.dongqh.luckyhub.lottery.enums.LotteryErrorCode;
import com.dongqh.luckyhub.lottery.quota.DrawQuotaKeys;
import com.dongqh.luckyhub.lottery.service.LotteryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "luckyhub.lottery.reconcile-initial-delay=24h",
        "luckyhub.messaging.consumer-initial-delay=24h",
        "luckyhub.lottery.outbox-initial-delay=24h",
        "luckyhub.activity.status-refresh-initial-delay=86400000"
})
class LotteryConcurrencyTests {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final long BASE_USER = 916000L;

    @Autowired LotteryService lotteryService;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @MockitoBean DrawRandomSource randomSource;

    private final List<String> requestIds = new ArrayList<>();
    private final List<Long> activityIds = new ArrayList<>();
    private final List<Long> prizeIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Mockito.reset(randomSource);
        when(randomSource.nextLong(anyLong())).thenReturn(0L);
    }

    @AfterEach
    void cleanExactRowsAndKeys() {
        LoginContext.clear();
        for (String requestId : requestIds) {
            deleteQuotaForReservation(requestId);
            List<String> eventIds = jdbc.queryForList(
                    "SELECT event_id FROM message_outbox WHERE payload ->> '$.requestId'=?", String.class, requestId);
            eventIds.forEach(eventId -> jdbc.update("DELETE FROM message_consume_record WHERE event_id=?", eventId));
            jdbc.update("DELETE FROM message_outbox WHERE payload ->> '$.requestId'=?", requestId);
            jdbc.update("DELETE b FROM user_benefit b JOIN lottery_draw_record r ON r.id=b.draw_record_id WHERE r.request_id=?", requestId);
            jdbc.update("DELETE FROM lottery_draw_record WHERE request_id=?", requestId);
            jdbc.update("DELETE FROM lottery_draw_order WHERE request_id=?", requestId);
            redis.delete(DrawQuotaKeys.reservation(requestId));
            redis.opsForZSet().remove(DrawQuotaKeys.reservationTimeouts(), requestId);
        }
        for (long activityId : activityIds) {
            jdbc.update("DELETE FROM marketing_activity_prize WHERE activity_id=?", activityId);
            jdbc.update("DELETE FROM marketing_activity WHERE id=?", activityId);
        }
        for (long prizeId : prizeIds) jdbc.update("DELETE FROM marketing_prize WHERE id=?", prizeId);
    }

    @Test
    void concurrentUniqueRequestsNeverExceedDailyLimitAndEverySuccessHasOneRecord() throws Exception {
        long activityId = activity(100, 17);
        long userId = BASE_USER + 1;
        AtomicInteger success = new AtomicInteger();
        AtomicInteger quotaRejected = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(40);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(40);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 40; i++) {
                String requestId = requestId();
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    await(start);
                    LoginContext.set(new LoginPrincipal(userId, "quota-user", "quota-session"));
                    try {
                        lotteryService.draw(new DrawCommand(requestId, activityId, 1));
                        success.incrementAndGet();
                    } catch (BusinessException error) {
                        if (error.getErrorCode() == LotteryErrorCode.DAILY_QUOTA_EXCEEDED) {
                            quotaRejected.incrementAndGet();
                        } else {
                            throw error;
                        }
                    } finally {
                        LoginContext.clear();
                    }
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) future.get(30, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(success).hasValue(17);
        assertThat(quotaRejected).hasValue(23);
        assertThat(redis.opsForValue().get(DrawQuotaKeys.quota(
                activityId, userId, LocalDate.now(SHANGHAI)))).isEqualTo("17");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lottery_draw_order WHERE activity_id=? AND user_id=? AND status='SUCCESS'",
                Integer.class, activityId, userId)).isEqualTo(17);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM lottery_draw_order o
                WHERE o.activity_id=? AND o.user_id=? AND o.status='SUCCESS'
                  AND (SELECT COUNT(*) FROM lottery_draw_record r WHERE r.order_id=o.id) <> o.draw_count
                """, Integer.class, activityId, userId)).isZero();
    }

    @Test
    void concurrentSameRequestCreatesOneOrderConsumesOnceAndEventuallyReturnsExactTen() throws Exception {
        long activityId = activity(100, 100);
        long userId = BASE_USER + 2;
        String requestId = requestId();
        CountDownLatch ready = new CountDownLatch(16);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(16);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 16; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    await(start);
                    LoginContext.set(new LoginPrincipal(userId, "same-user", "same-session"));
                    try {
                        lotteryService.draw(new DrawCommand(requestId, activityId, 10));
                    } catch (BusinessException error) {
                        assertThat(error.getErrorCode()).isEqualTo(LotteryErrorCode.DRAW_ORDER_PROCESSING);
                    } finally {
                        LoginContext.clear();
                    }
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) future.get(30, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        LoginContext.set(new LoginPrincipal(userId, "same-user", "same-session"));
        var retry = lotteryService.draw(new DrawCommand(requestId, activityId, 10));
        assertThat(retry.results()).hasSize(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lottery_draw_order WHERE request_id=?", Integer.class, requestId)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lottery_draw_record WHERE request_id=?", Integer.class, requestId)).isEqualTo(10);
        assertThat(redis.opsForValue().get(DrawQuotaKeys.quota(
                activityId, userId, retry.drawDate()))).isEqualTo("10");
    }

    @Test
    void concurrentUsersNeverOversellStockAndLosingCandidatesAreCompleteNoWinOrders() throws Exception {
        Fixture fixture = prizeActivity(5, 100, 5, 0, 1);
        int calls = 20;
        CountDownLatch ready = new CountDownLatch(calls);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(calls);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < calls; i++) {
                long userId = BASE_USER + 100 + i;
                String requestId = requestId();
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    await(start);
                    LoginContext.set(new LoginPrincipal(userId, "stock-user", "stock-session"));
                    try {
                        lotteryService.draw(new DrawCommand(requestId, fixture.activityId(), 1));
                    } finally {
                        LoginContext.clear();
                    }
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) future.get(30, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(jdbc.queryForObject("SELECT remaining_stock FROM marketing_activity_prize WHERE id=?",
                Integer.class, fixture.activityPrizeId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lottery_draw_record WHERE activity_id=? AND result_type='WIN'",
                Integer.class, fixture.activityId())).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lottery_draw_record WHERE activity_id=? AND result_type='NO_WIN'",
                Integer.class, fixture.activityId())).isEqualTo(15);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM lottery_draw_order o
                WHERE o.activity_id=? AND o.status='SUCCESS'
                  AND (SELECT COUNT(*) FROM lottery_draw_record r WHERE r.order_id=o.id) <> o.draw_count
                """, Integer.class, fixture.activityId())).isZero();
    }

    private Fixture prizeActivity(int totalStock, int weight, int remainingStock,
                                  int noWinWeight, int dailyLimit) {
        long prizeId = insert("""
                INSERT INTO marketing_prize(prize_name, prize_type, prize_level, status)
                VALUES ('Task15 Concurrent Prize', 'COUPON', 'FIRST', 1)
                """);
        prizeIds.add(prizeId);
        long activityId = activity(noWinWeight, dailyLimit);
        long relationId = insert("""
                INSERT INTO marketing_activity_prize
                    (activity_id, prize_id, weight, total_stock, remaining_stock, sort_order)
                VALUES (?, ?, ?, ?, ?, 0)
                """, activityId, prizeId, weight, totalStock, remainingStock);
        return new Fixture(activityId, relationId);
    }

    private long activity(int noWinWeight, int dailyLimit) {
        long id = insert("""
                INSERT INTO marketing_activity
                    (activity_name, status, start_time, end_time, daily_limit, no_win_weight, created_by)
                VALUES (?, 'RUNNING', NOW(3) - INTERVAL 1 HOUR,
                        NOW(3) + INTERVAL 1 HOUR, ?, ?, 1)
                """, "task15-concurrent-" + UUID.randomUUID(), dailyLimit, noWinWeight);
        activityIds.add(id);
        return id;
    }

    private String requestId() {
        String value = UUID.randomUUID().toString();
        requestIds.add(value);
        return value;
    }

    private long insert(String sql, Object... args) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("concurrency barrier timed out");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private void deleteQuotaForReservation(String requestId) {
        List<Object> identity = redis.opsForHash().multiGet(
                DrawQuotaKeys.reservation(requestId), List.of("activityId", "userId", "drawDate"));
        if (identity.size() == 3 && identity.stream().noneMatch(java.util.Objects::isNull)) {
            long activityId = Long.parseLong(identity.get(0).toString());
            long userId = Long.parseLong(identity.get(1).toString());
            LocalDate drawDate = LocalDate.parse(identity.get(2).toString(), DateTimeFormatter.BASIC_ISO_DATE);
            redis.delete(DrawQuotaKeys.quota(activityId, userId, drawDate));
        }
    }

    private record Fixture(long activityId, long activityPrizeId) {}
}
