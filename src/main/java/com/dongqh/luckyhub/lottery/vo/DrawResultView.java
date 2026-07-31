package com.dongqh.luckyhub.lottery.vo;

import com.dongqh.luckyhub.lottery.enums.DrawResultType;
import com.dongqh.luckyhub.prize.enums.PrizeType;

public record DrawResultView(
        long recordId, int sequenceNo, DrawResultType resultType, Long prizeId,
        String prizeName, PrizeType prizeType, String prizeImageUrl, Long benefitId) {
}
