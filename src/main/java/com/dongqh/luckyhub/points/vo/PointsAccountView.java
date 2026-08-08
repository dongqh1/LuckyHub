package com.dongqh.luckyhub.points.vo;

import java.time.LocalDateTime;

public record PointsAccountView(Long userId, Long balance, LocalDateTime updatedAt) {
}
