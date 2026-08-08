package com.dongqh.luckyhub.payment;

import com.dongqh.luckyhub.membership.dto.CreateMembershipProductCommand;
import com.dongqh.luckyhub.membership.dto.PurchaseMembershipCommand;
import com.dongqh.luckyhub.membership.enums.MembershipCardType;
import com.dongqh.luckyhub.membership.service.MembershipService;
import com.dongqh.luckyhub.order.dto.CreateCashOrderCommand;
import com.dongqh.luckyhub.payment.dto.CreatePaymentCommand;
import com.dongqh.luckyhub.payment.dto.PaymentCallbackCommand;
import com.dongqh.luckyhub.payment.enums.PaymentResult;
import com.dongqh.luckyhub.payment.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class Phase3EndToEndTests extends PaymentTestFixture {
    @Autowired MembershipService memberships;
    @Autowired PaymentService payments;

    @AfterEach void cleanMembership() {
        jdbc.update("DELETE FROM membership_grant_record");
        jdbc.update("DELETE FROM user_membership");
        jdbc.update("DELETE FROM membership_product");
    }

    @Test void memberCouponOrderPaymentLifecycleIsAtomic() {
        var product = memberships.createProduct(new CreateMembershipProductCommand(
                "YEAR-" + suffix, "年卡", "VIP", MembershipCardType.YEAR, 365,
                9900L, 9000, 2, 12000));
        memberships.purchase(new PurchaseMembershipCommand("MEM-" + suffix, product.id(), userId));

        var order = orders.create(userId, new CreateCashOrderCommand("E2E-O", skuId, 1, couponId));
        assertThat(order.originalAmountCent()).isEqualTo(10000L);
        assertThat(order.membershipDiscountCent()).isEqualTo(1000L);
        assertThat(order.couponDiscountCent()).isEqualTo(1000L);
        assertThat(order.payableAmountCent()).isEqualTo(8000L);

        payments.create(userId, new CreatePaymentCommand("E2E-P", "E2E-O"));
        String signature = payments.signForSimulation("E2E-P", PaymentResult.SUCCESS, 8000L);
        payments.callback(new PaymentCallbackCommand("E2E-P", PaymentResult.SUCCESS, null, signature));

        assertThat(orders.get(userId, "E2E-O").status().name()).isEqualTo("PAID");
        assertThat(coupons.getMine(userId, couponId).status().name()).isEqualTo("USED");
        assertThat(inventory.get(skuId, "MALL").consumedStock()).isOne();
    }
}
