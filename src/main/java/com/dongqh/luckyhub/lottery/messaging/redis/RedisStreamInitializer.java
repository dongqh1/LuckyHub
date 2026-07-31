package com.dongqh.luckyhub.lottery.messaging.redis;

import com.dongqh.luckyhub.lottery.config.MessagingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnExpression("'${luckyhub.messaging.enabled:true}' == 'true' and '${luckyhub.messaging.provider:redis-stream}' == 'redis-stream'")
public class RedisStreamInitializer {

    private final StringRedisTemplate redisTemplate;
    private final MessagingProperties properties;

    public RedisStreamInitializer(StringRedisTemplate redisTemplate, MessagingProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        RecordId marker = redisTemplate.opsForStream().add(
                properties.lotteryStream(), Map.of("initializer", "group-bootstrap"));
        try {
            redisTemplate.opsForStream().createGroup(
                    properties.lotteryStream(), ReadOffset.from("0-0"), properties.lotteryGroup());
        } catch (DataAccessException exception) {
            if (!isBusyGroup(exception)) {
                throw exception;
            }
        } finally {
            if (marker != null) {
                redisTemplate.opsForStream().delete(properties.lotteryStream(), marker);
            }
        }
    }

    private boolean isBusyGroup(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
