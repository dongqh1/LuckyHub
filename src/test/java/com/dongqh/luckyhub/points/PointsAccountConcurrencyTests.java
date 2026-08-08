package com.dongqh.luckyhub.points;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.points.dto.AdminPointsAdjustmentCommand;
import com.dongqh.luckyhub.points.dto.PointsMutationCommand;
import com.dongqh.luckyhub.points.enums.PointsBusinessType;
import com.dongqh.luckyhub.points.enums.PointsErrorCode;
import com.dongqh.luckyhub.points.service.PointsAccountService;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import com.dongqh.luckyhub.rbac.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@SpringBootTest
class PointsAccountConcurrencyTests {

    @Autowired
    private PointsAccountService service;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;
    private Long userId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM points_ledger");
        jdbcTemplate.update("DELETE FROM points_account");
        executor = Executors.newFixedThreadPool(40);
        userId = createUser();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        jdbcTemplate.update("DELETE FROM points_ledger");
        jdbcTemplate.update("DELETE FROM points_account");
        if (userId != null) {
            userMapper.deleteById(userId);
        }
    }

    @Test
    void fortyUniqueDebitsCanSpendOnlySeventeenAvailablePoints() {
        assertTimeoutPreemptively(Duration.ofSeconds(45), () -> {
            service.adjust(new AdminPointsAdjustmentCommand(
                    userId, 17L, "CONCURRENCY-SEED-17", "并发扣减初始积分"));
            int callers = 40;
            CountDownLatch ready = new CountDownLatch(callers);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger insufficient = new AtomicInteger();
            ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
            List<Future<Boolean>> futures = new ArrayList<>();

            for (int index = 0; index < callers; index++) {
                String businessId = "CONCURRENT-DEBIT-" + index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    try {
                        service.debit(new PointsMutationCommand(
                                userId, PointsBusinessType.REDEMPTION,
                                businessId, 1L, "并发兑换"));
                        return true;
                    } catch (BusinessException exception) {
                        if (exception.getErrorCode() == PointsErrorCode.POINTS_INSUFFICIENT) {
                            insufficient.incrementAndGet();
                        } else {
                            unexpected.add(exception);
                        }
                        return false;
                    } catch (Throwable throwable) {
                        unexpected.add(throwable);
                        return false;
                    }
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int successes = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(30, TimeUnit.SECONDS)) {
                    successes++;
                }
            }

            assertThat(unexpected).isEmpty();
            assertThat(successes).isEqualTo(17);
            assertThat(insufficient).hasValue(23);
            assertThat(service.get(userId).balance()).isZero();
            assertThat(countRedemptionLedgers()).isEqualTo(17);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM points_account WHERE balance < 0", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM points_ledger WHERE balance_after < 0", Integer.class)).isZero();
        });
    }

    @Test
    void twentyDuplicateDebitsConvergeToOneLedgerAndOneBalanceChange() {
        assertTimeoutPreemptively(Duration.ofSeconds(45), () -> {
            service.adjust(new AdminPointsAdjustmentCommand(
                    userId, 100L, "CONCURRENCY-SEED-100", "重复请求初始积分"));
            int callers = 20;
            CountDownLatch ready = new CountDownLatch(callers);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Long>> futures = new ArrayList<>();

            for (int index = 0; index < callers; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    return service.debit(new PointsMutationCommand(
                            userId, PointsBusinessType.REDEMPTION,
                            "CONCURRENT-SAME-DEBIT", 10L, "同一兑换请求")).id();
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Set<Long> ledgerIds = new HashSet<>();
            for (Future<Long> future : futures) {
                ledgerIds.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(ledgerIds).hasSize(1);
            assertThat(service.get(userId).balance()).isEqualTo(90L);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM points_ledger
                    WHERE business_type = 'REDEMPTION'
                      AND business_id = 'CONCURRENT-SAME-DEBIT'
                    """, Integer.class)).isOne();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM points_account WHERE user_id = ?", Integer.class, userId)).isOne();
        });
    }

    private long createUser() {
        SysUser user = new SysUser();
        user.setUsername("pc-" + UUID.randomUUID());
        user.setPassword("test-password");
        user.setNickname("积分并发测试用户");
        user.setStatus(1);
        userMapper.insert(user);
        return user.getId();
    }

    private int countRedemptionLedgers() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM points_ledger
                WHERE business_type = 'REDEMPTION'
                """, Integer.class);
        return count == null ? 0 : count;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("积分并发测试启动栅栏超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
