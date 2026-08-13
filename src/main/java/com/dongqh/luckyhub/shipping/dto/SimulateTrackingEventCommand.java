package com.dongqh.luckyhub.shipping.dto;

import com.dongqh.luckyhub.shipping.enums.TrackingEventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record SimulateTrackingEventCommand(
        @NotNull TrackingEventType eventType,
        @NotNull LocalDateTime eventTime,
        @Size(max = 200) String locationSummary,
        @Size(max = 500) String description
) {
}
