package com.dongqh.luckyhub.points.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AdminPointsAdjustmentCommand(
        @NotNull @Positive Long userId,
        @NotNull Long delta,
        @NotBlank @Size(max = 100) String businessId,
        @NotBlank @Size(max = 500) String reason
) {
    public AdminPointsAdjustmentCommand {
        if (delta != null && (delta == 0L || delta == Long.MIN_VALUE)) {
            throw new IllegalArgumentException("积分调整值不能为0或Long.MIN_VALUE");
        }
    }
}
