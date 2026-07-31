package com.dongqh.luckyhub.lottery.model;

import com.dongqh.luckyhub.lottery.algorithm.PrizeWeightSnapshot;
import com.dongqh.luckyhub.prize.enums.PrizeType;

import java.util.Objects;

public record DrawPrizeSnapshot(
        long activityPrizeId,
        long prizeId,
        String prizeName,
        PrizeType prizeType,
        String prizeImageUrl,
        long weight,
        int remainingStock,
        boolean enabled) {

    public DrawPrizeSnapshot {
        if (activityPrizeId <= 0 || prizeId <= 0) {
            throw new IllegalArgumentException("prize identifiers must be positive");
        }
        if (prizeName == null || prizeName.isBlank()) {
            throw new IllegalArgumentException("prizeName must not be blank");
        }
        Objects.requireNonNull(prizeType, "prizeType must not be null");
        if (weight <= 0 || remainingStock < 0) {
            throw new IllegalArgumentException("weight must be positive and stock non-negative");
        }
    }

    public PrizeWeightSnapshot toWeightSnapshot() {
        return new PrizeWeightSnapshot(activityPrizeId, prizeId, weight, remainingStock, enabled);
    }
}
