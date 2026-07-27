package com.dongqh.luckyhub.activity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record AddActivityPrizeCommand(
        @NotNull(message = "奖品ID不能为空")
        @Positive(message = "奖品ID必须大于0")
        Long prizeId,

        @NotNull(message = "中奖权重不能为空")
        @Positive(message = "中奖权重必须大于0")
        Integer weight,

        @NotNull(message = "总库存不能为空")
        @Positive(message = "总库存必须大于0")
        Integer totalStock,

        @NotNull(message = "展示顺序不能为空")
        @PositiveOrZero(message = "展示顺序不能小于0")
        Integer sortOrder
) {
}
