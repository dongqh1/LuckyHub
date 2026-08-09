package com.dongqh.luckyhub.lottery.quota;

import java.time.LocalDate;

/**
 * @param dailyLimit server policy snapshot used only by a new reservation; it is excluded from
 *                   the idempotency identity
 */
public record QuotaReservationRequest(
        String requestId,
        long activityId,
        long userId,
        int drawCount,
        long dailyLimit,
        LocalDate drawDate
) {
    public QuotaReservationRequest(String requestId, long activityId, long userId,
                                   int drawCount, long dailyLimit) {
        this(requestId, activityId, userId, drawCount, dailyLimit, null);
    }
}
