package com.dongqh.luckyhub.lottery.scheduler;

import com.dongqh.luckyhub.lottery.service.OutboxRelayService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxRelaySchedulerTests {

    @Test
    void delegatesOneBoundedRelayBatch() {
        OutboxRelayService relayService = mock(OutboxRelayService.class);
        OutboxRelayScheduler scheduler = new OutboxRelayScheduler(relayService);

        scheduler.relayOutbox();

        verify(relayService).relayBatch();
    }
}
