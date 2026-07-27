package com.dongqh.luckyhub.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateActivityCommand(
        @NotBlank(message = "活动名称不能为空")
        @Size(max = 100, message = "活动名称不能超过100个字符")
        String activityName,

        @Size(max = 1000, message = "活动说明不能超过1000个字符")
        String description,

        @NotNull(message = "开始时间不能为空")
        LocalDateTime startTime,

        @NotNull(message = "结束时间不能为空")
        LocalDateTime endTime,

        @NotNull(message = "每日参与次数不能为空")
        @Positive(message = "每日参与次数必须大于0")
        Integer dailyLimit
) {
}
