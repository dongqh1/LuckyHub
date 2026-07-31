package com.dongqh.luckyhub.lottery;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.lottery.algorithm.DrawRandomSource;
import com.dongqh.luckyhub.lottery.config.MessagingProperties;
import com.dongqh.luckyhub.lottery.dto.DrawCommand;
import com.dongqh.luckyhub.lottery.dto.DrawOrderQuery;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawOrder;
import com.dongqh.luckyhub.lottery.entity.MessageOutbox;
import com.dongqh.luckyhub.lottery.enums.DrawOrderStatus;
import com.dongqh.luckyhub.lottery.enums.DrawResultType;
import com.dongqh.luckyhub.lottery.enums.LotteryErrorCode;
import com.dongqh.luckyhub.lottery.enums.OutboxStatus;
import com.dongqh.luckyhub.lottery.mapper.MessageOutboxMapper;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.event.DrawConfirmedEvent;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventType;
import com.dongqh.luckyhub.lottery.messaging.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.luckyhub.lottery.messaging.port.DrawEventPublisher;
import com.dongqh.luckyhub.lottery.messaging.redis.RedisStreamDrawEventConsumer;
import com.dongqh.luckyhub.lottery.messaging.redis.RedisStreamDrawEventPublisher;
import com.dongqh.luckyhub.lottery.messaging.redis.RedisStreamInitializer;
import com.dongqh.luckyhub.lottery.model.NewDrawOrder;
import com.dongqh.luckyhub.lottery.quota.DrawQuotaKeys;
import com.dongqh.luckyhub.lottery.quota.DrawQuotaService;
import com.dongqh.luckyhub.lottery.quota.QuotaReservationRequest;
import com.dongqh.luckyhub.lottery.service.DrawOrderLifecycleService;
import com.dongqh.luckyhub.lottery.service.LotteryQueryService;
import com.dongqh.luckyhub.lottery.service.LotteryReconciliationService;
import com.dongqh.luckyhub.lottery.service.LotteryService;
import com.dongqh.luckyhub.lottery.service.MessageConsumeService;
import com.dongqh.luckyhub.lottery.service.OutboxRelayService;
import com.dongqh.luckyhub.lottery.vo.DrawOrderView;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "luckyhub.lottery.reconcile-initial-delay=24h",
        "luckyhub.messaging.consumer-initial-delay=24h",
        "luckyhub.lottery.outbox-initial-delay=24h",
        "luckyhub.activity.status-refresh-initial-delay=86400000"
})
class LotteryEndToEndTests {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final long USER_ID = 915001L;

    @Autowired LotteryService lotteryService;
    @Autowired LotteryQueryService lotteryQueryService;
    @Autowired DrawQuotaService quotaService;
    @Autowired DrawOrderLifecycleService lifecycleService;
    @Autowired LotteryReconciliationService reconciliationService;
    @Autowired MessageConsumeService consumeService;
    @Autowired MessageOutboxMapper outboxMapper;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean DrawRandomSource randomSource;

