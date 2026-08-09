package com.dongqh.luckyhub.drawchance.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dongqh.luckyhub.drawchance.enums.DrawChanceBusinessType;
import com.dongqh.luckyhub.drawchance.enums.DrawChanceDirection;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("draw_chance_ledger")
public class DrawChanceLedger {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private DrawChanceBusinessType businessType;
    private String businessId;
    private DrawChanceDirection direction;
    private Long amount;
    private Long availableAfter;
    private Long reservedAfter;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
}
