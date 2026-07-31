package com.dongqh.luckyhub.lottery.quota;

public interface DrawQuotaService {

    QuotaReservationResult reserve(QuotaReservationRequest request);

    void confirm(String requestId);

    void release(String requestId);
}