    private final List<String> requestIds = new ArrayList<>();
    private final List<Long> activityIds = new ArrayList<>();
    private final List<Long> prizeIds = new ArrayList<>();
    private final List<Long> insertedUserIds = new ArrayList<>();
    private final List<String> streams = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Mockito.reset(randomSource);
        when(randomSource.nextLong(anyLong())).thenReturn(0L);
        LoginContext.set(new LoginPrincipal(USER_ID, "task15-user", "task15-session"));
    }

    @AfterEach
    void cleanExactRowsAndKeys() {
        LoginContext.clear();
        for (String requestId : requestIds) {
            deleteQuotaForReservation(requestId);
            List<String> eventIds = jdbc.queryForList(
                    "SELECT event_id FROM message_outbox WHERE payload ->> '$.requestId' = ?",
                    String.class, requestId);
            eventIds.forEach(eventId ->
                    jdbc.update("DELETE FROM message_consume_record WHERE event_id=?", eventId));
            jdbc.update("DELETE FROM message_outbox WHERE payload ->> '$.requestId' = ?", requestId);
            jdbc.update("DELETE b FROM user_benefit b JOIN lottery_draw_record r ON r.id=b.draw_record_id WHERE r.request_id=?", requestId);
            jdbc.update("DELETE FROM lottery_draw_record WHERE request_id=?", requestId);
            jdbc.update("DELETE FROM lottery_draw_order WHERE request_id=?", requestId);
            redis.delete(DrawQuotaKeys.reservation(requestId));
            redis.opsForZSet().remove(DrawQuotaKeys.reservationTimeouts(), requestId);
        }
        for (long activityId : activityIds) {
            jdbc.update("DELETE FROM marketing_activity_prize WHERE activity_id=?", activityId);
            jdbc.update("DELETE FROM marketing_activity WHERE id=?", activityId);
        }
        for (long prizeId : prizeIds) jdbc.update("DELETE FROM marketing_prize WHERE id=?", prizeId);
        streams.forEach(redis::delete);
        for (long userId : insertedUserIds) {
            jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", userId);
            jdbc.update("DELETE FROM sys_user WHERE id=?", userId);
        }
    }

    @Test
    void realFlowPersistsWinBenefitSnapshotAndOutbox() {
        Fixture fixture = prizeActivity(1, 100, 1, 0, 10);
        String requestId = requestId();

        DrawOrderView result = lotteryService.draw(new DrawCommand(requestId, fixture.activityId(), 1));

        assertThat(result.status()).isEqualTo(DrawOrderStatus.SUCCESS);
        assertThat(result.results()).singleElement().satisfies(item -> {
            assertThat(item.resultType()).isEqualTo(DrawResultType.WIN);
            assertThat(item.prizeId()).isEqualTo(fixture.prizeId());
            assertThat(item.prizeName()).isEqualTo("Task15 Prize");
            assertThat(item.benefitId()).isNotNull();
        });
        assertThat(stock(fixture.activityPrizeId())).isZero();
        assertThat(count("user_benefit b JOIN lottery_draw_record r ON r.id=b.draw_record_id", "r.request_id=?", requestId)).isOne();
        assertThat(jdbc.queryForList("SELECT event_type FROM message_outbox WHERE payload ->> '$.requestId'=?", String.class, requestId))
                .containsExactlyInAnyOrder("DRAW_CONFIRMED", "PRIZE_FULFILLMENT_REQUESTED");
    }

    @Test
    void independentAndUnavailablePrizeIntervalsRemainNoWinWithoutRedistribution() {
        Fixture independent = prizeActivity(1, 1, 1, 100, 10);
        when(randomSource.nextLong(101L)).thenReturn(100L);
        DrawOrderView independentResult = lotteryService.draw(
                new DrawCommand(requestId(), independent.activityId(), 1));
        assertNoWin(independentResult);
        assertThat(stock(independent.activityPrizeId())).isOne();

        Fixture soldOut = prizeActivity(0, 100, 0, 0, 10);
        when(randomSource.nextLong(100L)).thenReturn(0L);
        DrawOrderView soldOutResult = lotteryService.draw(
                new DrawCommand(requestId(), soldOut.activityId(), 1));
        assertNoWin(soldOutResult);
        assertThat(stock(soldOut.activityPrizeId())).isZero();
    }

    @Test
    void stockLostAfterCandidateSelectionBecomesNoWinWithoutReroll() {
        Fixture fixture = prizeActivity(1, 100, 1, 0, 10);
        when(randomSource.nextLong(100L)).thenAnswer(invocation -> {
            jdbc.update("UPDATE marketing_activity_prize SET remaining_stock=0 WHERE id=?", fixture.activityPrizeId());
            return 0L;
        });

        DrawOrderView result = lotteryService.draw(new DrawCommand(requestId(), fixture.activityId(), 1));

        assertNoWin(result);
        Mockito.verify(randomSource, Mockito.times(1)).nextLong(100L);
        assertThat(stock(fixture.activityPrizeId())).isZero();
    }

    @Test
    void tenDrawReturnsTenCompleteOrderedResults() {
        long activityId = noWinActivity(100, 10);
        DrawOrderView result = lotteryService.draw(new DrawCommand(requestId(), activityId, 10));

        assertThat(result.results()).hasSize(10);
        assertThat(result.results()).extracting(item -> item.sequenceNo())
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(result.results()).allMatch(item -> item.resultType() == DrawResultType.NO_WIN);
        assertThat(result.results()).allSatisfy(item -> {
            assertThat(item.recordId()).isNotNull().isPositive();
            assertThat(item.prizeId()).isNull();
            assertThat(item.prizeName()).isNull();
            assertThat(item.prizeType()).isNull();
            assertThat(item.prizeImageUrl()).isNull();
            assertThat(item.benefitId()).isNull();
        });
        assertThat(count("lottery_draw_record", "request_id=?", result.requestId())).isEqualTo(10);
    }

    @Test
    void retryIsExactAndChangedIdentityIsRejectedWithoutSecondConsumption() {
        long activityId = noWinActivity(100, 10);
        String requestId = requestId();
        DrawCommand command = new DrawCommand(requestId, activityId, 1);

        DrawOrderView first = lotteryService.draw(command);
        DrawOrderView retry = lotteryService.draw(command);

        assertThat(retry).isEqualTo(first);
        assertThat(count("lottery_draw_order", "request_id=?", requestId)).isOne();
        assertThat(quotaValue(activityId, USER_ID, first.drawDate())).isEqualTo("1");
        assertThatThrownBy(() -> lotteryService.draw(new DrawCommand(requestId, activityId, 10)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(LotteryErrorCode.IDEMPOTENCY_CONFLICT));
        assertThat(quotaValue(activityId, USER_ID, first.drawDate())).isEqualTo("1");
    }

    @Test
    void failedTransactionRetainsFailedOrderAndReleaseEventReturnsReservedQuota() throws Exception {
        long activityId = noWinActivity(100, 10);
        String requestId = requestId();
        when(randomSource.nextLong(anyLong())).thenThrow(new IllegalStateException("forced transaction failure"));

        assertThatThrownBy(() -> lotteryService.draw(new DrawCommand(requestId, activityId, 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(LotteryErrorCode.DRAW_TRANSACTION_FAILED));

        assertThat(jdbc.queryForObject("SELECT status FROM lottery_draw_order WHERE request_id=?", String.class, requestId))
                .isEqualTo("FAILED");
        assertThat(count("lottery_draw_record", "request_id=?", requestId)).isZero();
        deliverThroughRealStream(outbox("DRAW_RELEASE_REQUESTED", requestId));
        assertThat(reservationStatus(requestId)).isEqualTo("RELEASED");
        assertThat(quotaValue(activityId, USER_ID, LocalDate.now(SHANGHAI))).isEqualTo("0");
    }

    @Test
    void fulfillmentUsesBenefitDatabaseTypeInsteadOfTrustingMessagePayloadType() throws Exception {
        Fixture fixture = prizeActivity(1, 100, 1, 0, 10);
        String requestId = requestId();
        DrawOrderView draw = lotteryService.draw(new DrawCommand(requestId, fixture.activityId(), 1));
        long benefitId = draw.results().get(0).benefitId();
        long recordId = draw.results().get(0).recordId();
        MessageOutbox fulfillment = outbox("PRIZE_FULFILLMENT_REQUESTED", requestId);
        DrawEventEnvelope stored = objectMapper.readValue(fulfillment.getPayload(), DrawEventEnvelope.class);
        DrawEventEnvelope tampered = new DrawEventEnvelope(
                stored.eventId(), stored.eventType(), stored.eventVersion(), stored.requestId(),
                stored.userId(), stored.activityId(), stored.orderId(), stored.occurredAt(),
                objectMapper.valueToTree(new PrizeFulfillmentRequestedEvent(
                        benefitId, recordId, fixture.prizeId(), PrizeType.PHYSICAL)));

        consumeService.consume(tampered);

        assertThat(jdbc.queryForObject("SELECT CONCAT(prize_type, ':', status) FROM user_benefit WHERE id=?",
                String.class, benefitId)).isEqualTo("COUPON:AVAILABLE");
    }

    @Test
    void unavailableStreamLeavesOutboxRetryableThenRealRedisPublishMarksItSent() {
        long activityId = noWinActivity(100, 10);
        String requestId = requestId();
        lotteryService.draw(new DrawCommand(requestId, activityId, 1));
        MessageOutbox confirmed = outbox("DRAW_CONFIRMED", requestId);
        MessageOutbox sentinel = insertSentinelOutbox();
        MessageOutboxMapper scopedMapper = scopedOutboxMapper(confirmed.getId());
        OutboxRelayService failing = new OutboxRelayService(
                scopedMapper, ignored -> { throw new IllegalStateException("stream unavailable"); }, objectMapper, 100);

        failing.relayBatch();

        MessageOutbox failed = outboxMapper.selectById(confirmed.getId());
        assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failed.getRetryCount()).isOne();
        assertThat(failed.getLastError()).contains("stream unavailable");
        failed.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        outboxMapper.updateById(failed);
        MessagingProperties properties = uniqueMessagingProperties();
        new RedisStreamInitializer(redis, properties).initialize();
        DrawEventPublisher uniqueStreamPublisher = new RedisStreamDrawEventPublisher(redis, objectMapper, properties);
        new OutboxRelayService(scopedMapper, uniqueStreamPublisher, objectMapper, 100).relayBatch();

        assertThat(outboxMapper.selectById(confirmed.getId()).getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(outboxMapper.selectById(sentinel.getId()).getStatus()).isEqualTo(OutboxStatus.PENDING);
        List<MapRecord<String, Object, Object>> records = redis.opsForStream()
                .range(properties.lotteryStream(), Range.unbounded());
        assertThat(records).anyMatch(record ->
                confirmed.getEventId().equals(record.getValue().get("eventId")));
    }

    @Test
    void timeoutReconciliationFailsProcessingOrderThenReleaseEventReturnsQuota() throws Exception {
        long activityId = noWinActivity(100, 10);
        String requestId = requestId();
        var reservation = quotaService.reserve(new QuotaReservationRequest(requestId, activityId, USER_ID, 1, 10));
        LotteryDrawOrder order = lifecycleService.createProcessing(new NewDrawOrder(
                requestId, USER_ID, activityId, 1, reservation.drawDate()));
        jdbc.update("UPDATE lottery_draw_order SET created_at=? WHERE id=?", LocalDateTime.now().minusMinutes(10), order.getId());
        Instant now = Instant.now();
        redis.opsForZSet().add(DrawQuotaKeys.reservationTimeouts(), requestId, now.minusSeconds(1).toEpochMilli());

        var result = reconciliationService.reconcileExpiredReservations(now);

        assertThat(result.timedOut()).isOne();
        assertThat(jdbc.queryForObject("SELECT CONCAT(status, ':', fail_reason) FROM lottery_draw_order WHERE id=?", String.class, order.getId()))
                .isEqualTo("FAILED:PROCESSING_TIMEOUT");
        deliverThroughRealStream(outbox("DRAW_RELEASE_REQUESTED", requestId));
        assertThat(reservationStatus(requestId)).isEqualTo("RELEASED");
        assertThat(quotaValue(activityId, USER_ID, reservation.drawDate())).isEqualTo("0");
    }

    @Test
    void realUserScopeReadsOnlySelfWhileAdminReadsAllUsers() {
        long userId = insertUser("USER");
        long adminId = insertUser("ADMIN");
        long activityId = noWinActivity(100, 10);
        LoginContext.set(new LoginPrincipal(userId, "task15-normal", "s1"));
        String userRequest = requestId();
        lotteryService.draw(new DrawCommand(userRequest, activityId, 1));
        LoginContext.set(new LoginPrincipal(adminId, "task15-admin", "s2"));
        String adminRequest = requestId();
        lotteryService.draw(new DrawCommand(adminRequest, activityId, 1));

        DrawOrderQuery query = new DrawOrderQuery();
        query.setActivityId(activityId);
        LoginContext.set(new LoginPrincipal(userId, "task15-normal", "s1"));
        assertThat(lotteryQueryService.pageOrders(query).records())
                .extracting(row -> row.requestId()).containsExactly(userRequest);

        LoginContext.set(new LoginPrincipal(adminId, "task15-admin", "s2"));
        assertThat(lotteryQueryService.pageOrders(query).records())
                .extracting(row -> row.requestId()).containsExactlyInAnyOrder(userRequest, adminRequest);
    }

    private void assertNoWin(DrawOrderView result) {
        assertThat(result.results()).singleElement().satisfies(item -> {
            assertThat(item.recordId()).isNotNull().isPositive();
            assertThat(item.resultType()).isEqualTo(DrawResultType.NO_WIN);
            assertThat(item.prizeId()).isNull();
            assertThat(item.prizeName()).isNull();
            assertThat(item.prizeType()).isNull();
            assertThat(item.prizeImageUrl()).isNull();
            assertThat(item.benefitId()).isNull();
        });
    }

    private Fixture prizeActivity(int totalStock, int weight, int remainingStock,
                                  int noWinWeight, int dailyLimit) {
        long prizeId = insert("""
                INSERT INTO marketing_prize
                    (prize_name, prize_type, prize_level, image_url, status)
                VALUES ('Task15 Prize', 'COUPON', 'FIRST', 'https://cdn/task15.png', 1)
                """);
        prizeIds.add(prizeId);
        long activityId = activity(noWinWeight, dailyLimit);
        long relationId = insert("""
                INSERT INTO marketing_activity_prize
                    (activity_id, prize_id, weight, total_stock, remaining_stock, sort_order)
                VALUES (?, ?, ?, ?, ?, 0)
                """, activityId, prizeId, weight, totalStock, remainingStock);
        return new Fixture(activityId, prizeId, relationId);
    }

    private long noWinActivity(int noWinWeight, int dailyLimit) {
        return activity(noWinWeight, dailyLimit);
    }

    private long activity(int noWinWeight, int dailyLimit) {
        long activityId = insert("""
                INSERT INTO marketing_activity
                    (activity_name, status, start_time, end_time, daily_limit, no_win_weight, created_by)
                VALUES (?, 'RUNNING', NOW(3) - INTERVAL 1 HOUR,
                        NOW(3) + INTERVAL 1 HOUR, ?, ?, 1)
                """, "task15-" + UUID.randomUUID(), dailyLimit, noWinWeight);
        activityIds.add(activityId);
        return activityId;
    }

    private long insertUser(String roleCode) {
        long id = insert("""
                INSERT INTO sys_user(username, password, nickname, status)
                VALUES (?, '$2a$10$task15.not.a.real.password.hash', 'task15', 1)
                """, "task15-" + UUID.randomUUID());
        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) SELECT ?, id FROM sys_role WHERE role_code=?", id, roleCode);
        insertedUserIds.add(id);
        return id;
    }

    private long insert(String sql, Object... args) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private String requestId() {
        String id = UUID.randomUUID().toString();
        requestIds.add(id);
        return id;
    }

    private int stock(long relationId) {
        return jdbc.queryForObject("SELECT remaining_stock FROM marketing_activity_prize WHERE id=?", Integer.class, relationId);
    }

    private int count(String table, String predicate, Object... args) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + predicate, Integer.class, args);
    }

    private MessageOutbox outbox(String eventType, String requestId) {
        return jdbc.queryForObject("SELECT * FROM message_outbox WHERE event_type=? AND payload ->> '$.requestId'=?",
                (rs, rowNum) -> outboxMapper.selectById(rs.getLong("id")), eventType, requestId);
    }

    private String quotaValue(long activityId, long userId, LocalDate date) {
        return redis.opsForValue().get(DrawQuotaKeys.quota(activityId, userId, date));
    }

    private String reservationStatus(String requestId) {
        Object value = redis.opsForHash().get(DrawQuotaKeys.reservation(requestId), "status");
        return value == null ? null : value.toString();
    }

    private void deleteQuotaForReservation(String requestId) {
        List<Object> identity = redis.opsForHash().multiGet(
                DrawQuotaKeys.reservation(requestId), List.of("activityId", "userId", "drawDate"));
        if (identity.size() == 3 && identity.stream().noneMatch(java.util.Objects::isNull)) {
            long activityId = Long.parseLong(identity.get(0).toString());
            long userId = Long.parseLong(identity.get(1).toString());
            LocalDate drawDate = LocalDate.parse(identity.get(2).toString(), DateTimeFormatter.BASIC_ISO_DATE);
            redis.delete(DrawQuotaKeys.quota(activityId, userId, drawDate));
        }
    }

    private MessagingProperties uniqueMessagingProperties() {
        String stream = "task15:stream:" + UUID.randomUUID();
        streams.add(stream);
        return new MessagingProperties(false, "redis-stream", stream,
                "task15-group-" + UUID.randomUUID(), "task15-core-" + UUID.randomUUID(),
                20, Duration.ofMillis(10), Duration.ofMillis(100), Duration.ofSeconds(30));
    }

    private void deliverThroughRealStream(MessageOutbox outbox) throws Exception {
        MessagingProperties properties = uniqueMessagingProperties();
        RedisStreamInitializer initializer = new RedisStreamInitializer(redis, properties);
        RedisStreamDrawEventPublisher publisher =
                new RedisStreamDrawEventPublisher(redis, objectMapper, properties);
        RedisStreamDrawEventConsumer consumer =
                new RedisStreamDrawEventConsumer(redis, objectMapper, consumeService, properties);
        initializer.initialize();
        OutboxRelayService relay = new OutboxRelayService(
                scopedOutboxMapper(outbox.getId()), publisher, objectMapper, 1);

        assertThat(relay.relayBatch()).isOne();
        assertThat(outboxMapper.selectById(outbox.getId()).getStatus()).isEqualTo(OutboxStatus.SENT);

        assertThat(consumer.pollOnce()).isOne();
        assertThat(redis.opsForStream().pending(
                properties.lotteryStream(), properties.lotteryGroup()).getTotalPendingMessages()).isZero();
    }

    private MessageOutboxMapper scopedOutboxMapper(long trackedId) {
        MessageOutboxMapper scoped = Mockito.mock(MessageOutboxMapper.class);
        when(scoped.selectRelayCandidates(any(LocalDateTime.class), anyInt())).thenAnswer(invocation -> {
            MessageOutbox row = outboxMapper.selectById(trackedId);
            return row == null ? List.of() : List.of(row);
        });
        when(scoped.claimForRelay(anyLong(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenAnswer(invocation -> outboxMapper.claimForRelay(
                        invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2), invocation.getArgument(3)));
        when(scoped.markSent(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> outboxMapper.markSent(
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        when(scoped.markFailed(anyLong(), anyString(), anyString(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> outboxMapper.markFailed(
                        invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2), invocation.getArgument(3)));
        return scoped;
    }

    private MessageOutbox insertSentinelOutbox() {
        String sentinelRequest = requestId();
        DrawEventEnvelope event = DrawEventEnvelope.create(
                DrawEventType.DRAW_CONFIRMED, sentinelRequest, USER_ID, 999_991L, 999_992L,
                LocalDateTime.now(), new DrawConfirmedEvent(1, LocalDate.now(SHANGHAI)), objectMapper);
        MessageOutbox row = new MessageOutbox();
        row.setEventId(event.eventId().toString());
        row.setEventType(event.eventType().name());
        row.setEventVersion(event.eventVersion());
        row.setAggregateType("LOTTERY_DRAW_ORDER");
        row.setAggregateId(event.orderId().toString());
        try {
            row.setPayload(objectMapper.writeValueAsString(event));
        } catch (Exception error) {
            throw new AssertionError(error);
        }
        row.setStatus(OutboxStatus.PENDING);
        row.setRetryCount(0);
        outboxMapper.insert(row);
        return row;
    }

    private record Fixture(long activityId, long prizeId, long activityPrizeId) {}
}
