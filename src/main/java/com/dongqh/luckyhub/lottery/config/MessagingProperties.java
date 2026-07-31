package com.dongqh.luckyhub.lottery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "luckyhub.messaging")
public record MessagingProperties(
        boolean enabled,
        String provider,
        String lotteryStream,
        String lotteryGroup,
        String logicalConsumerName,
        int consumerBatchSize,
        Duration consumerPollInterval,
        Duration claimIdle,
        Duration outboxLease
) {
    public MessagingProperties {
        if (!hasText(lotteryStream)) {
            throw new IllegalArgumentException("lotteryStream must not be blank");
        }
        if (!hasText(lotteryGroup)) {
            throw new IllegalArgumentException("lotteryGroup must not be blank");
        }
        logicalConsumerName = hasText(logicalConsumerName) ? logicalConsumerName : "lottery-core";
        if (consumerBatchSize <= 0) {
            throw new IllegalArgumentException("consumerBatchSize must be positive");
        }
        requirePositive(consumerPollInterval, "consumerPollInterval");
        requirePositive(claimIdle, "claimIdle");
        if (claimIdle.compareTo(consumerPollInterval) <= 0) {
            throw new IllegalArgumentException("claimIdle must be greater than consumerPollInterval");
        }
        requirePositive(outboxLease, "outboxLease");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
