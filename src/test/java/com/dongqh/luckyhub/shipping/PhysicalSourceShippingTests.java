package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.order.dto.CreateCashOrderCommand;
import com.dongqh.luckyhub.order.service.CashOrderService;
import com.dongqh.luckyhub.payment.dto.CreatePaymentCommand;
import com.dongqh.luckyhub.payment.dto.PaymentCallbackCommand;
import com.dongqh.luckyhub.payment.enums.PaymentResult;
import com.dongqh.luckyhub.payment.service.PaymentService;
import com.dongqh.luckyhub.points.dto.AdminPointsAdjustmentCommand;
import com.dongqh.luckyhub.points.dto.CreatePointsRedemptionCommand;
import com.dongqh.luckyhub.points.dto.ReversePointsRedemptionCommand;
import com.dongqh.luckyhub.points.enums.PointsErrorCode;
import com.dongqh.luckyhub.points.service.PointsAccountService;
import com.dongqh.luckyhub.points.service.PointsRedemptionService;
import org.junit.jupiter.api.Test;
import com.dongqh.luckyhub.shipping.enums.ShippingStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PhysicalSourceShippingTests extends Task5ShippingTestFixture {
    @Autowired CashOrderService cashOrders;
    @Autowired PaymentService payments;
    @Autowired PointsRedemptionService redemptions;
    @Autowired PointsAccountService points;

    @Test
    void paidPhysicalCashOrderCreatesShippingExactlyOnceAcrossCallbackRetry() {
        long userId = createUser();
        long addressId = createAddress(userId);
        long skuId = createPhysicalSku(true, false);
        String orderNo = unique("CASH-SHIP");
        String paymentNo = unique("PAY-SHIP");
        cashOrders.create(userId, new CreateCashOrderCommand(orderNo, skuId, 1, null, addressId));
        var payment = payments.create(userId, new CreatePaymentCommand(paymentNo, orderNo));
        String signature = payments.signForSimulation(paymentNo, PaymentResult.SUCCESS, payment.amountCent());
        PaymentCallbackCommand callback = new PaymentCallbackCommand(
                paymentNo, PaymentResult.SUCCESS, null, signature);

        payments.callback(callback);
        payments.callback(callback);

        var order = cashOrders.get(userId, orderNo);
        assertThat(order.shippingOrderId()).isNotNull();
        assertThat(order.shippingNo()).startsWith("SHIPPING-");
        assertThat(order.shippingStatus()).isEqualTo(ShippingStatus.FULFILLING);
        String fulfillmentNo = "LOGISTICS-" + order.shippingOrderId();
        trackFulfillment(fulfillmentNo);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shipping_order WHERE source_type='CASH_ORDER' AND source_id=?",
                Integer.class, String.valueOf(order.id()))).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM fulfillment_task WHERE fulfillment_no=?",
                Integer.class, fulfillmentNo)).isOne();
    }

    @Test
    void completedPhysicalPointsRedemptionShipsOnceAndCannotReverse() {
        long userId = createUser();
        long addressId = createAddress(userId);
        long skuId = createPhysicalSku(false, true);
        String redemptionNo = unique("POINTS-SHIP");
        points.adjust(new AdminPointsAdjustmentCommand(
                userId, 1_000L, unique("POINTS-SEED"), "任务5测试入账"));

        var first = redemptions.create(userId,
                new CreatePointsRedemptionCommand(redemptionNo, skuId, 1, addressId));
        var repeated = redemptions.create(userId,
                new CreatePointsRedemptionCommand(redemptionNo, skuId, 1, addressId));

        assertThat(repeated.shippingOrderId()).isEqualTo(first.shippingOrderId()).isNotNull();
        assertThat(repeated.shippingNo()).startsWith("SHIPPING-");
        assertThat(repeated.shippingStatus()).isEqualTo(ShippingStatus.FULFILLING);
        String fulfillmentNo = "LOGISTICS-" + first.shippingOrderId();
        trackFulfillment(fulfillmentNo);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM fulfillment_task WHERE fulfillment_no=?",
                Integer.class, fulfillmentNo)).isOne();
        assertThatThrownBy(() -> redemptions.reverse(redemptionNo,
                new ReversePointsRedemptionCommand(unique("REV"), "已有实物发货单")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(PointsErrorCode.REDEMPTION_STATE_CONFLICT));
    }
}
