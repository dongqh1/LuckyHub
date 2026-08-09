package com.dongqh.luckyhub.prize.dto;

import com.dongqh.luckyhub.prize.enums.PrizeLevel;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

public record UpdatePrizeCommand(

        @NotBlank(message = "奖品名称不能为空")
        @Size(max = 100, message = "奖品名称不能超过100个字符")
        String prizeName,

        @NotNull(message = "奖品类型不能为空")
        PrizeType prizeType,

        @NotNull(message = "奖品等级不能为空")
        PrizeLevel prizeLevel,

        @Size(max = 500, message = "奖品图片地址不能超过500个字符")
        String imageUrl,

        @Size(max = 500, message = "奖品说明不能超过500个字符")
        String description,

        @NotNull(message = "是否可叠加不能为空")
        Boolean stackable,

        @Positive(message = "统一奖励定义ID必须大于0")
        Long rewardDefinitionId
) {
    public UpdatePrizeCommand(String prizeName, PrizeType prizeType, PrizeLevel prizeLevel,
                              String imageUrl, String description, Boolean stackable) {
        this(prizeName, prizeType, prizeLevel, imageUrl, description, stackable, null);
    }
}
