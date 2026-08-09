package com.dongqh.luckyhub.lottery.messaging.event;

import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.reward.enums.RewardType;

import java.util.Objects;

public record PrizeFulfillmentRequestedEvent(
        Long benefitId,
        Long drawRecordId,
        Long prizeId,
        PrizeType prizeType,
        Long rewardDefinitionId,
        RewardType rewardType,
        String rewardFingerprint) {

    public PrizeFulfillmentRequestedEvent(Long benefitId, Long drawRecordId,
                                          Long prizeId, PrizeType prizeType) {
        this(benefitId, drawRecordId, prizeId, prizeType, null, null, null);
    }

    public PrizeFulfillmentRequestedEvent {
        requirePositive(benefitId, "benefitId");
        requirePositive(drawRecordId, "drawRecordId");
        requirePositive(prizeId, "prizeId");
        Objects.requireNonNull(prizeType, "prizeType must not be null");
        boolean noRewardIdentity = rewardDefinitionId == null && rewardType == null && rewardFingerprint == null;
        boolean completeRewardIdentity = rewardDefinitionId != null && rewardType != null
                && rewardFingerprint != null && rewardFingerprint.matches("[0-9a-f]{64}");
        if (!noRewardIdentity && !completeRewardIdentity) {
            throw new IllegalArgumentException("reward identity must be complete or absent");
        }
        if (rewardDefinitionId != null) requirePositive(rewardDefinitionId, "rewardDefinitionId");
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
