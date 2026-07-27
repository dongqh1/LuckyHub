package com.dongqh.luckyhub.prize.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.*;
import com.dongqh.luckyhub.prize.enums.PrizeLevel;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@TableName("marketing_prize")
public class MarketingPrize{

    @TableId(type = IdType.AUTO)
    private Long id;
    private String prizeName;
    private PrizeType prizeType;
    private PrizeLevel prizeLevel;
    private String imageUrl;
    private String description;
    private Boolean stackable;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
