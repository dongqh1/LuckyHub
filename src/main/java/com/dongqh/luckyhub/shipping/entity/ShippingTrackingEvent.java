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
@TableName("shipping_tracking_event")
public class ShippingTrackingEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shippingOrderId;
    private String providerEventId;
    private String waybillNo;
    private TrackingEventType eventType;
    private String locationSummary;
    private String description;
    private LocalDateTime eventTime;
    private LocalDateTime receivedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
