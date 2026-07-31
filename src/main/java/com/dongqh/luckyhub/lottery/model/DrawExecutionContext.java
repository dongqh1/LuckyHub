package com.dongqh.luckyhub.lottery.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record DrawExecutionContext(
        long orderId,
        String requestId,
        long userId,
        long activityId,
        int drawCount,
        LocalDate drawDate,
        int noWinWeight,
        List<DrawPrizeSnapshot> prizes,
        LocalDateTime drawTime) {

    public DrawExecutionContext {
        if (orderId <= 0 || userId <= 0 || activityId <= 0) {
            throw new IllegalArgumentException("order, user and activity identifiers must be positive");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (drawCount != 1 && drawCount != 10) {
            throw new IllegalArgumentException("drawCount must be 1 or 10");
        }
        if (noWinWeight < 0) {
            throw new IllegalArgumentException("noWinWeight must not be negative");
        }
        Objects.requireNonNull(drawDate, "drawDate must not be null");
        Objects.requireNonNull(prizes, "prizes must not be null");
        Objects.requireNonNull(drawTime, "drawTime must not be null");
        prizes = List.copyOf(prizes);
    }
}
