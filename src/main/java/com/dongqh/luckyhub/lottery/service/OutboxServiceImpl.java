package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.lottery.entity.MessageOutbox;
import com.dongqh.luckyhub.lottery.enums.OutboxStatus;
import com.dongqh.luckyhub.lottery.mapper.MessageOutboxMapper;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

@Service
public class OutboxServiceImpl implements OutboxService {

    private static final String DRAW_ORDER_AGGREGATE = "LOTTERY_DRAW_ORDER";

    private final MessageOutboxMapper messageOutboxMapper;
    private final ObjectMapper objectMapper;

    public OutboxServiceImpl(MessageOutboxMapper messageOutboxMapper, ObjectMapper objectMapper) {
        this.messageOutboxMapper = messageOutboxMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(DrawEventEnvelope event) {
        Objects.requireNonNull(event, "event must not be null");

        MessageOutbox outbox = new MessageOutbox();
        outbox.setEventId(event.eventId().toString());
        outbox.setEventType(event.eventType().name());
        outbox.setEventVersion(event.eventVersion());
        outbox.setAggregateType(DRAW_ORDER_AGGREGATE);
        outbox.setAggregateId(event.orderId().toString());
        outbox.setPayload(serialize(event));
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setRetryCount(0);
        messageOutboxMapper.insert(outbox);
    }

    private String serialize(DrawEventEnvelope event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Draw event cannot be serialized", exception);
        }
    }
}
