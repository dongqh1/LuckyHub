package com.dongqh.luckyhub.prize.dto;

import com.dongqh.luckyhub.prize.enums.PrizeLevel;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePrizeCommand(

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
        Boolean stackable
) {
}
