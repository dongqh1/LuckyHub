package com.dongqh.luckyhub.lottery.vo;

import com.dongqh.luckyhub.lottery.enums.DrawResultType;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import java.time.LocalDateTime;

public record DrawRecordView(Long id, Long orderId, String requestId, Integer sequenceNo, Long userId,
                             Long activityId, DrawResultType resultType, Long prizeId, String prizeName,
                             PrizeType prizeType, String prizeImageUrl, Long benefitId, LocalDateTime drawTime) {
}
