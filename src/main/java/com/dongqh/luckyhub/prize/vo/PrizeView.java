package com.dongqh.luckyhub.prize.vo;

import com.dongqh.luckyhub.prize.enums.PrizeLevel;
import com.dongqh.luckyhub.prize.enums.PrizeType;

import java.time.LocalDateTime;

public record PrizeView(
        Long id,
        String prizeName,
        PrizeType prizeType,
        PrizeLevel prizeLevel,
        String imageUrl,
        String description,
        Boolean stackable,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
