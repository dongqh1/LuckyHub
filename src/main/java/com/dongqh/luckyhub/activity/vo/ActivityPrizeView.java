package com.dongqh.luckyhub.activity.vo;

import com.dongqh.luckyhub.prize.enums.PrizeLevel;
import com.dongqh.luckyhub.prize.enums.PrizeType;

public record ActivityPrizeView(
        Long id,
        Long activityId,
        Long prizeId,
        String prizeName,
        PrizeType prizeType,
        PrizeLevel prizeLevel,
        String imageUrl,
        Integer prizeStatus,
        Integer weight,
        Integer totalStock,
        Integer remainingStock,
        Integer sortOrder
) {
}
