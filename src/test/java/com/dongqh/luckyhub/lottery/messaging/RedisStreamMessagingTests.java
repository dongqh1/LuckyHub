package com.dongqh.luckyhub.lottery.messaging;

import com.dongqh.luckyhub.lottery.config.MessagingProperties;
import com.dongqh.luckyhub.lottery.entity.MessageOutbox;
import com.dongqh.luckyhub.lottery.enums.OutboxStatus;
import com.dongqh.luckyhub.lottery.mapper.MessageOutboxMapper;
import com.dongqh.luckyhub.lottery.mapper.MessageConsumeRecordMapper;
import com.dongqh.luckyhub.lottery.messaging.event.DrawConfirmedEvent;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventType;
import com.dongqh.luckyhub.lottery.messaging.event.DrawReleaseRequestedEvent;
import com.dongqh.luckyhub.lottery.messaging.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.luckyhub.lottery.messaging.redis.RedisStreamDrawEventConsumer;
import com.dongqh.luckyhub.lottery.messaging.redis.RedisStreamDrawEventPublisher;
import com.dongqh.luckyhub.lottery.messaging.redis.RedisStreamInitializer;
import com.dongqh.luckyhub.lottery.messaging.port.DrawEventPublisher;
import com.dongqh.luckyhub.lottery.quota.DrawQuotaKeys;
import com.dongqh.luckyhub.lottery.quota.DrawQuotaService;
import com.dongqh.luckyhub.lottery.quota.QuotaReservationRequest;
import com.dongqh.luckyhub.lottery.service.MessageConsumeService;
import com.dongqh.luckyhub.lottery.service.OutboxRelayService;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "luckyhub.lottery.outbox-interval=1h",
        "luckyhub.messaging.consumer-poll-interval=1h"
})
class RedisStreamMessagingTests {

    @Autowired StringRedisTemplate redisTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired MessageOutboxMapper outboxMapper;
    @Autowired MessageConsumeRecordMapper consumeRecordMapper;
    @Autowired MessageConsumeService consumeService;
    @Autowired DrawQuotaService quotaService;
    @Autowired PlatformTransactionManager transactionManager;

    private final Set<String> streams = new java.util.HashSet<>();
    private final Set<String> eventIds = new java.util.HashSet<>();
    private final Set<String> quotaKeys = new java.util.HashSet<>();
    private final Set<String> reservationKeys = new java.util.HashSet<>();
    private final Set<String> reservationIds = new java.util.HashSet<>();

