package com.dongqh.luckyhub.lottery.vo;

import com.dongqh.luckyhub.lottery.enums.DrawOrderStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DrawOrderView(
        long orderId, String requestId, long activityId, int drawCount, LocalDate drawDate,
        DrawOrderStatus status, String failReason, LocalDateTime completedAt,
        List<DrawResultView> results) {
    public DrawOrderView {
        results = List.copyOf(results);
    }
}
