package com.dongqh.luckyhub.shipping.vo;

import com.dongqh.luckyhub.shipping.enums.AddressStatus;

import java.time.LocalDateTime;

public record ShippingAddressView(
        Long id,
        String receiverMasked,
        String phoneMasked,
        String regionMasked,
        boolean defaultAddress,
        AddressStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
