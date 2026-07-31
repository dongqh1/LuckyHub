package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.lottery.dto.DrawCommand;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawOrder;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawRecord;
import com.dongqh.luckyhub.lottery.enums.DrawOrderStatus;
import com.dongqh.luckyhub.lottery.enums.DrawResultType;
import com.dongqh.luckyhub.lottery.enums.LotteryErrorCode;
import com.dongqh.luckyhub.lottery.lock.DrawLockService;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawOrderMapper;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawRecordMapper;
import com.dongqh.luckyhub.lottery.model.DrawExecutionResult;
import com.dongqh.luckyhub.lottery.model.DrawPrizeSnapshot;
import com.dongqh.luckyhub.lottery.model.DrawResultItem;
import com.dongqh.luckyhub.lottery.quota.DrawQuotaService;
import com.dongqh.luckyhub.lottery.quota.QuotaReservationResult;
import com.dongqh.luckyhub.lottery.quota.ReservationStatus;
import com.dongqh.luckyhub.lottery.service.impl.LotteryServiceImpl;
import com.dongqh.luckyhub.lottery.vo.DrawOrderView;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LotteryServiceTests {

    private final LotteryDrawOrderMapper orderMapper = mock(LotteryDrawOrderMapper.class);
    private final LotteryDrawRecordMapper recordMapper = mock(LotteryDrawRecordMapper.class);
    private final DrawEligibilityService eligibility = mock(DrawEligibilityService.class);
    private final DrawLockService lock = mock(DrawLockService.class);
    private final DrawQuotaService quota = mock(DrawQuotaService.class);
    private final DrawOrderLifecycleService lifecycle = mock(DrawOrderLifecycleService.class);
    private final DrawTransactionService transaction = mock(DrawTransactionService.class);
    private final LotteryService service = new LotteryServiceImpl(
            orderMapper, recordMapper, eligibility, lock, quota, lifecycle, transaction);

    private final String requestId = UUID.randomUUID().toString();
    private final DrawCommand command = new DrawCommand(requestId, 20L, 1);
    private final LocalDate drawDate = LocalDate.of(2026, 7, 31);
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 31, 12, 0);

    @BeforeEach
    void setUp() {
        LoginContext.set(new LoginPrincipal(10L, "user", "session"));
        when(lock.execute(anyLong(), anyLong(), any())).thenAnswer(invocation ->
                invocation.<java.util.function.Supplier<?>>getArgument(2).get());
        when(eligibility.load(20L)).thenReturn(new DrawEligibilityService.EligibilitySnapshot(
                20L, 5, 25, List.of(prize()), now));
        when(quota.reserve(any())).thenReturn(new QuotaReservationResult(
                requestId, ReservationStatus.RESERVED, drawDate, 1, false));
        when(lifecycle.createProcessing(any())).thenReturn(order(99L, DrawOrderStatus.PROCESSING));
        when(transaction.execute(any())).thenReturn(new DrawExecutionResult(
                99L, requestId, DrawOrderStatus.SUCCESS, now,
                List.of(new DrawResultItem(101L, 1, DrawResultType.WIN, 30L,
                        "一等奖", PrizeType.COUPON, "https://img", 201L))));
    }

    @AfterEach
    void clearLogin() {
        LoginContext.clear();
    }

    @Test
    void orchestratesInRequiredOrderAndReturnsExactSynchronousResult() {
        DrawOrderView result = service.draw(command);

        assertThat(result.requestId()).isEqualTo(requestId);
        assertThat(result.status()).isEqualTo(DrawOrderStatus.SUCCESS);
        assertThat(result.results()).singleElement().satisfies(item -> {
            assertThat(item.sequenceNo()).isOne();
            assertThat(item.resultType()).isEqualTo(DrawResultType.WIN);
            assertThat(item.prizeName()).isEqualTo("一等奖");
            assertThat(item.benefitId()).isEqualTo(201L);
        });
        InOrder ordered = inOrder(orderMapper, eligibility, lock, quota, lifecycle, transaction);
        ordered.verify(orderMapper).selectByRequestId(requestId);
        ordered.verify(eligibility).load(20L);
        ordered.verify(lock).execute(eq(20L), eq(10L), any());
        ordered.verify(orderMapper).selectByRequestId(requestId);
        ordered.verify(quota).reserve(argThat(r -> r.userId() == 10L && r.dailyLimit() == 5));
        ordered.verify(lifecycle).createProcessing(argThat(o -> o.drawDate().equals(drawDate)));
        ordered.verify(transaction).execute(argThat(c -> c.prizes().size() == 1
                && c.noWinWeight() == 25 && c.drawTime().equals(now)));
        verify(eligibility, times(1)).load(20L);
    }

    @Test
    void returnsStoredSuccessBeforeEligibilityEvenAcrossPolicyOrDateChanges() {
        LotteryDrawOrder success = order(7L, DrawOrderStatus.SUCCESS);
        success.setDrawDate(LocalDate.of(2026, 7, 30));
        success.setCompletedAt(now.minusDays(1));
        when(orderMapper.selectByRequestId(requestId)).thenReturn(success);
        LotteryDrawRecord record = new LotteryDrawRecord();
        record.setId(8L); record.setSequenceNo(1); record.setResultType(DrawResultType.NO_WIN);
        when(recordMapper.selectByOrderId(7L)).thenReturn(List.of(record));

        DrawOrderView result = service.draw(command);

        assertThat(result.drawDate()).isEqualTo(LocalDate.of(2026, 7, 30));
        assertThat(result.results()).singleElement().extracting("resultType")
                .isEqualTo(DrawResultType.NO_WIN);
        verifyNoInteractions(eligibility, lock, quota, lifecycle, transaction);
    }

    @Test
    void rejectsInvalidUuidAndUnsupportedDrawCountBeforeDependencies() {
        assertError(() -> service.draw(new DrawCommand("not-uuid", 20L, 1)), LotteryErrorCode.DRAW_PARAMETER_INVALID);
        assertError(() -> service.draw(new DrawCommand(UUID.randomUUID().toString(), 20L, 2)), LotteryErrorCode.DRAW_PARAMETER_INVALID);
        verifyNoInteractions(orderMapper, eligibility, lock, quota, lifecycle, transaction);
    }

    @Test
    void existingProcessingFailedAndConflictingIdentityAreTerminal() {
        when(orderMapper.selectByRequestId(requestId)).thenReturn(order(1L, DrawOrderStatus.PROCESSING));
        assertError(() -> service.draw(command), LotteryErrorCode.DRAW_ORDER_PROCESSING);

        when(orderMapper.selectByRequestId(requestId)).thenReturn(order(1L, DrawOrderStatus.FAILED));
        assertError(() -> service.draw(command), LotteryErrorCode.DRAW_ORDER_FAILED);

        LotteryDrawOrder conflict = order(1L, DrawOrderStatus.SUCCESS);
        conflict.setDrawCount(10);
        when(orderMapper.selectByRequestId(requestId)).thenReturn(conflict);
        assertError(() -> service.draw(command), LotteryErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    void secondIdempotencyCheckPreventsQuotaReservation() {
        when(orderMapper.selectByRequestId(requestId))
                .thenReturn(null, order(1L, DrawOrderStatus.PROCESSING));

        assertError(() -> service.draw(command), LotteryErrorCode.DRAW_ORDER_PROCESSING);
        verifyNoInteractions(quota, lifecycle, transaction);
    }

    @Test
    void successDiscoveredBySecondIdempotencyCheckReturnsStoredResult() {
        LotteryDrawOrder success = order(1L, DrawOrderStatus.SUCCESS);
        when(orderMapper.selectByRequestId(requestId)).thenReturn(null, success);
        LotteryDrawRecord record = new LotteryDrawRecord();
        record.setId(2L); record.setSequenceNo(1); record.setResultType(DrawResultType.NO_WIN);
        when(recordMapper.selectByOrderId(1L)).thenReturn(List.of(record));

        DrawOrderView result = service.draw(command);

        assertThat(result.orderId()).isOne();
        assertThat(result.results()).hasSize(1);
        verifyNoInteractions(quota, lifecycle, transaction);
    }

    @Test
    void failureAfterOrderCreationMarksFailedAndAppendsReleaseInNewTransaction() {
        when(transaction.execute(any())).thenThrow(new IllegalStateException("SQL password should not leak"));

        assertError(() -> service.draw(command), LotteryErrorCode.DRAW_TRANSACTION_FAILED);

        verify(lifecycle).markFailedAndRequestRelease(
                argThat(o -> o.getId() == 99L), eq("DRAW_TRANSACTION_FAILED"), eq(now));
    }

    @Test
    void mysqlFailureBeforeOrderExistsLeavesReservationForTimeoutReconciliation() {
        when(lifecycle.createProcessing(any())).thenThrow(new IllegalStateException("mysql down"));

        assertError(() -> service.draw(command), LotteryErrorCode.DRAW_TRANSACTION_FAILED);

        verify(lifecycle, never()).markFailedAndRequestRelease(any(), anyString(), any());
        verify(quota, never()).release(anyString());
    }

    @Test
    void getByRequestIdEnforcesJwtOwnership() {
        LotteryDrawOrder other = order(1L, DrawOrderStatus.SUCCESS);
        other.setUserId(999L);
        when(orderMapper.selectByRequestId(requestId)).thenReturn(other);

        assertError(() -> service.getByRequestId(requestId), LotteryErrorCode.DRAW_ACCESS_DENIED);
    }

    private LotteryDrawOrder order(long id, DrawOrderStatus status) {
        LotteryDrawOrder order = new LotteryDrawOrder();
        order.setId(id); order.setRequestId(requestId); order.setUserId(10L); order.setActivityId(20L);
        order.setDrawCount(1); order.setDrawDate(drawDate); order.setStatus(status); order.setCompletedAt(now);
        return order;
    }

    private DrawPrizeSnapshot prize() {
        return new DrawPrizeSnapshot(40L, 30L, "一等奖", PrizeType.COUPON,
                "https://img", 75, 10, true);
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
                             LotteryErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(code));
    }
}
