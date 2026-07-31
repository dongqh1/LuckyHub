package com.dongqh.luckyhub.lottery.service.impl;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawOrder;
import com.dongqh.luckyhub.lottery.enums.DrawOrderStatus;
import com.dongqh.luckyhub.lottery.enums.LotteryErrorCode;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawOrderMapper;
import com.dongqh.luckyhub.lottery.model.NewDrawOrder;
import com.dongqh.luckyhub.lottery.service.DrawOrderLifecycleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class DrawOrderLifecycleServiceImpl implements DrawOrderLifecycleService {

    private static final int MAX_REASON_LENGTH = 500;
    private static final String DEFAULT_SAFE_REASON = "DRAW_TRANSACTION_FAILED";

    private final LotteryDrawOrderMapper orderMapper;

    public DrawOrderLifecycleServiceImpl(LotteryDrawOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
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

        LotteryDrawOrder persisted = orderMapper.selectByRequestId(command.requestId());
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
