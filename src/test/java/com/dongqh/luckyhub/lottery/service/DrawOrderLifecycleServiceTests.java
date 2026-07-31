package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawOrder;
import com.dongqh.luckyhub.lottery.enums.DrawOrderStatus;
import com.dongqh.luckyhub.lottery.enums.LotteryErrorCode;
import com.dongqh.luckyhub.lottery.model.NewDrawOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class DrawOrderLifecycleServiceTests {

    private final DrawOrderLifecycleService service;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Set<String> requestIds = new LinkedHashSet<>();

    @Autowired
    DrawOrderLifecycleServiceTests(
            DrawOrderLifecycleService service,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.service = service;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanUpExactRows() {
        requestIds.forEach(id -> jdbcTemplate.update(
                "DELETE FROM lottery_draw_order WHERE request_id = ?", id));
        requestIds.clear();
    }

    @Test
    void createProcessingCommitsIndependentlyFromARolledBackCallerTransaction() {
        NewDrawOrder command = command(1L, 11L, 1);

        transactionTemplate.executeWithoutResult(status -> {
            service.createProcessing(command);
            status.setRollbackOnly();
        });

        assertThat(status(command.requestId())).isEqualTo(DrawOrderStatus.PROCESSING.name());
    }

    @Test
    void duplicateIdentityReturnsTheExistingOrderWithoutCreatingAnother() {
        NewDrawOrder command = command(2L, 12L, 10);

        LotteryDrawOrder first = service.createProcessing(command);
        LotteryDrawOrder duplicate = service.createProcessing(new NewDrawOrder(
                command.requestId(), command.userId(), command.activityId(), command.drawCount(),
                command.drawDate().plusDays(1)));

        assertThat(duplicate.getId()).isEqualTo(first.getId());
        assertThat(count(command.requestId())).isOne();
        assertThat(duplicate.getDrawDate()).isEqualTo(command.drawDate());
    }

    @Test
    void duplicateRequestWithDifferentIdentityRaisesIdempotencyConflict() {
        NewDrawOrder command = command(3L, 13L, 1);
        service.createProcessing(command);

        assertConflict(new NewDrawOrder(command.requestId(), 30L, command.activityId(), 1, command.drawDate()));
        assertConflict(new NewDrawOrder(command.requestId(), command.userId(), 130L, 1, command.drawDate()));
        assertConflict(new NewDrawOrder(command.requestId(), command.userId(), command.activityId(), 10, command.drawDate()));
        assertThat(count(command.requestId())).isOne();
    }

    @Test
    void markFailedOnlyTransitionsProcessingAndStoresABoundedSafeReason() {
        LotteryDrawOrder processing = service.createProcessing(command(4L, 14L, 1));
        service.markFailed(processing.getId(), "  safe failure  ");
        service.markFailed(processing.getId(), "must not overwrite");

        assertThat(orderState(processing.getId()))
                .isEqualTo(new OrderState(DrawOrderStatus.FAILED.name(), "safe failure", true));

        LotteryDrawOrder success = service.createProcessing(command(5L, 15L, 1));
        jdbcTemplate.update("UPDATE lottery_draw_order SET status = 'SUCCESS' WHERE id = ?", success.getId());
        service.markFailed(success.getId(), "must not overwrite success");

        assertThat(orderState(success.getId()).status()).isEqualTo(DrawOrderStatus.SUCCESS.name());
        assertThat(orderState(success.getId()).reason()).isNull();
    }

    private NewDrawOrder command(long userId, long activityId, int drawCount) {
        String requestId = "task9-life-" + UUID.randomUUID();
        requestIds.add(requestId);
        return new NewDrawOrder(requestId, userId, activityId, drawCount, LocalDate.of(2026, 7, 31));
    }

    private int count(String requestId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lottery_draw_order WHERE request_id = ?",
                Integer.class, requestId);
    }

    private void assertConflict(NewDrawOrder command) {
        assertThatThrownBy(() -> service.createProcessing(command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(LotteryErrorCode.IDEMPOTENCY_CONFLICT));
    }

    private String status(String requestId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM lottery_draw_order WHERE request_id = ?",
                String.class, requestId);
    }

    private OrderState orderState(long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status, fail_reason, completed_at IS NOT NULL completed FROM lottery_draw_order WHERE id = ?",
                (rs, row) -> new OrderState(rs.getString(1), rs.getString(2), rs.getBoolean(3)), orderId);
    }

    private record OrderState(String status, String reason, boolean completed) {
    }
}
