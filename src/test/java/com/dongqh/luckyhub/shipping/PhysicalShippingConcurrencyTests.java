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
        CashFlow flow = paidCashFlow();
        concurrently(20, () -> payments.callback(flow.paymentCallback()));
        assertOne(flow.shippingOrderId(), flow.fulfillmentNo());
    }

    @Test
    void twentyDuplicateRedemptionsStillConsumeOnceAndCreateOneAggregate() throws Exception {
        PointsFlow flow = pointsFlow();
        long skuId = jdbc.queryForObject("SELECT sku_id FROM points_redemption_order WHERE redemption_no=?",
                Long.class, flow.redemptionNo());
        long addressId = jdbc.queryForObject("SELECT address_id FROM shipping_address_snapshot WHERE id=(SELECT address_snapshot_id FROM shipping_order WHERE id=?)",
                Long.class, flow.shippingOrderId());
        concurrently(20, () -> redemptions.create(flow.userId(),
                new CreatePointsRedemptionCommand(flow.redemptionNo(), skuId, 1, addressId)));
        assertOne(flow.shippingOrderId(), flow.fulfillmentNo());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM points_ledger WHERE user_id=? AND business_id=?",
                Integer.class, flow.userId(), flow.redemptionNo())).isOne();
    }

    @Test
    void twentyDuplicateClaimsAndWorkersKeepStableIdentity() throws Exception {
        LotteryFlow flow = lotteryFlow();
        long addressId = jdbc.queryForObject("SELECT address_id FROM shipping_address_snapshot WHERE id=(SELECT address_snapshot_id FROM shipping_order WHERE id=?)",
                Long.class, flow.shippingOrderId());
        concurrently(20, () -> claims.claim(flow.userId(), flow.benefitId(),
                new ClaimPhysicalBenefitCommand(flow.claimRequestId(), addressId)));
        concurrently(20, worker::runBatch);
        shippingProjector.projectOne(flow.shippingOrderId());
        assertOne(flow.shippingOrderId(), flow.fulfillmentNo());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sim_logistics_record WHERE fulfillment_no=?",
                Integer.class, flow.fulfillmentNo())).isOne();
    }

    private void assertOne(long shippingOrderId, String fulfillmentNo) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipping_order WHERE id=?",
                Integer.class, shippingOrderId)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fulfillment_task WHERE fulfillment_no=?",
                Integer.class, fulfillmentNo)).isOne();
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
