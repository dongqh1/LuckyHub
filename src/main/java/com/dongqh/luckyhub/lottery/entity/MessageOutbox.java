package com.dongqh.luckyhub.lottery.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.luckyhub.lottery.enums.OutboxStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@TableName("message_outbox")
public class MessageOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String eventType;
    private Integer eventVersion;
    private String aggregateType;
    private String aggregateId;
    private String payload;
    private OutboxStatus status;
    private Integer retryCount;
    private String lastError;
    private String claimToken;
    private LocalDateTime nextRetryAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;
}
