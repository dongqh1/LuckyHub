package com.dongqh.luckyhub.reward.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.luckyhub.reward.enums.RewardType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@TableName("reward_definition")
public class RewardDefinition {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String rewardCode;
    private String rewardName;
    private RewardType rewardType;
    private Long targetId;
    private Long quantity;
    private String configSnapshot;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
