package com.dongqh.luckyhub.order.scheduler;

import com.dongqh.luckyhub.order.service.CashOrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderPaymentTimeoutScheduler {

    private final CashOrderService orders;

    public OrderPaymentTimeoutScheduler(CashOrderService orders) {
        this.orders = orders;
    }

    @Scheduled(fixedDelayString = "${luckyhub.order.payment-timeout-interval:60000}",
            initialDelayString = "${luckyhub.order.payment-timeout-initial-delay:60000}")
    public void cancelExpiredOrders() {
        orders.cancelExpired(100);
    }
}
