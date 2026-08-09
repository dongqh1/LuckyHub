package com.dongqh.luckyhub.prize.vo;

import com.dongqh.luckyhub.prize.enums.PrizeLevel;
import com.dongqh.luckyhub.prize.enums.PrizeType;

import java.time.LocalDateTime;
import com.dongqh.luckyhub.reward.enums.RewardType;

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
        LocalDateTime updatedAt,
        Long rewardDefinitionId,
        RewardType rewardType
) {
    public PrizeView(Long id,String prizeName,PrizeType prizeType,PrizeLevel prizeLevel,
                     String imageUrl,String description,Boolean stackable,Integer status,
                     LocalDateTime createdAt,LocalDateTime updatedAt) {
        this(id,prizeName,prizeType,prizeLevel,imageUrl,description,stackable,status,
                createdAt,updatedAt,null,null);
    }
}
