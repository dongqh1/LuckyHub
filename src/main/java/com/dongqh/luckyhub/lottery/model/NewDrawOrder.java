package com.dongqh.luckyhub.lottery.model;

import java.time.LocalDate;
import java.util.Objects;

public record NewDrawOrder(
        String requestId,
        long userId,
        long activityId,
        int drawCount,
        LocalDate drawDate) {

    public NewDrawOrder {
        if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
            throw new IllegalArgumentException("requestId must contain 1 to 64 characters");
        }
        if (userId <= 0 || activityId <= 0) {
            throw new IllegalArgumentException("userId and activityId must be positive");
        }
        if (drawCount != 1 && drawCount != 10) {
            throw new IllegalArgumentException("drawCount must be 1 or 10");
        }
        Objects.requireNonNull(drawDate, "drawDate must not be null");
    }
}
