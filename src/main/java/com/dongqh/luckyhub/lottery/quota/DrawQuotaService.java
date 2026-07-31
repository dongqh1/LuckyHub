package com.dongqh.luckyhub.lottery.quota;

public interface DrawQuotaService {

    /**
     * Reserves quota idempotently by requestId, userId, activityId and drawCount.
     * The dailyLimit is a server policy snapshot and is not part of that identity.
     */
    QuotaReservationResult reserve(QuotaReservationRequest request);

    void confirm(String requestId);

    void release(String requestId);

    /** Removes only the timeout-index member; it never changes reservation state or quota. */
    void removeTimeout(String requestId);
}
