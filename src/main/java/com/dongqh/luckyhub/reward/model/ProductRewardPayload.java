package com.dongqh.luckyhub.reward.model;
public record ProductRewardPayload(long skuId, String skuCode, String productName,
                                   String skuName, int quantity) {
    public ProductRewardPayload {
        if (skuId <= 0 || skuCode == null || skuCode.isBlank()
                || productName == null || productName.isBlank()
                || skuName == null || skuName.isBlank() || quantity <= 0) {
            throw new IllegalArgumentException("商品奖励参数不合法");
        }
    }
}
