package com.dongqh.luckyhub.lottery.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DrawCommand(
        @NotBlank(message = "请求ID不能为空") @Size(max = 64, message = "请求ID不能超过64个字符") String requestId,
        @NotNull(message = "活动ID不能为空") @Positive(message = "活动ID必须大于0") Long activityId,
        @NotNull(message = "抽奖次数不能为空") Integer drawCount) {

    @AssertTrue(message = "抽奖次数只能是1或10")
    public boolean isSupportedDrawCount() {
        return drawCount != null && (drawCount == 1 || drawCount == 10);
    }
}
