package com.dongqh.luckyhub.lottery.scheduler;

import com.dongqh.luckyhub.drawchance.service.DrawChanceService;
import com.dongqh.luckyhub.lottery.config.LotteryProperties;
import com.dongqh.luckyhub.lottery.service.LotteryReconciliationService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LotteryReconciliationSchedulerTests {

    @Test
    void delegatesOneRunToReconciliationService() {
        LotteryReconciliationService service = mock(LotteryReconciliationService.class);
        DrawChanceService chances = mock(DrawChanceService.class);
        LotteryProperties properties = mock(LotteryProperties.class);
        org.mockito.Mockito.when(properties.zoneId()).thenReturn(java.time.ZoneId.of("Asia/Shanghai"));
        org.mockito.Mockito.when(properties.processingTimeout()).thenReturn(java.time.Duration.ofMinutes(5));
        org.mockito.Mockito.when(properties.reconcileBatchSize()).thenReturn(100);
        LotteryReconciliationScheduler scheduler = new LotteryReconciliationScheduler(service, chances, properties);

        scheduler.reconcileReservations();

        verify(service).reconcileExpiredReservations(any());
        verify(chances).reconcileExpired(anyInt(), any());
    }
}
