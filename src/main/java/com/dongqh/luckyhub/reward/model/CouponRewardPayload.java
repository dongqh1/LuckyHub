package com.dongqh.luckyhub.reward.model;
public record CouponRewardPayload(long templateId, String templateCode, int quantity) {
    public CouponRewardPayload {
        if (templateId <= 0 || templateCode == null || templateCode.isBlank() || quantity <= 0) {
            throw new IllegalArgumentException("优惠券奖励参数不合法");
        }
    }
}
