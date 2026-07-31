package com.dongqh.luckyhub.lottery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "luckyhub.lottery")
public record LotteryProperties(
        ZoneId zoneId,
        Duration lockWait,
        Duration processingTimeout,
        Duration reconcileInterval,
        Duration reservationRetention,
        Duration outboxInterval,
        int outboxBatchSize
) {
}
