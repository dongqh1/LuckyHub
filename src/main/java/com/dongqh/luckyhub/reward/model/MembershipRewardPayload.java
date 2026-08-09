package com.dongqh.luckyhub.reward.model;
public record MembershipRewardPayload(long membershipProductId, String productCode,
                                      String membershipLevel, int durationDays, int quantity) {
    public MembershipRewardPayload {
        if (membershipProductId <= 0 || productCode == null || productCode.isBlank()
                || membershipLevel == null || membershipLevel.isBlank()
                || durationDays <= 0 || quantity <= 0) {
            throw new IllegalArgumentException("会员奖励参数不合法");
        }
    }
}
