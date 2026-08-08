package com.dongqh.luckyhub.reward.vo;

import com.dongqh.luckyhub.reward.enums.RewardType;

import java.time.LocalDateTime;

public record RewardDefinitionView(
        Long id,
        String rewardCode,
        String rewardName,
        RewardType rewardType,
        Long targetId,
        Long quantity,
        String configSnapshot,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
