package com.dongqh.luckyhub.lottery.scheduler;

import com.dongqh.luckyhub.lottery.service.OutboxRelayService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix = "luckyhub.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayScheduler {

    private final OutboxRelayService relayService;

    public OutboxRelayScheduler(OutboxRelayService relayService) {
        this.relayService = relayService;
    }

    @Scheduled(
            fixedDelayString = "${luckyhub.lottery.outbox-interval:5s}",
            initialDelayString = "${luckyhub.lottery.outbox-initial-delay:60s}"
    )
    public void relayOutbox() {
        relayService.relayBatch();
    }
}
