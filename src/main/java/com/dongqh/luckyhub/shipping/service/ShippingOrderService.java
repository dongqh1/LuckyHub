package com.dongqh.luckyhub.shipping.service;

import com.dongqh.luckyhub.shipping.model.CreateShippingOrderCommand;
import com.dongqh.luckyhub.shipping.vo.ShippingOrderView;

public interface ShippingOrderService {
    ShippingOrderView create(CreateShippingOrderCommand command);
    ShippingOrderView getForUser(long userId, String shippingNo);
    void projectFulfillmentState(String fulfillmentNo);
}
