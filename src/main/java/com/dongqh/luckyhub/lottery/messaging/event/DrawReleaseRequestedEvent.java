package com.dongqh.luckyhub.lottery.messaging.event;

import java.time.LocalDate;
import java.util.Objects;

public record DrawReleaseRequestedEvent(int drawCount, LocalDate drawDate, String reason) {

    public DrawReleaseRequestedEvent {
        if (drawCount != 1 && drawCount != 10) {
            throw new IllegalArgumentException("drawCount must be 1 or 10");
        }
        Objects.requireNonNull(drawDate, "drawDate must not be null");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }
}
