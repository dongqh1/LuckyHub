package com.dongqh.luckyhub.shipping.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.enums.ShippingStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@TableName("shipping_order")
public class ShippingOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String shippingNo;
    private ShippingSourceType sourceType;
    private String sourceId;
    private Long targetUserId;
    private Long addressSnapshotId;
    private String skuCode;
    private String productName;
    private String imageUrl;
    private Integer quantity;
    private String fulfillmentNo;
    private String claimRequestId;
    private String carrierCode;
    private String carrierName;
    private String waybillNo;
    private ShippingStatus status;
    private String lastErrorCode;
    private String lastErrorMessage;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime failedAt;
    private LocalDateTime terminatedAt;
    @Version
    private Integer version;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
