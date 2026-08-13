package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.shipping.dto.ClaimPhysicalBenefitCommand;
import com.dongqh.luckyhub.shipping.service.PhysicalClaimService;
import com.dongqh.luckyhub.shipping.service.PhysicalClaimExpiryService;
import com.dongqh.luckyhub.shipping.vo.ShippingOrderView;
import com.dongqh.luckyhub.benefit.mapper.UserBenefitMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

@SpringBootTest
class PhysicalClaimConcurrencyTests extends Task5ShippingTestFixture {
    @Autowired PhysicalClaimService claims;
    @Autowired PhysicalClaimExpiryService expiry;
    @MockitoSpyBean UserBenefitMapper benefitMapperSpy;
    private Long benefitId;
    private Long drawRecordId;
    private Long drawOrderId;
    private Long activityPrizeId;
    private Long activityId;
    private Long prizeId;
    private Long rewardId;

    @AfterEach
    void cleanLotteryFixture() {
        if (benefitId != null) jdbc.update("DELETE FROM inventory_ledger WHERE business_no=?", "CLAIM-EXPIRE-" + benefitId);
        if (benefitId != null) jdbc.update("DELETE FROM user_benefit WHERE id=?", benefitId);
        if (drawRecordId != null) jdbc.update("DELETE FROM lottery_draw_record WHERE id=?", drawRecordId);
        if (drawOrderId != null) jdbc.update("DELETE FROM lottery_draw_order WHERE id=?", drawOrderId);
        if (activityPrizeId != null) jdbc.update("DELETE FROM marketing_activity_prize WHERE id=?", activityPrizeId);
        if (activityId != null) jdbc.update("DELETE FROM marketing_activity WHERE id=?", activityId);
        if (prizeId != null) jdbc.update("DELETE FROM marketing_prize WHERE id=?", prizeId);
        if (rewardId != null) jdbc.update("DELETE FROM reward_definition WHERE id=?", rewardId);
    }

