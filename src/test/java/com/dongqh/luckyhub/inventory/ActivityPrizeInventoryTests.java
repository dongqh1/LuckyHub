package com.dongqh.luckyhub.inventory;

import com.dongqh.luckyhub.activity.entity.MarketingActivityPrize;
import com.dongqh.luckyhub.activity.mapper.MarketingActivityPrizeMapper;
import com.dongqh.luckyhub.inventory.service.ActivityPrizeInventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@SpringBootTest
class ActivityPrizeInventoryTests {

    private final ActivityPrizeInventoryService inventoryService;
    private final MarketingActivityPrizeMapper activityPrizeMapper;
    private final JdbcTemplate jdbcTemplate;
    private Long activityPrizeId;

    @Autowired
    ActivityPrizeInventoryTests(
            ActivityPrizeInventoryService inventoryService,
            MarketingActivityPrizeMapper activityPrizeMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.inventoryService = inventoryService;
        this.activityPrizeMapper = activityPrizeMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void cleanUpCommittedTestData() {
        if (activityPrizeId != null) {
            activityPrizeMapper.deleteById(activityPrizeId);
        }
    }

    @Test
    void oneHundredConcurrentAttemptsCanConsumeStockTenTimesOnly() {
        MarketingActivityPrize relation = new MarketingActivityPrize();
        long uniqueValue = System.nanoTime();
        relation.setActivityId(uniqueValue);
        relation.setPrizeId(uniqueValue);
        relation.setWeight(1);
        relation.setTotalStock(10);
        relation.setRemainingStock(10);
        relation.setSortOrder(0);
        activityPrizeMapper.insert(relation);
        activityPrizeId = relation.getId();

        List<Attempt> attempts = assertTimeoutPreemptively(
                Duration.ofSeconds(30),
                () -> runConcurrentAttempts(100)
        );

        assertThat(attempts).filteredOn(Attempt::decremented).hasSize(10);
        assertThat(attempts).extracting(Attempt::observedStock).allMatch(stock -> stock >= 0);
        assertThat(remainingStock()).isZero();
        assertThat(inventoryService.decrementIfAvailable(activityPrizeId)).isFalse();
        assertThat(remainingStock()).isZero();
    }

    private List<Attempt> runConcurrentAttempts(int count) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Attempt>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    boolean decremented = inventoryService.decrementIfAvailable(activityPrizeId);
                    return new Attempt(decremented, remainingStock());
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Attempt> attempts = new ArrayList<>();
            for (Future<Attempt> future : futures) {
                attempts.add(future.get(20, TimeUnit.SECONDS));
            }
            return attempts;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private int remainingStock() {
        return jdbcTemplate.queryForObject(
                "SELECT remaining_stock FROM marketing_activity_prize WHERE id = ?",
                Integer.class,
                activityPrizeId
        );
    }

    private record Attempt(boolean decremented, int observedStock) {
    }
}
