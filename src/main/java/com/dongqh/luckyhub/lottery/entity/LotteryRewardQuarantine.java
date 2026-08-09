package com.dongqh.luckyhub.lottery.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dongqh.luckyhub.lottery.enums.RewardQuarantineStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("lottery_reward_quarantine")
public class LotteryRewardQuarantine {
    @TableId(type = IdType.AUTO) private Long id;
    private String eventId;
    private String requestId;
    private Long orderId;
    private Long drawRecordId;
    private Long benefitId;
    private Long prizeId;
    private Long rewardDefinitionId;
    private String reasonCode;
    private RewardQuarantineStatus status;
    private LocalDateTime quarantinedAt;
    private LocalDateTime resolvedAt;
    private Long resolvedBy;
    private String resolutionNote;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
