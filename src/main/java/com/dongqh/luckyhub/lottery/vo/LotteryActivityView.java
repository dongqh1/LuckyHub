package com.dongqh.luckyhub.lottery.vo;

import com.dongqh.luckyhub.activity.enums.ActivityStatus;
import java.time.LocalDateTime;

public record LotteryActivityView(Long id, String activityName, String description, ActivityStatus status,
                                  LocalDateTime startTime, LocalDateTime endTime, Integer dailyLimit) {
}
