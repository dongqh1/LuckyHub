package com.dongqh.luckyhub.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@TableName("message_consume_record")
public class MessageConsumeRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String consumerName;
    private LocalDateTime consumedAt;
}
