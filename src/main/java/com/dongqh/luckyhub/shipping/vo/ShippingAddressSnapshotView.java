package com.dongqh.luckyhub.shipping.vo;

public record ShippingAddressSnapshotView(
        Long id,
        String snapshotNo,
        String receiverMasked,
        String phoneMasked,
        String regionMasked
) {
}
