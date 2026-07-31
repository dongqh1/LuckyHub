package com.dongqh.luckyhub.lottery.quota;

/**
 * @param dailyLimit server policy snapshot used only by a new reservation; it is excluded from
 *                   the idempotency identity
 */
public record QuotaReservationRequest(
        String requestId,
        long activityId,
        long userId,
        int drawCount,
        int dailyLimit
) {
}
