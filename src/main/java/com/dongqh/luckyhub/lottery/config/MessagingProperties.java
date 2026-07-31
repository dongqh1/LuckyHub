package com.dongqh.luckyhub.lottery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "luckyhub.messaging")
public record MessagingProperties(
        String provider,
        String lotteryStream,
        String lotteryGroup,
        String logicalConsumerName,
        int consumerBatchSize,
        Duration consumerPollInterval
) {
    public MessagingProperties {
        logicalConsumerName = hasText(logicalConsumerName) ? logicalConsumerName : "lottery-core";
        consumerBatchSize = consumerBatchSize > 0 ? consumerBatchSize : 20;
        consumerPollInterval = consumerPollInterval == null ? Duration.ofSeconds(1) : consumerPollInterval;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
