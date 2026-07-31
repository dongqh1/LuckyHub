package com.dongqh.luckyhub.lottery.messaging.event;

import com.dongqh.luckyhub.prize.enums.PrizeType;

import java.util.Objects;

public record PrizeFulfillmentRequestedEvent(
        Long benefitId,
        Long drawRecordId,
        Long prizeId,
        PrizeType prizeType) {

    public PrizeFulfillmentRequestedEvent {
        requirePositive(benefitId, "benefitId");
        requirePositive(drawRecordId, "drawRecordId");
        requirePositive(prizeId, "prizeId");
        Objects.requireNonNull(prizeType, "prizeType must not be null");
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
