package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.points.dto.CreatePointsRedemptionCommand;
import com.dongqh.luckyhub.shipping.dto.ClaimPhysicalBenefitCommand;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "luckyhub.fulfillment.initial-delay=24h",
        "luckyhub.fulfillment.projection-initial-delay=24h"
})
class PhysicalShippingConcurrencyTests extends ShippingTestFixture {

    @Test
    void twentyDuplicatePaymentCallbacksStillCreateOneAggregateAndTask() throws Exception {
        PreparedCashFlow prepared = prepareCashFlow();
        concurrently(20, () -> payments.callback(prepared.paymentCallback()));
        CashFlow flow = completedCashFlow(prepared);
        assertOne("CASH_ORDER", Long.toString(flow.sourceId()), flow.fulfillmentNo(), false);
    }

    @Test
    void twentyDuplicateRedemptionsStillConsumeOnceAndCreateOneAggregate() throws Exception {
        PreparedPointsFlow prepared = preparePointsFlow();
        concurrently(20, () -> redemptions.create(prepared.userId(),
                new CreatePointsRedemptionCommand(prepared.redemptionNo(), prepared.skuId(), 1, prepared.addressId())));
        PointsFlow flow = completedPointsFlow(prepared);
        assertOne("POINTS_REDEMPTION", Long.toString(flow.sourceId()), flow.fulfillmentNo(), false);
        assertThat(points.get(flow.userId()).balance()).isEqualTo(900);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM points_ledger WHERE user_id=? AND business_id=?",
                Integer.class, flow.userId(), flow.redemptionNo())).isOne();
    }

    @Test
    void twentyDuplicateClaimsAndWorkersKeepStableIdentity() throws Exception {
        PreparedLotteryFlow prepared = prepareLotteryFlow();
        concurrently(20, () -> claims.claim(prepared.userId(), prepared.benefitId(),
                new ClaimPhysicalBenefitCommand(prepared.claimRequestId(), prepared.addressId())));
        LotteryFlow flow = completedLotteryFlow(prepared);
        concurrently(20, worker::runBatch);
        shippingProjector.projectOne(flow.shippingOrderId());
        assertOne("LOTTERY_BENEFIT", Long.toString(flow.benefitId()), flow.fulfillmentNo(), true);
    }

    private void assertOne(String sourceType, String sourceId, String fulfillmentNo, boolean simulatorExpected) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipping_address_snapshot WHERE source_type=? AND source_id=?",
                Integer.class, sourceType, sourceId)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipping_order WHERE source_type=? AND source_id=?",
                Integer.class, sourceType, sourceId)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM fulfillment_task t JOIN shipping_order o ON o.fulfillment_no=t.fulfillment_no
                WHERE o.source_type=? AND o.source_id=? AND t.fulfillment_no=?
                """, Integer.class, sourceType, sourceId, fulfillmentNo)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM sim_logistics_record s JOIN shipping_order o ON o.fulfillment_no=s.fulfillment_no
                WHERE o.source_type=? AND o.source_id=?
                """, Integer.class, sourceType, sourceId)).isEqualTo(simulatorExpected ? 1 : 0);
    }

    private void concurrently(int count, ThrowingAction action) throws Exception {
        var start = new CountDownLatch(1);
        var errors = new ArrayList<Throwable>();
        var executor = Executors.newFixedThreadPool(count);
        try {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) futures.add(executor.submit(() -> {
                start.await();
                try { action.run(); } catch (Throwable error) { synchronized (errors) { errors.add(error); } }
                return null;
            }));
            start.countDown();
            for (var future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
        assertThat(errors).isEmpty();
    }

    @FunctionalInterface interface ThrowingAction { void run() throws Exception; }
}
