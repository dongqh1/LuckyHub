package com.dongqh.luckyhub.shipping.service;

import com.dongqh.luckyhub.shipping.vo.ShippingTrackingView;

public interface ShippingQueryService {
    ShippingTrackingView getForUser(long userId, String shippingNo);
}
