package com.dongqh.luckyhub.shipping.model;

import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;

public record CreateShippingOrderCommand(
        ShippingSourceType sourceType,
        String sourceId,
        long targetUserId,
        long addressSnapshotId,
        String skuCode,
        String productName,
        String imageUrl,
        int quantity,
        String claimRequestId
) {
    public CreateShippingOrderCommand {
        if (sourceType == null) throw new IllegalArgumentException("sourceType不能为空");
        sourceId = required(sourceId, "sourceId", 100);
        if (targetUserId <= 0) throw new IllegalArgumentException("targetUserId必须大于0");
        if (addressSnapshotId <= 0) throw new IllegalArgumentException("addressSnapshotId必须大于0");
        skuCode = required(skuCode, "skuCode", 64);
        productName = required(productName, "productName", 100);
        imageUrl = optional(imageUrl, 500);
        if (quantity <= 0) throw new IllegalArgumentException("quantity必须大于0");
        claimRequestId = optional(claimRequestId, 64);
    }

    private static String required(String value, String field, int max) {
        String normalized = optional(value, max);
        if (normalized == null) throw new IllegalArgumentException(field + "不能为空");
        return normalized;
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("参数长度不合法");
        return normalized;
    }
}
