package com.dongqh.luckyhub.points.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreatePointsRedemptionCommand(
        @NotBlank @Size(max = 64) String redemptionNo,
        @NotNull @Positive Long skuId,
        @NotNull @Min(1) @Max(100) Integer quantity
) {
}
