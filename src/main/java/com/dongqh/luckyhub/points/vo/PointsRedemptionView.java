package com.dongqh.luckyhub.points.vo;

import com.dongqh.luckyhub.catalog.enums.ProductType;
import com.dongqh.luckyhub.points.enums.PointsRedemptionStatus;
import com.dongqh.luckyhub.shipping.vo.ShippingAddressSnapshotView;

import java.time.LocalDateTime;

public record PointsRedemptionView(
        Long id,
        String redemptionNo,
        Long userId,
        Long skuId,
        Integer quantity,
        Long unitPoints,
        Long totalPoints,
        String productCode,
        String productName,
        String skuCode,
        String skuName,
        ProductType productType,
        String imageUrl,
        PointsRedemptionStatus status,
        String reversalNo,
        String failureReason,
        ShippingAddressSnapshotView addressSnapshot,
        Long shippingOrderId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
