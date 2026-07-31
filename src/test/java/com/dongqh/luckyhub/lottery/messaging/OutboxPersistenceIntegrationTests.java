package com.dongqh.luckyhub.lottery.messaging;

import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventType;
import com.dongqh.luckyhub.lottery.messaging.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.luckyhub.lottery.service.OutboxService;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OutboxPersistenceIntegrationTests {

    private final OutboxService outboxService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Set<String> eventIdsToClean = new LinkedHashSet<>();

    @Autowired
    OutboxPersistenceIntegrationTests(
            OutboxService outboxService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanUpExactTestRows() {
        eventIdsToClean.forEach(eventId -> jdbcTemplate.update(
                "DELETE FROM message_outbox WHERE event_id = ?", eventId));
        eventIdsToClean.clear();
    }

    @Test
    void appendCommitsAndRollsBackWithTheCallersRealMysqlTransaction() {
        DrawEventEnvelope committed = newEnvelope(UUID.randomUUID(), "commit-request");
        DrawEventEnvelope rolledBack = newEnvelope(UUID.randomUUID(), "rollback-request");
        track(committed, rolledBack);

        transactionTemplate.executeWithoutResult(status -> outboxService.append(committed));
        transactionTemplate.executeWithoutResult(status -> {
            outboxService.append(rolledBack);
            status.setRollbackOnly();
        });

        assertThat(rowCount(committed.eventId())).isOne();
        assertThat(rowCount(rolledBack.eventId())).isZero();
    }

    @Test
    void mysqlUniqueConstraintRejectsADuplicateEventId() {
        DrawEventEnvelope event = newEnvelope(UUID.randomUUID(), "duplicate-request");
        track(event);
        transactionTemplate.executeWithoutResult(status -> outboxService.append(event));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                status -> outboxService.append(event)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(rowCount(event.eventId())).isOne();
    }

    @Test
    void mysqlJsonPayloadRestoresAllEnvelopeMetadataAndBusinessPayload() throws Exception {
        DrawEventEnvelope event = newEnvelope(UUID.randomUUID(), "json-round-trip-request");
        track(event);
        transactionTemplate.executeWithoutResult(status -> outboxService.append(event));

        StoredOutbox stored = jdbcTemplate.queryForObject("""
                        SELECT event_id, event_type, event_version, aggregate_type,
                               aggregate_id, payload, status, retry_count
                        FROM message_outbox
                        WHERE event_id = ?
                        """,
                (resultSet, rowNumber) -> new StoredOutbox(
                        resultSet.getString("event_id"),
                        resultSet.getString("event_type"),
                        resultSet.getInt("event_version"),
                        resultSet.getString("aggregate_type"),
                        resultSet.getString("aggregate_id"),
                        resultSet.getString("payload"),
                        resultSet.getString("status"),
                        resultSet.getInt("retry_count")),
                event.eventId().toString());

        assertThat(stored).isNotNull();
        assertThat(stored.eventId()).isEqualTo(event.eventId().toString());
        assertThat(stored.eventType()).isEqualTo(DrawEventType.PRIZE_FULFILLMENT_REQUESTED.name());
        assertThat(stored.eventVersion()).isEqualTo(1);
        assertThat(stored.aggregateType()).isEqualTo("LOTTERY_DRAW_ORDER");
        assertThat(stored.aggregateId()).isEqualTo(event.orderId().toString());
        assertThat(stored.status()).isEqualTo("PENDING");
        assertThat(stored.retryCount()).isZero();

        DrawEventEnvelope restored = objectMapper.readValue(stored.payload(), DrawEventEnvelope.class);
        assertThat(restored.eventId()).isEqualTo(event.eventId());
        assertThat(restored.eventType()).isEqualTo(event.eventType());
        assertThat(restored.eventVersion()).isEqualTo(event.eventVersion());
        assertThat(restored.requestId()).isEqualTo(event.requestId());
        assertThat(restored.userId()).isEqualTo(event.userId());
        assertThat(restored.activityId()).isEqualTo(event.activityId());
        assertThat(restored.orderId()).isEqualTo(event.orderId());
        assertThat(restored.occurredAt()).isEqualTo(event.occurredAt());
        assertThat(restored.payloadAs(PrizeFulfillmentRequestedEvent.class, objectMapper))
                .isEqualTo(new PrizeFulfillmentRequestedEvent(501L, 502L, 503L, PrizeType.PHYSICAL));
    }

    private DrawEventEnvelope newEnvelope(UUID eventId, String requestId) {
        return new DrawEventEnvelope(
                eventId,
                DrawEventType.PRIZE_FULFILLMENT_REQUESTED,
                1,
                requestId + '-' + eventId,
                101L,
                201L,
                301L,
                LocalDateTime.of(2026, 7, 31, 20, 0, 30),
                objectMapper.valueToTree(
                        new PrizeFulfillmentRequestedEvent(501L, 502L, 503L, PrizeType.PHYSICAL)));
    }

    private void track(DrawEventEnvelope... events) {
        for (DrawEventEnvelope event : events) {
            eventIdsToClean.add(event.eventId().toString());
        }
    }

    private int rowCount(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message_outbox WHERE event_id = ?",
                Integer.class,
                eventId.toString());
    }

    private record StoredOutbox(
            String eventId,
            String eventType,
            int eventVersion,
            String aggregateType,
            String aggregateId,
            String payload,
            String status,
            int retryCount) {
    }
}
