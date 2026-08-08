package com.dongqh.luckyhub.points.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.luckyhub.points.enums.PointsBusinessType;
import com.dongqh.luckyhub.points.enums.PointsDirection;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@TableName("points_ledger")
public class PointsLedger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private PointsBusinessType businessType;
    private String businessId;
    private PointsDirection direction;
    private Long amount;
    private Long balanceAfter;
    private Long reversalOfLedgerId;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
