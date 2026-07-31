package com.dongqh.luckyhub.lottery.messaging.event;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record DrawEventEnvelope(
        UUID eventId,
        DrawEventType eventType,
        int eventVersion,
        String requestId,
        Long userId,
        Long activityId,
        Long orderId,
        LocalDateTime occurredAt,
        JsonNode payload) {

    public static final int CURRENT_VERSION = 1;

    public DrawEventEnvelope {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        if (eventVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("eventVersion must be " + CURRENT_VERSION);
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        requirePositive(userId, "userId");
        requirePositive(activityId, "activityId");
        requirePositive(orderId, "orderId");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        payload = payload.deepCopy();
    }

    public static DrawEventEnvelope create(
            DrawEventType eventType,
            String requestId,
            Long userId,
            Long activityId,
            Long orderId,
            LocalDateTime occurredAt,
            Object payload,
            ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        return new DrawEventEnvelope(
                UUID.randomUUID(),
                eventType,
                CURRENT_VERSION,
                requestId,
                userId,
                activityId,
                orderId,
                occurredAt,
                objectMapper.valueToTree(Objects.requireNonNull(payload, "payload must not be null")));
    }

    @Override
    public JsonNode payload() {
        return payload.deepCopy();
    }

    public <T> T payloadAs(Class<T> payloadType, ObjectMapper objectMapper) {
        try {
            return objectMapper.treeToValue(payload, payloadType);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Event payload cannot be converted to " + payloadType.getSimpleName(), exception);
        }
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
