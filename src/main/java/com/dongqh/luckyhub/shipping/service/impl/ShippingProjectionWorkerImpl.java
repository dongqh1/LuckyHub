package com.dongqh.luckyhub.shipping.service.impl;

import com.dongqh.luckyhub.shipping.entity.ShippingOrder;
import com.dongqh.luckyhub.shipping.enums.ShippingStatus;
import com.dongqh.luckyhub.shipping.mapper.ShippingOrderMapper;
import com.dongqh.luckyhub.shipping.service.ShippingOrderService;
import com.dongqh.luckyhub.shipping.service.ShippingProjectionWorker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShippingProjectionWorkerImpl implements ShippingProjectionWorker {
    private final ShippingOrderMapper orders;
    private final ShippingOrderService shippingOrders;

    public ShippingProjectionWorkerImpl(ShippingOrderMapper orders, ShippingOrderService shippingOrders) {
        this.orders = orders;
        this.shippingOrders = shippingOrders;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean projectOne(long shippingOrderId) {
        ShippingOrder order = orders.lockById(shippingOrderId);
        if (order == null || order.getStatus() == ShippingStatus.DELIVERED
                || order.getStatus() == ShippingStatus.TERMINATED || order.getFulfillmentNo() == null) {
            return false;
        }
        shippingOrders.projectFulfillmentState(order.getFulfillmentNo());
        return true;
    }
}
