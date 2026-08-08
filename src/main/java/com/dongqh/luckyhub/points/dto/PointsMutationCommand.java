package com.dongqh.luckyhub.points.dto;

import com.dongqh.luckyhub.points.enums.PointsBusinessType;

public record PointsMutationCommand(
        Long userId,
        PointsBusinessType businessType,
        String businessId,
        Long amount,
        String remark
) {
}
