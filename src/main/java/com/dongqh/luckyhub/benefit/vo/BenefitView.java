package com.dongqh.luckyhub.benefit.vo;

import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import java.time.LocalDateTime;

public record BenefitView(Long id, Long drawRecordId, Long userId, Long prizeId, PrizeType prizeType,
                          String prizeName, String prizeImageUrl, Integer quantity, BenefitStatus status,
                          LocalDateTime obtainedAt, LocalDateTime expireAt) {}
