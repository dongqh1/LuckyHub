package com.dongqh.luckyhub.benefit.scheduler;

import com.dongqh.luckyhub.benefit.service.LotteryRewardProjectionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LotteryRewardProjectionScheduler {
    private final LotteryRewardProjectionService projection;

    public LotteryRewardProjectionScheduler(LotteryRewardProjectionService projection) {
        this.projection = projection;
    }

    @Scheduled(
            fixedDelayString = "${luckyhub.fulfillment.projection-interval:5s}",
            initialDelayString = "${luckyhub.fulfillment.projection-initial-delay:60s}")
    public void projectSucceededRewards() {
        projection.projectBatch(100);
    }
}
