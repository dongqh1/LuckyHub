package com.dongqh.luckyhub.lottery.model;

import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.reward.model.RewardSnapshot;

public record ValidatedLotteryReward(long benefitId, long drawRecordId, long orderId,
        String requestId, long userId, long activityId, long prizeId, PrizeType prizeType,
        BenefitStatus benefitStatus, RewardSnapshot rewardSnapshot) {
}
