package com.dongqh.luckyhub.shipping.vo;

import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.enums.ShippingStatus;

import java.time.LocalDateTime;

public record ShippingOrderView(
        Long id,
        String shippingNo,
        ShippingSourceType sourceType,
        String sourceId,
        Long targetUserId,
        Long addressSnapshotId,
        String skuCode,
        String productName,
        String imageUrl,
        Integer quantity,
        String fulfillmentNo,
        String carrierCode,
        String carrierName,
        String waybillNo,
        ShippingStatus status,
        String lastErrorCode,
        String lastErrorMessage,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        LocalDateTime failedAt,
        LocalDateTime terminatedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String receiverMasked,
        String phoneMasked,
        String regionMasked
) {
    public ShippingOrderView(Long id, String shippingNo, ShippingSourceType sourceType, String sourceId,
                             Long targetUserId, Long addressSnapshotId, String skuCode, String productName,
                             String imageUrl, Integer quantity, String fulfillmentNo, String carrierCode,
                             String carrierName, String waybillNo, ShippingStatus status, String lastErrorCode,
                             String lastErrorMessage, LocalDateTime shippedAt, LocalDateTime deliveredAt,
                             LocalDateTime failedAt, LocalDateTime terminatedAt, LocalDateTime createdAt,
                             LocalDateTime updatedAt) {
        this(id, shippingNo, sourceType, sourceId, targetUserId, addressSnapshotId, skuCode, productName,
                imageUrl, quantity, fulfillmentNo, carrierCode, carrierName, waybillNo, status, lastErrorCode,
                lastErrorMessage, shippedAt, deliveredAt, failedAt, terminatedAt, createdAt, updatedAt,
                null, null, null);
    }
}
