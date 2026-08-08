package com.dongqh.luckyhub.points.dto;

import com.dongqh.luckyhub.points.enums.PointsBusinessType;

public record PointsReversalCommand(
        Long userId,
        PointsBusinessType originalBusinessType,
        String originalBusinessId,
        String reversalBusinessId,
        String remark
) {
}
