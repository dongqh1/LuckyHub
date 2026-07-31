package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.lottery.config.LotteryProperties;
import com.dongqh.luckyhub.lottery.config.MessagingProperties;
import com.dongqh.luckyhub.lottery.entity.MessageOutbox;
import com.dongqh.luckyhub.lottery.mapper.MessageOutboxMapper;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.port.DrawEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "luckyhub.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayService {

    private static final int MAX_ERROR_LENGTH = 500;

    private final MessageOutboxMapper outboxMapper;
    private final DrawEventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final Duration leaseDuration;
    private final Clock clock;

    @Autowired
    public OutboxRelayService(
            MessageOutboxMapper outboxMapper,
            DrawEventPublisher publisher,
            ObjectMapper objectMapper,
            LotteryProperties properties,
            MessagingProperties messagingProperties) {
        this(outboxMapper, publisher, objectMapper, properties.outboxBatchSize(),
                messagingProperties.outboxLease());
    }

    public OutboxRelayService(
            MessageOutboxMapper outboxMapper,
            DrawEventPublisher publisher,
            ObjectMapper objectMapper,
            int batchSize) {
        this(outboxMapper, publisher, objectMapper, batchSize, Duration.ofSeconds(30));
    }

    public OutboxRelayService(MessageOutboxMapper outboxMapper, DrawEventPublisher publisher,
                              ObjectMapper objectMapper, int batchSize, Duration leaseDuration) {
        this(outboxMapper, publisher, objectMapper, batchSize, leaseDuration, Clock.systemDefaultZone());
    }

    OutboxRelayService(MessageOutboxMapper outboxMapper, DrawEventPublisher publisher,
                       ObjectMapper objectMapper, int batchSize, Duration leaseDuration, Clock clock) {
        this.outboxMapper = outboxMapper;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
        this.leaseDuration = leaseDuration;
        this.clock = clock;
    }

    public int relayBatch() {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        List<MessageOutbox> rows = outboxMapper.selectRelayCandidates(now, batchSize);
        int sent = 0;
        for (MessageOutbox row : rows) {
            String claimToken = UUID.randomUUID().toString();
            if (outboxMapper.claimForRelay(
                    row.getId(), claimToken, now, now.plus(leaseDuration)) != 1) {
                continue;
            }
            try {
                DrawEventEnvelope event = objectMapper.readValue(row.getPayload(), DrawEventEnvelope.class);
                publisher.publish(event);
                if (outboxMapper.markSent(row.getId(), claimToken,
                        LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS)) == 1) {
                    sent++;
                }
            } catch (RuntimeException exception) {
                recordFailure(row, claimToken, now, exception);
            } catch (Exception exception) {
                recordFailure(row, claimToken, now, exception);
            }
        }
        return sent;
    }

    private void recordFailure(MessageOutbox row, String claimToken,
                               LocalDateTime now, Exception exception) {
        int retry = row.getRetryCount() == null ? 1 : row.getRetryCount() + 1;
        long delaySeconds = Math.min(300L, 1L << Math.min(retry, 8));
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        if (message.length() > MAX_ERROR_LENGTH) {
            message = message.substring(0, MAX_ERROR_LENGTH);
        }
        outboxMapper.markFailed(row.getId(), claimToken, message, now.plusSeconds(delaySeconds));
    }
}
