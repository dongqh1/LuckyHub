package com.dongqh.luckyhub.lottery.messaging.redis;

import com.dongqh.luckyhub.lottery.config.MessagingProperties;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.port.DrawEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnExpression("'${luckyhub.messaging.enabled:true}' == 'true' and '${luckyhub.messaging.provider:redis-stream}' == 'redis-stream'")
public class RedisStreamDrawEventPublisher implements DrawEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MessagingProperties properties;

    public RedisStreamDrawEventPublisher(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MessagingProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void publish(DrawEventEnvelope event) {
        try {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("eventId", event.eventId().toString());
            fields.put("eventType", event.eventType().name());
            fields.put("eventVersion", Integer.toString(event.eventVersion()));
            fields.put("requestId", event.requestId());
            fields.put("userId", event.userId().toString());
            fields.put("activityId", event.activityId().toString());
            fields.put("orderId", event.orderId().toString());
            fields.put("occurredAt", event.occurredAt().toString());
            fields.put("payload", objectMapper.writeValueAsString(event.payload()));
            fields.put("envelope", objectMapper.writeValueAsString(event));
            if (redisTemplate.opsForStream().add(properties.lotteryStream(), fields) == null) {
                throw new IllegalStateException("Redis Stream did not return a record id");
            }
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Draw event cannot be serialized", exception);
        }
    }
}
