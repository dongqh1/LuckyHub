package com.dongqh.luckyhub.lottery.messaging.redis;

import com.dongqh.luckyhub.lottery.config.MessagingProperties;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.service.MessageConsumeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnExpression("'${luckyhub.messaging.enabled:true}' == 'true' and '${luckyhub.messaging.provider:redis-stream}' == 'redis-stream'")
public class RedisStreamDrawEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamDrawEventConsumer.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MessageConsumeService consumeService;
    private final MessagingProperties properties;
    private final String consumerName;

    @Autowired
    public RedisStreamDrawEventConsumer(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MessageConsumeService consumeService,
            MessagingProperties properties) {
        this(redisTemplate, objectMapper, consumeService, properties, newInstanceIdentity());
    }

    RedisStreamDrawEventConsumer(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MessageConsumeService consumeService,
            MessagingProperties properties,
            String instanceIdentity) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.consumeService = consumeService;
        this.properties = properties;
        this.consumerName = properties.logicalConsumerName() + '-' + instanceIdentity;
    }

    @Scheduled(
            fixedDelayString = "${luckyhub.messaging.consumer-poll-interval:1s}",
            initialDelayString = "${luckyhub.messaging.consumer-initial-delay:60s}"
    )
    public void poll() {
        try {
            pollOnce();
        } catch (RuntimeException exception) {
            log.warn("Redis Stream poll failed for consumer={}", consumerName, exception);
        }
    }

    public int pollOnce() {
        int acknowledged = process(claimStalePending());
        List<MapRecord<String, Object, Object>> newRecords = redisTemplate.opsForStream().read(
                Consumer.from(properties.lotteryGroup(), consumerName),
                StreamReadOptions.empty().count(properties.consumerBatchSize()),
                StreamOffset.create(properties.lotteryStream(), ReadOffset.lastConsumed()));
        return acknowledged + process(newRecords);
    }

    private int process(List<MapRecord<String, Object, Object>> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        int acknowledged = 0;
        for (MapRecord<String, Object, Object> record : records) {
            try {
                Object serialized = record.getValue().get("envelope");
                if (serialized == null) {
                    throw new IllegalArgumentException("Stream record has no envelope field");
                }
                DrawEventEnvelope event = objectMapper.readValue(serialized.toString(), DrawEventEnvelope.class);
                consumeService.consume(event);
                Long ack = redisTemplate.opsForStream().acknowledge(
                        properties.lotteryStream(), properties.lotteryGroup(), record.getId());
                if (ack != null && ack == 1L) {
                    acknowledged++;
                }
            } catch (Exception exception) {
                log.warn("Redis Stream event remains pending, recordId={}, consumer={}",
                        record.getId(), consumerName, exception);
            }
        }
        return acknowledged;
    }

    public String consumerName() {
        return consumerName;
    }

    private List<MapRecord<String, Object, Object>> claimStalePending() {
        PendingMessages pending = redisTemplate.opsForStream().pending(
                properties.lotteryStream(), properties.lotteryGroup(), Range.unbounded(),
                properties.consumerBatchSize(), properties.claimIdle());
        if (pending == null || pending.isEmpty()) {
            return List.of();
        }
        List<RecordId> ids = new ArrayList<>(pending.size());
        for (PendingMessage message : pending) {
            ids.add(message.getId());
        }
        List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream().claim(
                properties.lotteryStream(), properties.lotteryGroup(), consumerName,
                properties.claimIdle(), ids.toArray(RecordId[]::new));
        return claimed == null ? List.of() : claimed;
    }

    private static String newInstanceIdentity() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            String process = ManagementFactory.getRuntimeMXBean().getName().replace('@', '-');
            return host + '-' + process + '-' + UUID.randomUUID().toString().substring(0, 8);
        } catch (Exception exception) {
            return "instance-" + UUID.randomUUID();
        }
    }
}
