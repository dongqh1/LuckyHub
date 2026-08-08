package com.dongqh.luckyhub.points.dto;

import com.dongqh.luckyhub.points.enums.PointsRedemptionStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PointsRedemptionQuery {
    @Min(1)
    private long page = 1;
    @Min(1)
    @Max(100)
    private long size = 20;
    private PointsRedemptionStatus status;
}
