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
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipping_address_snapshot WHERE source_type='CASH_ORDER' AND source_id=(SELECT CAST(id AS CHAR) FROM mall_order WHERE order_no=?)", Integer.class, flow.orderNo())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipping_order WHERE id=?", Integer.class, flow.shippingOrderId())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fulfillment_task WHERE fulfillment_no=?", Integer.class, flow.fulfillmentNo())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sim_logistics_record WHERE fulfillment_no=?", Integer.class, flow.fulfillmentNo())).isOne();
    }

    @Test
    void physicalPointsRedemptionConvergesWithoutMixingAssetAndShippingState() {
        PointsFlow flow = pointsFlow();

        shipAndDeliver(flow.shippingOrderId(), flow.fulfillmentNo());

        var source = redemptions.get(flow.userId(), flow.redemptionNo());
        assertThat(source.status().name()).isEqualTo("COMPLETED");
        assertThat(source.shippingStatus()).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(points.get(flow.userId()).balance()).isEqualTo(900);
        assertDeliveredAggregate(flow.shippingOrderId(), flow.fulfillmentNo(), flow.shippingNo());
    }

    @Test
    void lotteryPhysicalWinWaitsForClaimThenConvergesToDelivered() throws Exception {
        LotteryFlow flow = lotteryFlow();

        shipAndDeliver(flow.shippingOrderId(), flow.fulfillmentNo());

        assertThat(jdbc.queryForObject("SELECT status FROM user_benefit WHERE id=?", String.class,
                flow.benefitId())).isEqualTo("DELIVERED");
        assertDeliveredAggregate(flow.shippingOrderId(), flow.fulfillmentNo(), flow.shippingNo());
    }

    private void assertDeliveredAggregate(long shippingOrderId, String fulfillmentNo, String shippingNo) {
        var user = shippingQueries.getForUser(jdbc.queryForObject(
                "SELECT target_user_id FROM shipping_order WHERE id=?", Long.class, shippingOrderId), shippingNo);
        assertThat(user.status()).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(user.tracking()).hasSize(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipping_address_snapshot WHERE id=(SELECT address_snapshot_id FROM shipping_order WHERE id=?)", Integer.class, shippingOrderId)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipping_order WHERE id=?", Integer.class, shippingOrderId)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fulfillment_task WHERE fulfillment_no=?", Integer.class, fulfillmentNo)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sim_logistics_record WHERE fulfillment_no=?", Integer.class, fulfillmentNo)).isOne();
    }
}
