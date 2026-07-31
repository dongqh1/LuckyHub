package com.dongqh.luckyhub.lottery.scheduler;

import com.dongqh.luckyhub.lottery.service.LotteryReconciliationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(prefix = "luckyhub.lottery", name = "reconciliation-enabled",
        havingValue = "true", matchIfMissing = true)
public class LotteryReconciliationScheduler {

    private final LotteryReconciliationService reconciliationService;

    public LotteryReconciliationScheduler(LotteryReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(
            fixedDelayString = "${luckyhub.lottery.reconcile-interval:30s}",
            initialDelayString = "${luckyhub.lottery.reconcile-initial-delay:60s}")
    public void reconcileReservations() {
        reconciliationService.reconcileExpiredReservations(Instant.now());
    }
}
