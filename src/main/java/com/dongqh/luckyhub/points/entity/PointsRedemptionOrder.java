package com.dongqh.luckyhub.points.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.luckyhub.catalog.enums.ProductType;
import com.dongqh.luckyhub.points.enums.PointsRedemptionStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@TableName("points_redemption_order")
public class PointsRedemptionOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String redemptionNo;
    private Long userId;
    private Long skuId;
    private Integer quantity;
    private Long unitPoints;
    private Long totalPoints;
    private String productCode;
    private String productName;
    private String skuCode;
    private String skuName;
    private ProductType productType;
    private String imageUrl;
    private PointsRedemptionStatus status;
    private String reversalNo;
    private String failureReason;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
