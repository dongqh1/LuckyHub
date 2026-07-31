package com.dongqh.luckyhub.lottery.algorithm;

public record PrizeWeightSnapshot(
        long activityPrizeId,
        long prizeId,
        long weight,
        int remainingStock,
        boolean enabled
) {
}
