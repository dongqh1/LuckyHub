package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.shipping.crypto.LogisticsCallbackSigner;
import com.dongqh.luckyhub.shipping.dto.LogisticsCallbackCommand;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.enums.TrackingEventType;
import com.dongqh.luckyhub.shipping.model.CreateShippingOrderCommand;
import com.dongqh.luckyhub.shipping.service.LogisticsCallbackService;
import com.dongqh.luckyhub.shipping.service.ShippingOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LogisticsCallbackConcurrencyTests extends Task5ShippingTestFixture {
    @Autowired LogisticsCallbackSigner signer;
    @Autowired LogisticsCallbackService callbacks;
    @Autowired ShippingOrderService shippingOrders;

    @Test
    void twentyConcurrentDuplicatesCreateOneReceiptEventAndTransition() throws Exception {
        long userId = createUser();
        String sourceId = unique("TASK6-CONCURRENT");
        var snapshot = createSnapshot(userId, ShippingSourceType.CASH_ORDER, sourceId);
        var order = shippingOrders.create(new CreateShippingOrderCommand(
                ShippingSourceType.CASH_ORDER, sourceId, userId, snapshot.getId(),
                "SKU-CONCURRENT", "并发物流", null, 1, null));
        trackFulfillment(order.fulfillmentNo());
        String waybillNo = unique("SIM-L-CONCURRENT");
        jdbc.update("UPDATE shipping_order SET status='SHIPPED', waybill_no=?, version=0 WHERE id=?",
                waybillNo, order.id());
        LogisticsCallbackCommand unsigned = new LogisticsCallbackCommand(
                unique("CALLBACK"), unique("NONCE"), System.currentTimeMillis() / 1000,
                waybillNo, TrackingEventType.DELIVERED, LocalDateTime.now(), "杭州", "已签收", null);
        LogisticsCallbackCommand command = new LogisticsCallbackCommand(
                unsigned.callbackId(), unsigned.nonce(), unsigned.timestampEpochSecond(), unsigned.waybillNo(),
                unsigned.eventType(), unsigned.eventTime(), unsigned.locationSummary(), unsigned.description(),
                signer.sign(unsigned));

        int workers = 20;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    callbacks.handle(command);
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) future.get();
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shipping_callback_receipt WHERE callback_id=?",
                Integer.class, command.callbackId())).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shipping_tracking_event WHERE waybill_no=?",
                Integer.class, waybillNo)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM shipping_order WHERE id=?", String.class, order.id())).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject(
                "SELECT version FROM shipping_order WHERE id=?", Integer.class, order.id())).isOne();
    }
}
