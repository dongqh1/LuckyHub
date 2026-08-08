package com.dongqh.luckyhub.payment;

import com.dongqh.luckyhub.payment.dto.CreatePaymentCommand;
import com.dongqh.luckyhub.payment.service.PaymentService;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class Phase3SafetyTests extends PaymentTestFixture {
    @Autowired PaymentService payments;

    @Test void refusesPaymentCreationAfterOrderDeadline() {
        createOrder("EXPIRED-O");
        jdbc.update("UPDATE mall_order SET payment_deadline=DATE_SUB(NOW(3), INTERVAL 1 SECOND) WHERE order_no='EXPIRED-O'");
        assertThatThrownBy(() -> payments.create(userId, new CreatePaymentCommand("TOO-LATE", "EXPIRED-O")))
                .hasMessageContaining("不可支付");
    }

    @Test void anotherUserCannotReadOrderOrCoupon() {
        createOrder("PRIVATE-O");
        SysUser other = new SysUser();
        other.setUsername("other-" + UUID.randomUUID());
        other.setPassword("x");
        other.setNickname("其他用户");
        other.setStatus(1);
        users.insert(other);
        try {
            assertThatThrownBy(() -> orders.get(other.getId(), "PRIVATE-O")).hasMessageContaining("无权");
            // Return a generic not-found style business error, so another user cannot enumerate coupon ownership.
            assertThatThrownBy(() -> coupons.getMine(other.getId(), couponId))
                    .isInstanceOf(BusinessException.class);
        } finally {
            users.deleteById(other.getId());
        }
    }

    @Test void duplicateConcurrentOrderCreationReservesOnlyOnce() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        var gate = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            futures.add(pool.submit(() -> {
                gate.await();
                return orders.create(userId, new com.dongqh.luckyhub.order.dto.CreateCashOrderCommand(
                        "SAME-O", skuId, 1, couponId));
            }));
        }
        gate.countDown();
        for (var future : futures) future.get();
        pool.shutdown();
        org.assertj.core.api.Assertions.assertThat(inventory.get(skuId, "MALL").reservedStock()).isOne();
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM mall_order WHERE order_no='SAME-O'", Integer.class)).isOne();
    }
}