    @AfterEach
    void cleanExactRowsAndStreams() {
        eventIds.forEach(id -> outboxMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MessageOutbox>()
                        .eq(MessageOutbox::getEventId, id)));
        eventIds.forEach(id -> consumeRecordMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.dongqh.luckyhub.lottery.entity.MessageConsumeRecord>()
                        .eq(com.dongqh.luckyhub.lottery.entity.MessageConsumeRecord::getEventId, id)));
        redisTemplate.delete(streams);
        redisTemplate.delete(quotaKeys);
        redisTemplate.delete(reservationKeys);
        if (!reservationIds.isEmpty()) {
            redisTemplate.opsForZSet().remove(
                    DrawQuotaKeys.reservationTimeouts(), reservationIds.toArray());
        }
    }

    @Test
    void initializerIsIdempotentAndPublisherWritesCompleteEnvelopeFields() {
        String stream = "task11:stream:" + UUID.randomUUID();
        String group = "task11-group-" + UUID.randomUUID();
        streams.add(stream);
        MessagingProperties properties = properties(stream, group);
        RedisStreamInitializer initializer = new RedisStreamInitializer(redisTemplate, properties);
        RedisStreamDrawEventPublisher publisher =
                new RedisStreamDrawEventPublisher(redisTemplate, objectMapper, properties);

        initializer.initialize();
        initializer.initialize();
        DrawEventEnvelope event = confirmedEvent();
        publisher.publish(event);

        assertThat(redisTemplate.opsForStream().groups(stream))
                .extracting(groupInfo -> groupInfo.groupName())
                .containsExactly(group);
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .range(stream, Range.unbounded());
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getValue())
                .containsEntry("eventId", event.eventId().toString())
                .containsEntry("eventType", DrawEventType.DRAW_CONFIRMED.name())
                .containsEntry("eventVersion", "1")
                .containsEntry("requestId", event.requestId())
                .containsKeys("userId", "activityId", "orderId", "occurredAt", "payload", "envelope");
    }

    @Test
    void relayMarksSentOnlyAfterPublishAndRecordsRetryMetadataOnFailure() {
        DrawEventEnvelope success = confirmedEvent();
        DrawEventEnvelope failure = confirmedEvent();
        eventIds.add(success.eventId().toString());
        eventIds.add(failure.eventId().toString());
        insertOutbox(success);
        insertOutbox(failure);
        DrawEventPublisher publisher = event -> {
            if (event.eventId().equals(failure.eventId())) {
                throw new IllegalStateException("broker unavailable");
            }
        };
        OutboxRelayService relay = new OutboxRelayService(outboxMapper, publisher, objectMapper, 100);

        relay.relayBatch();

        MessageOutbox sent = find(success);
        MessageOutbox failed = find(failure);
        assertThat(sent.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(sent.getSentAt()).isNotNull();
        assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failed.getRetryCount()).isOne();
        assertThat(failed.getNextRetryAt()).isNotNull();
        assertThat(failed.getLastError()).contains("broker unavailable");
    }

    @Test
    void malformedOutboxPayloadIsFailedRatherThanReportedSent() {
        MessageOutbox row = new MessageOutbox();
        row.setEventId(UUID.randomUUID().toString());
        eventIds.add(row.getEventId());
        row.setEventType(DrawEventType.DRAW_CONFIRMED.name());
        row.setEventVersion(1);
        row.setAggregateType("LOTTERY_DRAW_ORDER");
        row.setAggregateId("1");
        row.setPayload("{}");
        row.setStatus(OutboxStatus.PENDING);
        row.setRetryCount(0);
        outboxMapper.insert(row);
        OutboxRelayService relay = new OutboxRelayService(outboxMapper,
                event -> { throw new AssertionError("must not publish malformed data"); }, objectMapper, 10);

        relay.relayBatch();

        MessageOutbox stored = outboxMapper.selectById(row.getId());
        assertThat(stored.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(stored.getLastError()).isNotBlank();
    }

    @Test
    void consumerConfirmsAndReleasesQuotaDeduplicatesAndAcknowledgesOnlySuccess() {
        String stream = "task11:stream:" + UUID.randomUUID();
        String group = "task11-group-" + UUID.randomUUID();
        streams.add(stream);
        MessagingProperties properties = properties(stream, group);
        RedisStreamInitializer initializer = new RedisStreamInitializer(redisTemplate, properties);
        RedisStreamDrawEventPublisher publisher =
                new RedisStreamDrawEventPublisher(redisTemplate, objectMapper, properties);
        RedisStreamDrawEventConsumer consumer =
                new RedisStreamDrawEventConsumer(redisTemplate, objectMapper, consumeService, properties);
        initializer.initialize();

        long activityId = positiveRandomLong();
        long userId = positiveRandomLong();
        String confirmedRequest = "task11-confirm-" + UUID.randomUUID();
        String releasedRequest = "task11-release-" + UUID.randomUUID();
        reserve(confirmedRequest, activityId, userId);
        reserve(releasedRequest, activityId, userId);
        DrawEventEnvelope confirmed = new DrawEventEnvelope(
                UUID.randomUUID(), DrawEventType.DRAW_CONFIRMED, 1, confirmedRequest,
                userId, activityId, positiveRandomLong(), LocalDateTime.now(),
                objectMapper.valueToTree(new DrawConfirmedEvent(1, LocalDate.now())));
        DrawEventEnvelope released = new DrawEventEnvelope(
                UUID.randomUUID(), DrawEventType.DRAW_RELEASE_REQUESTED, 1, releasedRequest,
                userId, activityId, positiveRandomLong(), LocalDateTime.now(),
                objectMapper.valueToTree(new DrawReleaseRequestedEvent(1, LocalDate.now(), "test")));
        eventIds.add(confirmed.eventId().toString());
        eventIds.add(released.eventId().toString());

        publisher.publish(confirmed);
        publisher.publish(released);
        assertThat(consumer.pollOnce()).isEqualTo(2);
        publisher.publish(confirmed);
        assertThat(consumer.pollOnce()).isOne();

        assertThat(redisTemplate.opsForHash().get(
                DrawQuotaKeys.reservation(confirmedRequest), "status")).isEqualTo("CONFIRMED");
        assertThat(redisTemplate.opsForHash().get(
                DrawQuotaKeys.reservation(releasedRequest), "status")).isEqualTo("RELEASED");
        assertThat(consumeRecordMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.dongqh.luckyhub.lottery.entity.MessageConsumeRecord>()
                        .in(com.dongqh.luckyhub.lottery.entity.MessageConsumeRecord::getEventId,
                                confirmed.eventId().toString(), released.eventId().toString())))
                .isEqualTo(2);

        DrawEventEnvelope fulfillment = new DrawEventEnvelope(
                UUID.randomUUID(), DrawEventType.PRIZE_FULFILLMENT_REQUESTED, 1,
                "task11-benefit-" + UUID.randomUUID(), userId, activityId, positiveRandomLong(),
                LocalDateTime.now(), objectMapper.valueToTree(
                        new PrizeFulfillmentRequestedEvent(1L, 2L, 3L, PrizeType.PHYSICAL)));
        eventIds.add(fulfillment.eventId().toString());
        publisher.publish(fulfillment);
        assertThat(consumer.pollOnce()).isZero();
        assertThat(redisTemplate.opsForStream().pending(stream, group).getTotalPendingMessages()).isOne();
    }

    @Test
    void concurrentRelayTransactionsCannotPublishTheSameClaimTwice() throws Exception {
        DrawEventEnvelope event = confirmedEvent();
        eventIds.add(event.eventId().toString());
        insertOutbox(event);
        AtomicInteger publications = new AtomicInteger();
        DrawEventPublisher countingPublisher = ignored -> publications.incrementAndGet();
        OutboxRelayService first = new OutboxRelayService(outboxMapper, countingPublisher, objectMapper, 1);
        OutboxRelayService second = new OutboxRelayService(outboxMapper, countingPublisher, objectMapper, 1);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> one = executor.submit(() -> {
                start.await();
                transactions.executeWithoutResult(status -> first.relayBatch());
                return null;
            });
            Future<?> two = executor.submit(() -> {
                start.await();
                transactions.executeWithoutResult(status -> second.relayBatch());
                return null;
            });
            start.countDown();
            one.get(10, TimeUnit.SECONDS);
            two.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(publications).hasValue(1);
        assertThat(find(event).getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    private MessagingProperties properties(String stream, String group) {
        return new MessagingProperties("redis-stream", stream, group, "lottery-core", 20, java.time.Duration.ofMillis(10));
    }

    private DrawEventEnvelope confirmedEvent() {
        return DrawEventEnvelope.create(DrawEventType.DRAW_CONFIRMED,
                "task11-" + UUID.randomUUID(), 11L, 12L, 13L,
                LocalDateTime.of(2026, 7, 31, 22, 0),
                new DrawConfirmedEvent(1, LocalDate.of(2026, 7, 31)), objectMapper);
    }

    private void insertOutbox(DrawEventEnvelope event) {
        MessageOutbox row = new MessageOutbox();
        row.setEventId(event.eventId().toString());
        row.setEventType(event.eventType().name());
        row.setEventVersion(event.eventVersion());
        row.setAggregateType("LOTTERY_DRAW_ORDER");
        row.setAggregateId(event.orderId().toString());
        try {
            row.setPayload(objectMapper.writeValueAsString(event));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        row.setStatus(OutboxStatus.PENDING);
        row.setRetryCount(0);
        outboxMapper.insert(row);
    }

    private MessageOutbox find(DrawEventEnvelope event) {
        return outboxMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MessageOutbox>()
                .eq(MessageOutbox::getEventId, event.eventId().toString()));
    }

    private void reserve(String requestId, long activityId, long userId) {
        LocalDate drawDate = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        reservationKeys.add(DrawQuotaKeys.reservation(requestId));
        reservationIds.add(requestId);
        quotaKeys.add(DrawQuotaKeys.quota(activityId, userId, drawDate));
        quotaService.reserve(new QuotaReservationRequest(requestId, activityId, userId, 1, 10));
    }

    private long positiveRandomLong() {
        long value = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        return value == 0 ? 1 : value;
    }
}
