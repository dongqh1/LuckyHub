package com.dongqh.luckyhub.lottery.config;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LotteryConfigurationTests {

    @Autowired
    private LotteryProperties properties;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private Environment environment;

    @Test
    void bindsSafeLotteryDefaults() {
        assertThat(properties.zoneId()).isEqualTo(ZoneId.of("Asia/Shanghai"));
        assertThat(properties.lockWait()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.processingTimeout()).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.reconcileInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.reservationRetention()).isEqualTo(Duration.ofHours(72));
        assertThat(properties.outboxInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.outboxBatchSize()).isEqualTo(100);
        assertThat(redissonClient).isNotNull();
    }

    @Test
    void exposesRedisStreamBrokerSettings() {
        assertThat(environment.getProperty("luckyhub.messaging.provider"))
                .isEqualTo("redis-stream");
        assertThat(environment.getProperty("luckyhub.messaging.lottery-stream"))
                .isEqualTo("luckyhub:stream:lottery");
        assertThat(environment.getProperty("luckyhub.messaging.lottery-group"))
                .isEqualTo("luckyhub-lottery-consumers");
    }
}
