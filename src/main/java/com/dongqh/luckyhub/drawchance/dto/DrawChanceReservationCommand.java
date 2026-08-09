package com.dongqh.luckyhub.drawchance.dto;

import java.time.LocalDate;

public record DrawChanceReservationCommand(String requestId, long activityId, long userId,
                                           int drawCount, LocalDate drawDate) {
    public DrawChanceReservationCommand {
        if (requestId == null || requestId.isBlank() || requestId.length() > 64
                || activityId <= 0 || userId <= 0 || (drawCount != 1 && drawCount != 10)
                || drawDate == null) {
            throw new IllegalArgumentException("抽奖次数预留参数不合法");
        }
    }
}
