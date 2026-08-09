package com.dongqh.luckyhub.drawchance.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dongqh.luckyhub.drawchance.enums.DrawChanceReservationStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("draw_chance_reservation")
public class DrawChanceReservation {
    @TableId(type = IdType.AUTO) private Long id;
    private String requestId;
    private Long userId;
    private Long activityId;
    private LocalDate drawDate;
    private Integer drawCount;
    private Long bonusReserved;
    private DrawChanceReservationStatus status;
    private LocalDateTime settledAt;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
