package com.dongqh.luckyhub.shipping.service;

import com.dongqh.luckyhub.shipping.entity.ShippingAddressSnapshot;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;

public interface ShippingAddressSnapshotService {
    ShippingAddressSnapshot create(long userId, long addressId,
                                   ShippingSourceType sourceType, String sourceId);

    ShippingAddressSnapshot require(long snapshotId);
}
