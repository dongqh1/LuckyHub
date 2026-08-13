package com.dongqh.luckyhub.shipping.service;

import com.dongqh.luckyhub.shipping.dto.CreateShippingAddressCommand;
import com.dongqh.luckyhub.shipping.dto.UpdateShippingAddressCommand;
import com.dongqh.luckyhub.shipping.entity.UserShippingAddress;
import com.dongqh.luckyhub.shipping.vo.ShippingAddressView;

import java.util.List;

public interface ShippingAddressService {
    ShippingAddressView create(long userId, CreateShippingAddressCommand command);
    List<ShippingAddressView> list(long userId);
    ShippingAddressView update(long userId, long addressId, UpdateShippingAddressCommand command);
    void delete(long userId, long addressId);
    ShippingAddressView makeDefault(long userId, long addressId);
    UserShippingAddress requireOwnedActive(long userId, long addressId);
}
