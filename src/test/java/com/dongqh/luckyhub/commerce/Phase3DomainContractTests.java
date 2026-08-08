package com.dongqh.luckyhub.commerce;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Phase3DomainContractTests {

    @Test
    void exposesStableDomainTypes() throws Exception {
        assertEnum("com.dongqh.luckyhub.coupon.enums.CouponType", "NO_THRESHOLD", "THRESHOLD");
        assertEnum("com.dongqh.luckyhub.coupon.enums.UserCouponStatus", "AVAILABLE", "LOCKED", "USED", "EXPIRED");
        assertEnum("com.dongqh.luckyhub.membership.enums.MembershipCardType", "MONTH", "QUARTER", "YEAR");
        assertEnum("com.dongqh.luckyhub.order.enums.CashOrderStatus", "PENDING_PAYMENT", "PAID", "CANCELLED");
        assertEnum("com.dongqh.luckyhub.payment.enums.PaymentStatus", "PENDING", "SUCCESS", "FAILED");
        assertEnum("com.dongqh.luckyhub.payment.enums.PaymentResult", "PROCESSING", "SUCCESS", "FAILURE");
        assertThat(Class.forName("com.dongqh.luckyhub.coupon.enums.CouponErrorCode").getEnumConstants()).hasSize(8);
        assertThat(Class.forName("com.dongqh.luckyhub.membership.enums.MembershipErrorCode").getEnumConstants()).hasSize(6);
        assertThat(Class.forName("com.dongqh.luckyhub.order.enums.OrderErrorCode").getEnumConstants()).hasSize(9);
        assertThat(Class.forName("com.dongqh.luckyhub.payment.enums.PaymentErrorCode").getEnumConstants()).hasSize(6);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertEnum(String className, String... values) throws Exception {
        Class<? extends Enum> type = (Class<? extends Enum>) Class.forName(className);
        assertThat(type.getEnumConstants()).extracting(Enum::name).containsExactly(values);
    }
}
