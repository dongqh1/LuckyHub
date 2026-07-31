package com.dongqh.luckyhub.lottery.model;

import com.dongqh.luckyhub.lottery.enums.DrawOrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record DrawExecutionResult(
        long orderId,
        String requestId,
        DrawOrderStatus status,
        LocalDateTime completedAt,
        List<DrawResultItem> items) {

    public DrawExecutionResult {
        items = List.copyOf(items);
    }
}
