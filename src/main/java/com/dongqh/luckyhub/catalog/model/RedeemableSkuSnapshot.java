package com.dongqh.luckyhub.catalog.model;

import com.dongqh.luckyhub.catalog.enums.ProductType;

public record RedeemableSkuSnapshot(
        Long skuId,
        String productCode,
        String productName,
        String skuCode,
        String skuName,
        ProductType productType,
        String imageUrl,
        Long pointsPrice
) {
}
