package com.dongqh.luckyhub.shipping.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.luckyhub.shipping.enums.TrackingEventType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@TableName("shipping_callback_receipt")
public class ShippingCallbackReceipt {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String callbackId;
    private String nonceDigest;
    private String signatureDigest;
    private String waybillNo;
    private TrackingEventType eventType;
    private LocalDateTime eventTime;
    private String status;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
