package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.shipping.enums.ShippingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "luckyhub.fulfillment.initial-delay=24h",
        "luckyhub.fulfillment.projection-initial-delay=24h"
})
class PhysicalShippingEndToEndTests extends ShippingTestFixture {

    @Test
    void paidPhysicalOrderConvergesThroughWorkerSignedCallbacksAndQueries() {
        CashFlow flow = paidCashFlow();

        shipAndDeliver(flow.shippingOrderId(), flow.fulfillmentNo());

        var source = cashOrders.get(flow.userId(), flow.orderNo());
        var user = shippingQueries.getForUser(flow.userId(), flow.shippingNo());
        var admin = shippingAdmin.get(flow.shippingNo());
        assertThat(source.status().name()).isEqualTo("PAID");
        assertThat(source.shippingStatus()).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(user.status()).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(admin.status()).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(user.tracking()).extracting(event -> event.eventType().name())
                .containsExactly("PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED");
        assertSourceAggregate("CASH_ORDER", Long.toString(flow.sourceId()), flow.fulfillmentNo());
    }

    @Test
    void physicalPointsRedemptionConvergesWithoutMixingAssetAndShippingState() {
        PointsFlow flow = pointsFlow();

        shipAndDeliver(flow.shippingOrderId(), flow.fulfillmentNo());

        var source = redemptions.get(flow.userId(), flow.redemptionNo());
        assertThat(source.status().name()).isEqualTo("COMPLETED");
        assertThat(source.shippingStatus()).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(points.get(flow.userId()).balance()).isEqualTo(900);
        assertDeliveredAggregate(flow.shippingOrderId(), flow.shippingNo());
        assertSourceAggregate("POINTS_REDEMPTION", Long.toString(flow.sourceId()), flow.fulfillmentNo());
    }

    @Test
    void lotteryPhysicalWinWaitsForClaimThenConvergesToDelivered() throws Exception {
        LotteryFlow flow = lotteryFlow();

        shipAndDeliver(flow.shippingOrderId(), flow.fulfillmentNo());

        assertThat(jdbc.queryForObject("SELECT status FROM user_benefit WHERE id=?", String.class,
                flow.benefitId())).isEqualTo("DELIVERED");
        assertDeliveredAggregate(flow.shippingOrderId(), flow.shippingNo());
        assertSourceAggregate("LOTTERY_BENEFIT", Long.toString(flow.benefitId()), flow.fulfillmentNo());
    }

    private void assertDeliveredAggregate(long shippingOrderId, String shippingNo) {
        var user = shippingQueries.getForUser(jdbc.queryForObject(
                "SELECT target_user_id FROM shipping_order WHERE id=?", Long.class, shippingOrderId), shippingNo);
        assertThat(user.status()).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(user.tracking()).hasSize(4);
    }

    private void assertSourceAggregate(String sourceType, String sourceId, String fulfillmentNo) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipping_address_snapshot WHERE source_type=? AND source_id=?",
                Integer.class, sourceType, sourceId)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipping_order WHERE source_type=? AND source_id=?",
                Integer.class, sourceType, sourceId)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM fulfillment_task t
                JOIN shipping_order o ON o.fulfillment_no=t.fulfillment_no
                WHERE o.source_type=? AND o.source_id=? AND t.fulfillment_no=?
                """, Integer.class, sourceType, sourceId, fulfillmentNo)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM sim_logistics_record s
                JOIN shipping_order o ON o.fulfillment_no=s.fulfillment_no
                WHERE o.source_type=? AND o.source_id=?
                """, Integer.class, sourceType, sourceId)).isOne();
    }
}
