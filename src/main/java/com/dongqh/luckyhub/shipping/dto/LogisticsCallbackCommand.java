package com.dongqh.luckyhub.shipping.dto;

import com.dongqh.luckyhub.shipping.enums.TrackingEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record LogisticsCallbackCommand(
        @NotBlank @Size(max = 100) String callbackId,
        @NotBlank @Size(max = 100) String nonce,
        long timestampEpochSecond,
        @NotBlank @Size(max = 100) String waybillNo,
        @NotNull TrackingEventType eventType,
        @NotNull LocalDateTime eventTime,
        @Size(max = 200) String locationSummary,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 100) String signature
) {
    @Override
    public String toString() {
        return "LogisticsCallbackCommand[REDACTED]";
    }
}
