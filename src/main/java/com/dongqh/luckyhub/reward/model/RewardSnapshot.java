package com.dongqh.luckyhub.reward.model;

import com.dongqh.luckyhub.reward.enums.RewardType;

public record RewardSnapshot(Long rewardDefinitionId, String rewardCode, RewardType rewardType,
                             Long targetId, long quantity, String payloadJson, String fingerprint) {
    public RewardSnapshot {
        if (rewardDefinitionId == null || rewardDefinitionId <= 0 || rewardCode == null
                || rewardCode.isBlank() || rewardType == null || quantity <= 0
                || payloadJson == null || payloadJson.isBlank()
                || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("奖励快照不合法");
        }
    }
}
