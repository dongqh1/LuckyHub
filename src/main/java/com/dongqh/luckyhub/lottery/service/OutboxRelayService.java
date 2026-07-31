package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.lottery.config.LotteryProperties;
import com.dongqh.luckyhub.lottery.entity.MessageOutbox;
import com.dongqh.luckyhub.lottery.mapper.MessageOutboxMapper;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.port.DrawEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class OutboxRelayService {

    private static final int MAX_ERROR_LENGTH = 500;

    private final MessageOutboxMapper outboxMapper;
    private final DrawEventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final Clock clock;

    @Autowired
    public OutboxRelayService(
            MessageOutboxMapper outboxMapper,
            DrawEventPublisher publisher,
            ObjectMapper objectMapper,
            LotteryProperties properties) {
        this(outboxMapper, publisher, objectMapper, properties.outboxBatchSize());
    }

    public OutboxRelayService(
            MessageOutboxMapper outboxMapper,
            DrawEventPublisher publisher,
            ObjectMapper objectMapper,
            int batchSize) {
        this(outboxMapper, publisher, objectMapper, batchSize, Clock.systemDefaultZone());
    }

    OutboxRelayService(MessageOutboxMapper outboxMapper, DrawEventPublisher publisher,
                       ObjectMapper objectMapper, int batchSize, Clock clock) {
        this.outboxMapper = outboxMapper;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
        this.clock = clock;
    }

    @Transactional
    public int relayBatch() {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        List<MessageOutbox> rows = outboxMapper.lockRelayBatch(now, batchSize);
        int sent = 0;
        for (MessageOutbox row : rows) {
            try {
                DrawEventEnvelope event = objectMapper.readValue(row.getPayload(), DrawEventEnvelope.class);
                publisher.publish(event);
                if (outboxMapper.markSent(row.getId(), now) != 1) {
                    throw new IllegalStateException("Outbox state changed while locked");
                }
                sent++;
            } catch (RuntimeException exception) {
                recordFailure(row, now, exception);
            } catch (Exception exception) {
                recordFailure(row, now, exception);
            }
        }
        return sent;
    }

    private void recordFailure(MessageOutbox row, LocalDateTime now, Exception exception) {
        int retry = row.getRetryCount() == null ? 1 : row.getRetryCount() + 1;
        long delaySeconds = Math.min(300L, 1L << Math.min(retry, 8));
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        if (message.length() > MAX_ERROR_LENGTH) {
            message = message.substring(0, MAX_ERROR_LENGTH);
        }
        outboxMapper.markFailed(row.getId(), message, now.plusSeconds(delaySeconds));
    }
}
