package com.dongqh.luckyhub.catalog.vo;

import java.time.LocalDateTime;

public record SkuView(
        Long id,
        Long productId,
        String skuCode,
        String skuName,
        Long cashPriceCent,
        Long pointsPrice,
        Boolean cashEnabled,
        Boolean pointsEnabled,
        Integer status,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
