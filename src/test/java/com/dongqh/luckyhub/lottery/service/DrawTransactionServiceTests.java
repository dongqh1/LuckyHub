package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.activity.entity.MarketingActivityPrize;
import com.dongqh.luckyhub.activity.mapper.MarketingActivityPrizeMapper;
import com.dongqh.luckyhub.lottery.algorithm.DrawCandidate;
import com.dongqh.luckyhub.lottery.algorithm.WeightedDrawEngine;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawOrder;
import com.dongqh.luckyhub.lottery.enums.DrawOrderStatus;
import com.dongqh.luckyhub.lottery.enums.DrawResultType;
import com.dongqh.luckyhub.lottery.enums.LotteryErrorCode;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventType;
import com.dongqh.luckyhub.lottery.model.DrawExecutionContext;
import com.dongqh.luckyhub.lottery.model.DrawExecutionResult;
import com.dongqh.luckyhub.lottery.model.DrawPrizeSnapshot;
import com.dongqh.luckyhub.lottery.model.NewDrawOrder;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@SpringBootTest
class DrawTransactionServiceTests {

    @Autowired private DrawTransactionService transactionService;
    @Autowired private DrawOrderLifecycleService lifecycleService;
    @Autowired private MarketingActivityPrizeMapper activityPrizeMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private WeightedDrawEngine drawEngine;

    private final Set<String> requestIds = new LinkedHashSet<>();
    private final List<Long> activityPrizeIds = new ArrayList<>();

    @BeforeEach
    void resetEngine() {
        Mockito.reset(drawEngine);
    }

    @AfterEach
    void cleanUpExactRows() {
        requestIds.forEach(requestId -> {
            jdbcTemplate.update("DELETE FROM message_outbox WHERE payload ->> '$.requestId' = ?", requestId);
            jdbcTemplate.update("DELETE b FROM user_benefit b JOIN lottery_draw_record r ON r.id = b.draw_record_id WHERE r.request_id = ?", requestId);
            jdbcTemplate.update("DELETE FROM lottery_draw_record WHERE request_id = ?", requestId);
            jdbcTemplate.update("DELETE FROM lottery_draw_order WHERE request_id = ?", requestId);
        });
        activityPrizeIds.forEach(activityPrizeMapper::deleteById);
        requestIds.clear();
        activityPrizeIds.clear();
    }

