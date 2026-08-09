package com.dongqh.luckyhub.drawchance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("draw_chance_account")
public class DrawChanceAccount {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long availableBalance;
    private Long reservedBalance;
    @Version private Integer version;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
