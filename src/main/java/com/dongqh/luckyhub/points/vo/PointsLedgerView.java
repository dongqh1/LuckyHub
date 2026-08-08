package com.dongqh.luckyhub.points.vo;

import com.dongqh.luckyhub.points.enums.PointsBusinessType;
import com.dongqh.luckyhub.points.enums.PointsDirection;

import java.time.LocalDateTime;

public record PointsLedgerView(
        Long id,
        Long userId,
        PointsBusinessType businessType,
        String businessId,
        PointsDirection direction,
        Long amount,
        Long balanceAfter,
        Long reversalOfLedgerId,
        String remark,
        LocalDateTime createdAt
) {
}
