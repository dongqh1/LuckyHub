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
        Duration reconcileInitialDelay,
        int reconcileBatchSize,
        Duration reservationRetention,
        Duration outboxInterval,
        int outboxBatchSize
) {
    public LotteryProperties {
        if (zoneId == null) {
            throw new IllegalArgumentException("zoneId must not be null");
        }
        requirePositive(lockWait, "lockWait");
        requirePositive(processingTimeout, "processingTimeout");
        requirePositive(reconcileInterval, "reconcileInterval");
        if (reconcileInitialDelay == null || reconcileInitialDelay.isNegative()) {
            throw new IllegalArgumentException("reconcileInitialDelay must not be negative");
        }
        if (reconcileBatchSize <= 0) {
            throw new IllegalArgumentException("reconcileBatchSize must be positive");
        }
        requirePositive(reservationRetention, "reservationRetention");
        requirePositive(outboxInterval, "outboxInterval");
        if (outboxBatchSize <= 0) {
            throw new IllegalArgumentException("outboxBatchSize must be positive");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
