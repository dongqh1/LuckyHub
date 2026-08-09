package com.dongqh.luckyhub.lottery.service.impl;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.drawchance.dto.DrawChanceReservationCommand;
import com.dongqh.luckyhub.drawchance.enums.DrawChanceReservationStatus;
import com.dongqh.luckyhub.drawchance.model.DrawChanceReservationResult;
import com.dongqh.luckyhub.drawchance.service.DrawChanceService;
import com.dongqh.luckyhub.lottery.dto.DrawCommand;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawOrder;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawRecord;
import com.dongqh.luckyhub.lottery.enums.DrawOrderStatus;
import com.dongqh.luckyhub.lottery.enums.LotteryErrorCode;
import com.dongqh.luckyhub.lottery.lock.DrawLockService;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawOrderMapper;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawRecordMapper;
import com.dongqh.luckyhub.lottery.model.DrawExecutionContext;
import com.dongqh.luckyhub.lottery.model.DrawExecutionResult;
import com.dongqh.luckyhub.lottery.model.NewDrawOrder;
import com.dongqh.luckyhub.lottery.quota.DrawQuotaService;
import com.dongqh.luckyhub.lottery.quota.QuotaReservationRequest;
import com.dongqh.luckyhub.lottery.quota.QuotaReservationResult;
import com.dongqh.luckyhub.lottery.quota.ReservationStatus;
import com.dongqh.luckyhub.lottery.service.DrawEligibilityService;
import com.dongqh.luckyhub.lottery.service.DrawOrderLifecycleService;
import com.dongqh.luckyhub.lottery.service.DrawTransactionService;
import com.dongqh.luckyhub.lottery.service.LotteryService;
import com.dongqh.luckyhub.lottery.vo.DrawOrderView;
import com.dongqh.luckyhub.lottery.vo.DrawResultView;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class LotteryServiceImpl implements LotteryService {
    private static final Logger log = LoggerFactory.getLogger(LotteryServiceImpl.class);
    private final LotteryDrawOrderMapper orderMapper;
    private final LotteryDrawRecordMapper recordMapper;
    private final DrawEligibilityService eligibilityService;
    private final DrawLockService lockService;
    private final DrawQuotaService quotaService;
    private final DrawOrderLifecycleService lifecycleService;
    private final DrawTransactionService transactionService;
    private final DrawChanceService drawChanceService;

    public LotteryServiceImpl(LotteryDrawOrderMapper orderMapper,
                              LotteryDrawRecordMapper recordMapper,
                              DrawEligibilityService eligibilityService,
                              DrawLockService lockService, DrawQuotaService quotaService,
                              DrawOrderLifecycleService lifecycleService,
                              DrawTransactionService transactionService,
                              DrawChanceService drawChanceService) {
        this.orderMapper = orderMapper;
        this.recordMapper = recordMapper;
        this.eligibilityService = eligibilityService;
        this.lockService = lockService;
        this.quotaService = quotaService;
        this.lifecycleService = lifecycleService;
        this.transactionService = transactionService;
        this.drawChanceService = drawChanceService;
    }

    @Override
    public DrawOrderView draw(DrawCommand command) {
        validate(command);
        long userId = LoginContext.require().userId();
        LotteryDrawOrder existing = orderMapper.selectByRequestId(command.requestId());
        if (existing != null) return resolveExisting(existing, command, userId, false);

        DrawEligibilityService.EligibilitySnapshot snapshot = eligibilityService.load(command.activityId());
        LocalDate drawDate = snapshot.snapshotTime().toLocalDate();
        LotteryDrawOrder[] createdOrder = new LotteryDrawOrder[1];
        try {
            LockedDraw locked = lockService.execute(command.activityId(), userId, () -> {
                LotteryDrawOrder second = orderMapper.selectByRequestId(command.requestId());
                if (second != null) {
                    return new LockedDraw(resolveExisting(second, command, userId, false), null, null);
                }
                DrawChanceReservationResult bonus = drawChanceService.reserve(
                        new DrawChanceReservationCommand(command.requestId(), command.activityId(),
                                userId, command.drawCount(), drawDate));
                requireActiveBonusReservation(bonus);
                QuotaReservationResult reserved;
                try {
                    long totalLimit = Math.addExact((long) snapshot.dailyLimit(),
                            bonus.cumulativeBonusForDate());
                    reserved = quotaService.reserve(new QuotaReservationRequest(
                            command.requestId(), command.activityId(), userId, command.drawCount(),
                            totalLimit, drawDate));
                    requireActiveReservation(reserved);
                } catch (RuntimeException error) {
                    drawChanceService.release(command.requestId());
                    throw error;
                }
                LotteryDrawOrder processing;
                try {
                    processing = lifecycleService.createProcessing(new NewDrawOrder(
                            command.requestId(), userId, command.activityId(), command.drawCount(),
                            reserved.drawDate()));
                } catch (RuntimeException error) {
                    quotaService.release(command.requestId());
                    drawChanceService.release(command.requestId());
                    throw error;
                }
                createdOrder[0] = processing;
                return new LockedDraw(null, processing, reserved);
            });
            if (locked.existingView() != null) return locked.existingView();
            LotteryDrawOrder order = locked.order();
            DrawExecutionResult result = transactionService.execute(new DrawExecutionContext(
                    order.getId(), command.requestId(), userId, command.activityId(), command.drawCount(),
                    locked.reservation().drawDate(), snapshot.noWinWeight(), snapshot.prizes(),
                    snapshot.snapshotTime()));
            return fromExecution(order, result);
        } catch (BusinessException error) {
            compensateIfNeeded(createdOrder[0], snapshot.snapshotTime());
            throw error;
        } catch (RuntimeException error) {
            compensateIfNeeded(createdOrder[0], snapshot.snapshotTime());
            throw new BusinessException(LotteryErrorCode.DRAW_TRANSACTION_FAILED);
        }
    }

    private void requireActiveBonusReservation(DrawChanceReservationResult reservation) {
        if (reservation == null || reservation.status() != DrawChanceReservationStatus.RESERVED) {
            throw new BusinessException(LotteryErrorCode.DRAW_ORDER_FAILED);
        }
    }

    @Override
    public DrawOrderView getByRequestId(String requestId) {
        validateRequestId(requestId);
        long userId = LoginContext.require().userId();
        LotteryDrawOrder order = orderMapper.selectByRequestId(requestId);
        if (order == null) throw new BusinessException(LotteryErrorCode.DRAW_ORDER_FAILED);
        if (!Long.valueOf(userId).equals(order.getUserId())) {
            throw new BusinessException(LotteryErrorCode.DRAW_ACCESS_DENIED);
        }
        return toStoredView(order);
    }

    private DrawOrderView resolveExisting(LotteryDrawOrder order, DrawCommand command,
                                          long userId, boolean reading) {
        if (!Long.valueOf(userId).equals(order.getUserId())) {
            throw new BusinessException(reading ? LotteryErrorCode.DRAW_ACCESS_DENIED
                    : LotteryErrorCode.IDEMPOTENCY_CONFLICT);
        }
        if (!command.activityId().equals(order.getActivityId())
                || !command.drawCount().equals(order.getDrawCount())) {
            throw new BusinessException(LotteryErrorCode.IDEMPOTENCY_CONFLICT);
        }
        if (order.getStatus() == DrawOrderStatus.PROCESSING) {
            throw new BusinessException(LotteryErrorCode.DRAW_ORDER_PROCESSING);
        }
        if (order.getStatus() == DrawOrderStatus.FAILED) {
            throw new BusinessException(LotteryErrorCode.DRAW_ORDER_FAILED);
        }
        return toStoredView(order);
    }

    private DrawOrderView toStoredView(LotteryDrawOrder order) {
        List<DrawResultView> results = order.getStatus() == DrawOrderStatus.SUCCESS
                ? recordMapper.selectByOrderId(order.getId()).stream().map(this::toView).toList()
                : List.of();
        return new DrawOrderView(order.getId(), order.getRequestId(), order.getActivityId(),
                order.getDrawCount(), order.getDrawDate(), order.getStatus(), order.getFailReason(),
                order.getCompletedAt(), results);
    }

    private DrawOrderView fromExecution(LotteryDrawOrder order, DrawExecutionResult result) {
        List<DrawResultView> items = result.items().stream().map(item -> new DrawResultView(
                item.recordId(), item.sequenceNo(), item.resultType(), item.prizeId(), item.prizeName(),
                item.prizeType(), item.prizeImageUrl(), item.benefitId())).toList();
        return new DrawOrderView(order.getId(), order.getRequestId(), order.getActivityId(),
                order.getDrawCount(), order.getDrawDate(), result.status(), null,
                result.completedAt(), items);
    }

    private DrawResultView toView(LotteryDrawRecord record) {
        return new DrawResultView(record.getId(), record.getSequenceNo(), record.getResultType(),
                record.getPrizeId(), record.getPrizeName(), record.getPrizeType(),
                record.getPrizeImageUrl(), record.getBenefitId());
    }

    private void compensateIfNeeded(LotteryDrawOrder order, LocalDateTime occurredAt) {
        if (order != null) {
            try {
                lifecycleService.markFailedAndRequestRelease(
                        order, "DRAW_TRANSACTION_FAILED", occurredAt);
            } catch (RuntimeException compensationError) {
                // The reservation timeout index remains the final reconciliation safety net.
                log.error("Failed to compensate lottery draw, requestId={}, orderId={}",
                        order.getRequestId(), order.getId(), compensationError);
            }
        }
    }

    private void requireActiveReservation(QuotaReservationResult reservation) {
        if (reservation == null || reservation.status() == null) {
            throw new BusinessException(LotteryErrorCode.DRAW_QUOTA_UNAVAILABLE);
        }
        // MySQL is the idempotency truth. If no order exists while Redis already says this
        // request is finalized, the requestId is not reusable: reconciliation must investigate it.
        if (reservation.status() != ReservationStatus.RESERVED) {
            throw new BusinessException(LotteryErrorCode.DRAW_ORDER_FAILED);
        }
    }

    private void validate(DrawCommand command) {
        if (command == null || command.activityId() == null || command.activityId() <= 0
                || command.drawCount() == null || (command.drawCount() != 1 && command.drawCount() != 10)) {
            throw new BusinessException(LotteryErrorCode.DRAW_PARAMETER_INVALID);
        }
        validateRequestId(command.requestId());
    }

    private void validateRequestId(String requestId) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
            throw new BusinessException(LotteryErrorCode.DRAW_PARAMETER_INVALID);
        }
        try {
            if (!UUID.fromString(requestId).toString().equalsIgnoreCase(requestId)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException error) {
            throw new BusinessException(LotteryErrorCode.DRAW_PARAMETER_INVALID);
        }
    }

    private record LockedDraw(DrawOrderView existingView, LotteryDrawOrder order,
                              QuotaReservationResult reservation) {
    }
}
