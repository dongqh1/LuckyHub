package com.dongqh.luckyhub.reward.model;
public record DrawChanceRewardPayload(int chances) {
    public DrawChanceRewardPayload {
        if (chances <= 0) throw new IllegalArgumentException("抽奖次数必须大于0");
    }
}
