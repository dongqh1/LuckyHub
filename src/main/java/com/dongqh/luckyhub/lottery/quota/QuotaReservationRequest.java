package com.dongqh.luckyhub.lottery.quota;

public record QuotaReservationRequest(
        String requestId,
        long activityId,
        long userId,
        int drawCount,
        int dailyLimit
) {
}
