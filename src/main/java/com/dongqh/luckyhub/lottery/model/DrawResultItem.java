package com.dongqh.luckyhub.lottery.model;

import com.dongqh.luckyhub.lottery.enums.DrawResultType;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.reward.model.RewardSnapshot;

public record DrawResultItem(
        long recordId,
        int sequenceNo,
        DrawResultType resultType,
        Long prizeId,
        String prizeName,
        PrizeType prizeType,
        String prizeImageUrl,
        Long benefitId,
        RewardSnapshot rewardSnapshot) {

    public DrawResultItem(long recordId, int sequenceNo, DrawResultType resultType,
                          Long prizeId, String prizeName, PrizeType prizeType,
                          String prizeImageUrl, Long benefitId) {
        this(recordId, sequenceNo, resultType, prizeId, prizeName, prizeType,
                prizeImageUrl, benefitId, null);
    }
}
