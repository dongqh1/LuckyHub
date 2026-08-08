package com.dongqh.luckyhub.points.dto;

import com.dongqh.luckyhub.points.enums.PointsBusinessType;
import com.dongqh.luckyhub.points.enums.PointsDirection;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PointsLedgerQuery {
    @Min(1)
    private long page = 1;
    @Min(1)
    @Max(100)
    private long size = 20;
    @Size(max = 100)
    private String businessId;
    private PointsBusinessType businessType;
    private PointsDirection direction;
}
