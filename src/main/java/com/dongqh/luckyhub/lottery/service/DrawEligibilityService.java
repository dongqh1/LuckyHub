package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.lottery.model.DrawPrizeSnapshot;

import java.time.LocalDateTime;
import java.util.List;

public interface DrawEligibilityService {
    EligibilitySnapshot load(long activityId);

    record EligibilitySnapshot(long activityId, int dailyLimit, int noWinWeight,
                               List<DrawPrizeSnapshot> prizes, LocalDateTime snapshotTime) {
        public EligibilitySnapshot {
            prizes = List.copyOf(prizes);
        }
    }
}