    @Test
    void winningSingleDrawPersistsSnapshotBenefitSuccessAndBothOutboxEvents() {
        Fixture fixture = fixture(1, 2);
        when(drawEngine.select(anyList(), anyInt()))
                .thenReturn(DrawCandidate.prize(fixture.activityPrizeId(), fixture.prizeId()));

        DrawExecutionResult result = transactionService.execute(fixture.context());

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).resultType()).isEqualTo(DrawResultType.WIN);
        StoredRecord record = records(fixture.requestId()).get(0);
        assertThat(record).isEqualTo(new StoredRecord(
                1, "WIN", fixture.prizeId(), "Task 9 Prize", "COUPON", "https://img/prize.png"));
        assertThat(remainingStock(fixture.activityPrizeId())).isEqualTo(1);
        assertThat(orderStatus(fixture.orderId())).isEqualTo(DrawOrderStatus.SUCCESS.name());
        assertThat(benefitCount(fixture.requestId())).isOne();
        assertThat(benefitStatus(fixture.requestId())).isEqualTo("PENDING");
        assertThat(eventTypes(fixture.requestId())).containsExactlyInAnyOrder(
                DrawEventType.DRAW_CONFIRMED.name(), DrawEventType.PRIZE_FULFILLMENT_REQUESTED.name());
    }

    @Test
    void tenDrawPersistsTenOrderedResultsAndFailedInventoryCandidateBecomesNoWinWithoutReroll() {
        Fixture fixture = fixture(10, 1);
        when(drawEngine.select(anyList(), anyInt()))
                .thenReturn(DrawCandidate.prize(fixture.activityPrizeId(), fixture.prizeId()));

        DrawExecutionResult result = transactionService.execute(fixture.context());

        assertThat(result.items()).hasSize(10);
        assertThat(result.items()).extracting(item -> item.sequenceNo()).containsExactly(1,2,3,4,5,6,7,8,9,10);
        assertThat(result.items()).extracting(item -> item.resultType())
                .containsExactly(DrawResultType.WIN, DrawResultType.NO_WIN, DrawResultType.NO_WIN,
                        DrawResultType.NO_WIN, DrawResultType.NO_WIN, DrawResultType.NO_WIN,
                        DrawResultType.NO_WIN, DrawResultType.NO_WIN, DrawResultType.NO_WIN,
                        DrawResultType.NO_WIN);
        assertThat(records(fixture.requestId())).filteredOn(r -> r.resultType().equals("NO_WIN"))
                .allSatisfy(record -> {
                    assertThat(record.prizeId()).isNull();
                    assertThat(record.prizeName()).isNull();
                    assertThat(record.prizeType()).isNull();
                    assertThat(record.prizeImageUrl()).isNull();
                });
        assertThat(benefitCount(fixture.requestId())).isOne();
        assertThat(eventTypes(fixture.requestId())).containsExactlyInAnyOrder(
                DrawEventType.DRAW_CONFIRMED.name(), DrawEventType.PRIZE_FULFILLMENT_REQUESTED.name());
        Mockito.verify(drawEngine, Mockito.times(10)).select(anyList(), anyInt());
    }

    @Test
    void sequenceSevenFailureRollsBackInventoryRecordsBenefitsSuccessAndOutbox() {
        Fixture fixture = fixture(10, 10);
        when(drawEngine.select(anyList(), anyInt()))
                .thenReturn(DrawCandidate.prize(fixture.activityPrizeId(), fixture.prizeId()))
                .thenReturn(DrawCandidate.prize(fixture.activityPrizeId(), fixture.prizeId()))
                .thenReturn(DrawCandidate.prize(fixture.activityPrizeId(), fixture.prizeId()))
                .thenReturn(DrawCandidate.prize(fixture.activityPrizeId(), fixture.prizeId()))
                .thenReturn(DrawCandidate.prize(fixture.activityPrizeId(), fixture.prizeId()))
                .thenReturn(DrawCandidate.prize(fixture.activityPrizeId(), fixture.prizeId()))
                .thenThrow(new IllegalStateException("sequence 7 fault"));

        assertThatThrownBy(() -> transactionService.execute(fixture.context()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("sequence 7 fault");

        assertThat(remainingStock(fixture.activityPrizeId())).isEqualTo(10);
        assertThat(records(fixture.requestId())).isEmpty();
        assertThat(benefitCount(fixture.requestId())).isZero();
        assertThat(eventTypes(fixture.requestId())).isEmpty();
        assertThat(orderStatus(fixture.orderId())).isEqualTo(DrawOrderStatus.PROCESSING.name());

        lifecycleService.markFailed(fixture.orderId(), "DRAW_TRANSACTION_FAILED");
        assertThat(orderStatus(fixture.orderId())).isEqualTo(DrawOrderStatus.FAILED.name());
    }

    @Test
    void transactionRejectsAStaleOrderStateAndRollsBackAllBusinessWrites() {
        Fixture fixture = fixture(1, 2);
        jdbcTemplate.update("UPDATE lottery_draw_order SET status = 'FAILED' WHERE id = ?", fixture.orderId());
        when(drawEngine.select(anyList(), anyInt()))
                .thenReturn(DrawCandidate.prize(fixture.activityPrizeId(), fixture.prizeId()));

        assertThatThrownBy(() -> transactionService.execute(fixture.context()))
                .isInstanceOfSatisfying(com.dongqh.luckyhub.common.exception.BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(LotteryErrorCode.DRAW_TRANSACTION_FAILED));

        assertThat(remainingStock(fixture.activityPrizeId())).isEqualTo(2);
        assertThat(records(fixture.requestId())).isEmpty();
        assertThat(benefitCount(fixture.requestId())).isZero();
        assertThat(eventTypes(fixture.requestId())).isEmpty();
        assertThat(orderStatus(fixture.orderId())).isEqualTo(DrawOrderStatus.FAILED.name());
    }

    private Fixture fixture(int drawCount, int stock) {
        String requestId = "task9-tx-" + UUID.randomUUID();
        requestIds.add(requestId);
        long unique = Math.abs(System.nanoTime());
        long activityId = unique;
        long prizeId = unique + 1;

        MarketingActivityPrize relation = new MarketingActivityPrize();
        relation.setActivityId(activityId);
        relation.setPrizeId(prizeId);
        relation.setWeight(100);
        relation.setTotalStock(stock);
        relation.setRemainingStock(stock);
        relation.setSortOrder(0);
        activityPrizeMapper.insert(relation);
        activityPrizeIds.add(relation.getId());

        LotteryDrawOrder order = lifecycleService.createProcessing(new NewDrawOrder(
                requestId, 9001L, activityId, drawCount, LocalDate.of(2026, 7, 31)));
        DrawPrizeSnapshot snapshot = new DrawPrizeSnapshot(
                relation.getId(), prizeId, "Task 9 Prize", PrizeType.COUPON,
                "https://img/prize.png", 100, stock, true);
        DrawExecutionContext context = new DrawExecutionContext(
                order.getId(), requestId, 9001L, activityId, drawCount,
                LocalDate.of(2026, 7, 31), 0, List.of(snapshot),
                LocalDateTime.of(2026, 7, 31, 22, 0));
        return new Fixture(order.getId(), requestId, prizeId, relation.getId(), context);
    }

    private List<StoredRecord> records(String requestId) {
        return jdbcTemplate.query("""
                        SELECT sequence_no, result_type, prize_id, prize_name, prize_type, prize_image_url
                        FROM lottery_draw_record WHERE request_id = ? ORDER BY sequence_no
                        """, (rs, row) -> new StoredRecord(
                        rs.getInt(1), rs.getString(2), nullableLong(rs.getObject(3)),
                        rs.getString(4), rs.getString(5), rs.getString(6)), requestId);
    }

    private int remainingStock(long activityPrizeId) {
        return jdbcTemplate.queryForObject(
                "SELECT remaining_stock FROM marketing_activity_prize WHERE id = ?",
                Integer.class, activityPrizeId);
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private String orderStatus(long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM lottery_draw_order WHERE id = ?", String.class, orderId);
    }

    private int benefitCount(String requestId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_benefit b
                JOIN lottery_draw_record r ON r.id = b.draw_record_id
                WHERE r.request_id = ?
                """, Integer.class, requestId);
    }

    private String benefitStatus(String requestId) {
        return jdbcTemplate.queryForObject("""
                SELECT b.status FROM user_benefit b
                JOIN lottery_draw_record r ON r.id = b.draw_record_id
                WHERE r.request_id = ?
                """, String.class, requestId);
    }

    private List<String> eventTypes(String requestId) {
        return jdbcTemplate.queryForList(
                "SELECT event_type FROM message_outbox WHERE payload ->> '$.requestId' = ? ORDER BY event_type",
                String.class, requestId);
    }

    private record Fixture(long orderId, String requestId, long prizeId,
                           long activityPrizeId, DrawExecutionContext context) {
    }

    private record StoredRecord(int sequenceNo, String resultType, Long prizeId,
                                String prizeName, String prizeType, String prizeImageUrl) {
    }
}
