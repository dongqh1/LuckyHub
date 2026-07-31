package com.dongqh.luckyhub.lottery.model;

import com.dongqh.luckyhub.lottery.enums.DrawResultType;
import com.dongqh.luckyhub.prize.enums.PrizeType;

public record DrawResultItem(
        long recordId,
        int sequenceNo,
        DrawResultType resultType,
        Long prizeId,
        String prizeName,
        PrizeType prizeType,
        String prizeImageUrl,
        Long benefitId) {
}
