package com.dongqh.luckyhub.lottery.scheduler;

import com.dongqh.luckyhub.lottery.service.LotteryReconciliationService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LotteryReconciliationSchedulerTests {

    @Test
    void delegatesOneRunToReconciliationService() {
        LotteryReconciliationService service = mock(LotteryReconciliationService.class);
        LotteryReconciliationScheduler scheduler = new LotteryReconciliationScheduler(service);

        scheduler.reconcileReservations();

        verify(service).reconcileExpiredReservations(any());
    }
}