    @Test
    void twentySameIdentityClaimsCreateExactlyOneSnapshotOrderAndLogisticsTask() throws Exception {
        long userId = createUser();
        long addressId = createAddress(userId);
        long skuId = createPhysicalSku(true, false);
        createPendingBenefit(userId, skuId, LocalDateTime.now().plusDays(1));
        String requestId = UUID.randomUUID().toString();
        var pool = Executors.newFixedThreadPool(20);
        try {
            List<Callable<ShippingOrderView>> calls = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                calls.add(() -> claims.claim(userId, benefitId,
                        new ClaimPhysicalBenefitCommand(requestId, addressId)));
            }
            List<ShippingOrderView> results = pool.invokeAll(calls).stream()
                    .map(future -> {
                        try { return future.get(); }
                        catch (Exception exception) { throw new AssertionError(exception); }
                    }).toList();
            ShippingOrderView winner = results.get(0);
            trackFulfillment(winner.fulfillmentNo());
            assertThat(results).allSatisfy(result -> assertThat(result.id()).isEqualTo(winner.id()));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipping_address_snapshot WHERE source_type='LOTTERY_BENEFIT' AND source_id=?", Integer.class, benefitId.toString())).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipping_order WHERE source_type='LOTTERY_BENEFIT' AND source_id=?", Integer.class, benefitId.toString())).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fulfillment_task WHERE fulfillment_no=?", Integer.class, winner.fulfillmentNo())).isOne();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void claimAndExpiryAtTheSameInstantHaveExactlyOneLockedWinner() throws Exception {
        long userId = createUser();
        long addressId = createAddress(userId);
        long skuId = createPhysicalSku(true, false);
        LocalDateTime deadline = LocalDateTime.now().plusSeconds(1);
        createPendingBenefit(userId, skuId, deadline);
        int stockBefore = jdbc.queryForObject("SELECT remaining_stock FROM marketing_activity_prize WHERE id=?",
                Integer.class, activityPrizeId);
        String requestId = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var claimFuture = pool.submit(() -> {
                start.await();
                try { return claims.claim(userId, benefitId, new ClaimPhysicalBenefitCommand(requestId, addressId)); }
                catch (RuntimeException rejected) { return null; }
            });
            var expiryFuture = pool.submit(() -> {
                start.await();
                return expiry.expireDue(10, deadline);
            });
            start.countDown();
            ShippingOrderView claimed = claimFuture.get();
            int expired = expiryFuture.get();
            String status = jdbc.queryForObject("SELECT status FROM user_benefit WHERE id=?", String.class, benefitId);
            int stockAfter = jdbc.queryForObject("SELECT remaining_stock FROM marketing_activity_prize WHERE id=?",
                    Integer.class, activityPrizeId);
            if (claimed != null) {
                trackFulfillment(claimed.fulfillmentNo());
                assertThat(expired).isZero();
                assertThat(status).isEqualTo("FULFILLING");
                assertThat(stockAfter).isEqualTo(stockBefore);
            } else {
                assertThat(expired).isOne();
                assertThat(status).isEqualTo("CLAIM_EXPIRED");
                assertThat(stockAfter).isEqualTo(stockBefore + 1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void claimFailureRollsBackSnapshotOrderTaskAndBenefitLink() {
        long userId = createUser();
        long addressId = createAddress(userId);
        long skuId = createPhysicalSku(true, false);
        createPendingBenefit(userId, skuId, LocalDateTime.now().plusDays(1));
        doReturn(0).when(benefitMapperSpy).markClaimed(eq(benefitId), any(Long.class), any(LocalDateTime.class));
        try {
            assertThatThrownBy(() -> claims.claim(userId, benefitId,
                    new ClaimPhysicalBenefitCommand(UUID.randomUUID().toString(), addressId)))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            reset(benefitMapperSpy);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM user_benefit WHERE id=?", String.class, benefitId))
                .isEqualTo("CLAIM_PENDING");
        assertThat(jdbc.queryForObject("SELECT shipping_order_id FROM user_benefit WHERE id=?",
                Long.class, benefitId)).isNull();
        assertThat(jdbc.queryForObject("SELECT claimed_at FROM user_benefit WHERE id=?",
                LocalDateTime.class, benefitId)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipping_address_snapshot WHERE source_type='LOTTERY_BENEFIT' AND source_id=?",
                Integer.class, benefitId.toString())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipping_order WHERE source_type='LOTTERY_BENEFIT' AND source_id=?",
                Integer.class, benefitId.toString())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fulfillment_task WHERE source_type='LOTTERY_BENEFIT' AND source_id=?",
                Integer.class, benefitId.toString())).isZero();
    }

    @Test
    void poisonExpiryRollsBackLedgerStockAndBenefitState() {
        long userId = createUser();
        long skuId = createPhysicalSku(true, false);
        LocalDateTime now = LocalDateTime.now();
        createPendingBenefit(userId, skuId, now.minusSeconds(1));
        int stockBefore = jdbc.queryForObject("SELECT remaining_stock FROM marketing_activity_prize WHERE id=?",
                Integer.class, activityPrizeId);
        doReturn(0).when(benefitMapperSpy).markClaimExpired(eq(benefitId), eq(now));
        try {
            assertThat(expiry.expireDue(1, now)).isZero();
        } finally {
            reset(benefitMapperSpy);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM user_benefit WHERE id=?", String.class, benefitId))
                .isEqualTo("CLAIM_PENDING");
        assertThat(jdbc.queryForObject("SELECT remaining_stock FROM marketing_activity_prize WHERE id=?",
                Integer.class, activityPrizeId)).isEqualTo(stockBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE business_no=?",
                Integer.class, "CLAIM-EXPIRE-" + benefitId)).isZero();
    }

    private void createPendingBenefit(long userId, long skuId, LocalDateTime deadline) {
        String suffix = UUID.randomUUID().toString();
        String payload = "{\"skuId\":" + skuId + ",\"skuCode\":\"SKU-" + suffix
                + "\",\"productName\":\"并发礼盒\",\"skuName\":\"默认\",\"quantity\":1}";
        String fingerprint = "a".repeat(64);
        rewardId = insert("reward_definition", map("reward_code", "TASK4-R-" + suffix,
                "reward_name", "领取奖励", "reward_type", "PRODUCT", "target_id", skuId,
                "quantity", 1, "config_snapshot", payload, "status", 1));
        prizeId = insert("marketing_prize", map("prize_name", "并发实物", "prize_type", "PHYSICAL",
                "reward_definition_id", rewardId, "prize_level", "FIRST", "image_url", "https://example.test/gift.png",
                "stackable", 0, "status", 1));
        activityId = insert("marketing_activity", map("activity_name", "TASK4-A-" + suffix,
                "status", "RUNNING", "start_time", LocalDateTime.now().minusDays(1),
                "end_time", LocalDateTime.now().plusDays(1), "daily_limit", 1, "no_win_weight", 0,
                "created_by", userId));
        activityPrizeId = insert("marketing_activity_prize", map("activity_id", activityId,
                "prize_id", prizeId, "weight", 1, "total_stock", 10, "remaining_stock", 9, "sort_order", 0));
        drawOrderId = insert("lottery_draw_order", map("request_id", "TASK4-O-" + suffix,
                "user_id", userId, "activity_id", activityId, "draw_count", 1,
                "draw_date", LocalDate.now(), "status", "SUCCESS", "completed_at", LocalDateTime.now()));
        drawRecordId = insert("lottery_draw_record", map("order_id", drawOrderId,
                "request_id", "TASK4-O-" + suffix, "sequence_no", 1, "user_id", userId,
                "activity_id", activityId, "result_type", "WIN", "prize_id", prizeId,
                "prize_name", "并发实物", "prize_type", "PHYSICAL", "prize_image_url", "https://example.test/gift.png",
                "reward_definition_id", rewardId, "reward_type", "PRODUCT", "reward_target_id", skuId,
                "reward_quantity", 1, "reward_payload", payload, "reward_fingerprint", fingerprint,
                "draw_time", LocalDateTime.now()));
        benefitId = insert("user_benefit", map("draw_record_id", drawRecordId, "user_id", userId,
                "prize_id", prizeId, "prize_type", "PHYSICAL", "reward_definition_id", rewardId,
                "reward_type", "PRODUCT", "reward_target_id", skuId, "reward_quantity", 1,
                "reward_payload", payload, "reward_fingerprint", fingerprint, "quantity", 1,
                "status", "CLAIM_PENDING", "obtained_at", LocalDateTime.now(), "claim_deadline", deadline));
    }

    private long insert(String table, Map<String, Object> values) {
        return new SimpleJdbcInsert(jdbc).withTableName(table)
                .usingColumns(values.keySet().toArray(String[]::new)).usingGeneratedKeyColumns("id")
                .executeAndReturnKey(values).longValue();
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }
}
