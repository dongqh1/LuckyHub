package com.dongqh.luckyhub.lottery.service.impl;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawOrder;
import com.dongqh.luckyhub.lottery.enums.DrawOrderStatus;
import com.dongqh.luckyhub.lottery.enums.LotteryErrorCode;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawOrderMapper;
import com.dongqh.luckyhub.lottery.model.NewDrawOrder;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventType;
import com.dongqh.luckyhub.lottery.messaging.event.DrawReleaseRequestedEvent;
import com.dongqh.luckyhub.lottery.service.DrawOrderLifecycleService;
import com.dongqh.luckyhub.lottery.service.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;

@Service
public class DrawOrderLifecycleServiceImpl implements DrawOrderLifecycleService {

    private static final int MAX_REASON_LENGTH = 500;
    private static final String DEFAULT_SAFE_REASON = "DRAW_TRANSACTION_FAILED";

    private final LotteryDrawOrderMapper orderMapper;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public DrawOrderLifecycleServiceImpl(LotteryDrawOrderMapper orderMapper,
                                         OutboxService outboxService,
                                         ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LotteryDrawOrder createProcessing(NewDrawOrder command) {
        Objects.requireNonNull(command, "command must not be null");
        LotteryDrawOrder existing = orderMapper.selectByRequestId(command.requestId());
        if (existing != null) {
            validateIdentity(existing, command);
            return existing;
        }

        LotteryDrawOrder order = new LotteryDrawOrder();
        order.setRequestId(command.requestId());
        order.setUserId(command.userId());
        order.setActivityId(command.activityId());
        order.setDrawCount(command.drawCount());
        order.setDrawDate(command.drawDate());
        order.setStatus(DrawOrderStatus.PROCESSING);
        orderMapper.insertProcessingIfAbsent(order);

        // A locking read is a current read under InnoDB REPEATABLE READ. The first
        // plain SELECT may have established a snapshot before a concurrent creator
        // committed, so another plain SELECT could incorrectly keep returning null.
        LotteryDrawOrder persisted = orderMapper.selectByRequestIdForUpdate(command.requestId());
        validateIdentity(persisted, command);
        return persisted;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(long orderId, String safeReason) {
        if (orderId <= 0) {
            throw new IllegalArgumentException("orderId must be positive");
        }
        orderMapper.markFailedIfProcessing(orderId, normalizeReason(safeReason));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedAndRequestRelease(LotteryDrawOrder order, String safeReason,
                                            LocalDateTime occurredAt) {
        Objects.requireNonNull(order, "order must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        String reason = normalizeReason(safeReason);
        if (orderMapper.markFailedIfProcessing(order.getId(), reason) != 1) {
            return;
        }
        outboxService.append(DrawEventEnvelope.create(
                DrawEventType.DRAW_RELEASE_REQUESTED, order.getRequestId(), order.getUserId(),
                order.getActivityId(), order.getId(), occurredAt,
                new DrawReleaseRequestedEvent(order.getDrawCount(), order.getDrawDate(), reason),
                objectMapper));
    }

    private void validateIdentity(LotteryDrawOrder order, NewDrawOrder command) {
        if (order == null
                || !Objects.equals(order.getUserId(), command.userId())
                || !Objects.equals(order.getActivityId(), command.activityId())
                || !Objects.equals(order.getDrawCount(), command.drawCount())) {
            throw new BusinessException(LotteryErrorCode.IDEMPOTENCY_CONFLICT);
        }
    }

    private String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isEmpty()) {
            normalized = DEFAULT_SAFE_REASON;
        }
        return normalized.length() <= MAX_REASON_LENGTH
                ? normalized
                : normalized.substring(0, MAX_REASON_LENGTH);
    }
}
