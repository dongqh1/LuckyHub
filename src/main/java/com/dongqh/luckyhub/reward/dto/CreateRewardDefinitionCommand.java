package com.dongqh.luckyhub.reward.dto;

import com.dongqh.luckyhub.reward.enums.RewardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateRewardDefinitionCommand(
        @NotBlank @Size(max = 64) String rewardCode,
        @NotBlank @Size(max = 100) String rewardName,
        @NotNull RewardType rewardType,
        @Positive Long targetId,
        @NotNull @Positive Long quantity,
        @Size(max = 2000) String configSnapshot
) {
}
