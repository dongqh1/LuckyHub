package com.dongqh.luckyhub.lottery.messaging;

import com.dongqh.luckyhub.lottery.entity.MessageOutbox;
import com.dongqh.luckyhub.lottery.enums.OutboxStatus;
import com.dongqh.luckyhub.lottery.mapper.MessageOutboxMapper;
import com.dongqh.luckyhub.lottery.messaging.event.DrawConfirmedEvent;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventType;
import com.dongqh.luckyhub.lottery.messaging.event.DrawReleaseRequestedEvent;
import com.dongqh.luckyhub.lottery.messaging.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.luckyhub.lottery.messaging.port.DrawEventPublisher;
import com.dongqh.luckyhub.lottery.service.OutboxServiceImpl;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxServiceTests {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void createsAUniqueUuidForEveryNewEvent() {
        DrawEventEnvelope first = newConfirmedEnvelope("request-unique-1");
        DrawEventEnvelope second = newConfirmedEnvelope("request-unique-2");

        assertThat(first.eventId()).isNotEqualTo(second.eventId());
        assertThat(first.eventId().version()).isEqualTo(4);
        assertThat(second.eventId().version()).isEqualTo(4);
    }

    @Test
    void serializesAndRestoresStableBrokerNeutralEnvelope() throws Exception {
        UUID eventId = UUID.fromString("3e0b4500-7a68-49d8-922d-c3cf8a57a3e5");
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 31, 12, 30, 45);
        DrawEventEnvelope original = new DrawEventEnvelope(
                eventId,
                DrawEventType.DRAW_CONFIRMED,
                1,
                "request-1001",
                21L,
                31L,
                41L,
                occurredAt,
                objectMapper.valueToTree(new DrawConfirmedEvent(10, LocalDate.of(2026, 7, 31))));

        String json = objectMapper.writeValueAsString(original);
        DrawEventEnvelope restored = objectMapper.readValue(json, DrawEventEnvelope.class);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.eventVersion()).isEqualTo(1);
        assertThat(restored.payloadAs(DrawConfirmedEvent.class, objectMapper))
                .isEqualTo(new DrawConfirmedEvent(10, LocalDate.of(2026, 7, 31)));
        assertThat(json).contains("\"eventType\":\"DRAW_CONFIRMED\"")
                .contains("\"eventVersion\":1")
                .contains("\"requestId\":\"request-1001\"");
    }

    @Test
    void roundTripsEveryBusinessPayloadType() throws Exception {
        DrawConfirmedEvent confirmed = new DrawConfirmedEvent(10, LocalDate.of(2026, 7, 31));
        DrawReleaseRequestedEvent release = new DrawReleaseRequestedEvent(
                1, LocalDate.of(2026, 7, 31), "DRAW_TRANSACTION_FAILED");
        PrizeFulfillmentRequestedEvent fulfillment = new PrizeFulfillmentRequestedEvent(
                51L, 52L, 53L, PrizeType.COUPON);

        assertPayloadRoundTrip(DrawEventType.DRAW_CONFIRMED, confirmed, DrawConfirmedEvent.class);
        assertPayloadRoundTrip(
                DrawEventType.DRAW_RELEASE_REQUESTED, release, DrawReleaseRequestedEvent.class);
        assertPayloadRoundTrip(
                DrawEventType.PRIZE_FULFILLMENT_REQUESTED,
                fulfillment,
                PrizeFulfillmentRequestedEvent.class);
    }

    @Test
    void defensivelyCopiesPayloadWhenConstructedAndWhenRead() {
        ObjectNode source = objectMapper.createObjectNode().put("drawCount", 1);
        DrawEventEnvelope envelope = new DrawEventEnvelope(
                UUID.randomUUID(), DrawEventType.DRAW_CONFIRMED, 1, "copy-request",
                21L, 31L, 41L, LocalDateTime.of(2026, 7, 31, 12, 30), source);

        source.put("drawCount", 10);
        ObjectNode returned = (ObjectNode) envelope.payload();
        returned.put("drawCount", 10);

        assertThat(envelope.payload().get("drawCount").asInt()).isOne();
    }

    @Test
    void persistsPendingOutboxRowUsingEventIdAsUniqueIdentity() throws Exception {
        MessageOutboxMapper mapper = mock(MessageOutboxMapper.class);
        OutboxServiceImpl service = new OutboxServiceImpl(mapper, objectMapper);
        DrawEventEnvelope event = new DrawEventEnvelope(
                UUID.fromString("18d0e169-fe78-45ef-b67d-c9eca20b11fa"),
                DrawEventType.DRAW_RELEASE_REQUESTED,
                1,
                "request-2002",
                22L,
                32L,
                42L,
                LocalDateTime.of(2026, 7, 31, 13, 0),
                objectMapper.valueToTree(new DrawReleaseRequestedEvent(
                        1, LocalDate.of(2026, 7, 31), "DRAW_TRANSACTION_FAILED")));

        service.append(event);

        ArgumentCaptor<MessageOutbox> captor = ArgumentCaptor.forClass(MessageOutbox.class);
        verify(mapper).insert(captor.capture());
        MessageOutbox saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(event.eventId().toString());
        assertThat(saved.getEventType()).isEqualTo("DRAW_RELEASE_REQUESTED");
        assertThat(saved.getEventVersion()).isEqualTo(1);
        assertThat(saved.getAggregateType()).isEqualTo("LOTTERY_DRAW_ORDER");
        assertThat(saved.getAggregateId()).isEqualTo("42");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getNextRetryAt()).isNull();
        assertThat(saved.getSentAt()).isNull();
        assertThat(objectMapper.readValue(saved.getPayload(), DrawEventEnvelope.class)).isEqualTo(event);
    }

    @Test
    void appendRequiresAndThereforeJoinsTheCallersTransaction() throws Exception {
        Method append = OutboxServiceImpl.class.getMethod("append", DrawEventEnvelope.class);
        Transactional transactional = append.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
    }

    @Test
    void eventContractsAreImmutableAndContainNoRedisStreamFields() {
        assertImmutableAndBrokerNeutral(DrawEventEnvelope.class);
        assertImmutableAndBrokerNeutral(DrawConfirmedEvent.class);
        assertImmutableAndBrokerNeutral(DrawReleaseRequestedEvent.class);
        assertImmutableAndBrokerNeutral(PrizeFulfillmentRequestedEvent.class);
        assertBrokerNeutralFields(MessageOutbox.class);

        assertThat(DrawEventPublisher.class.getDeclaredMethods())
                .singleElement()
                .extracting(Method::getName)
                .isEqualTo("publish");
    }

    private static void assertImmutableAndBrokerNeutral(Class<?> type) {
        assertThat(type.isRecord()).isTrue();
        assertThat(Arrays.stream(type.getDeclaredFields()).filter(field -> !Modifier.isStatic(field.getModifiers())))
                .allMatch(field -> Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers()));
        assertBrokerNeutralFields(type);
    }

    private DrawEventEnvelope newConfirmedEnvelope(String requestId) {
        return DrawEventEnvelope.create(
                DrawEventType.DRAW_CONFIRMED,
                requestId,
                21L,
                31L,
                41L,
                LocalDateTime.of(2026, 7, 31, 12, 30),
                new DrawConfirmedEvent(1, LocalDate.of(2026, 7, 31)),
                objectMapper);
    }

    private <T> void assertPayloadRoundTrip(
            DrawEventType eventType,
            T payload,
            Class<T> payloadType) throws Exception {
        DrawEventEnvelope original = DrawEventEnvelope.create(
                eventType,
                "payload-" + eventType,
                21L,
                31L,
                41L,
                LocalDateTime.of(2026, 7, 31, 12, 30),
                payload,
                objectMapper);
        DrawEventEnvelope restored = objectMapper.readValue(
                objectMapper.writeValueAsString(original), DrawEventEnvelope.class);

        assertThat(restored.payloadAs(payloadType, objectMapper)).isEqualTo(payload);
    }

    private static void assertBrokerNeutralFields(Class<?> type) {
        Set<String> fieldNames = Arrays.stream(type.getDeclaredFields())
                .map(Field::getName)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        assertThat(fieldNames).noneMatch(name -> name.contains("stream")
                || name.contains("redis") || name.contains("topic") || name.contains("partition"));
    }
}
