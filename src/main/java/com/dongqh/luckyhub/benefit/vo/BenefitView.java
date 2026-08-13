package com.dongqh.luckyhub.benefit.vo;

import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.reward.enums.RewardType;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentStatus;
import com.dongqh.luckyhub.shipping.enums.ShippingStatus;
import java.time.LocalDateTime;

public record BenefitView(Long id, Long drawRecordId, Long userId, Long prizeId, PrizeType prizeType,
                          String prizeName, String prizeImageUrl, Integer quantity, BenefitStatus status,
                          LocalDateTime obtainedAt, LocalDateTime expireAt,
                          Long rewardDefinitionId, RewardType rewardType, Long rewardQuantity,
                          String fulfillmentNo, FulfillmentStatus fulfillmentStatus,
                          String shippingNo, ShippingStatus shippingStatus) {
    public BenefitView(Long id, Long drawRecordId, Long userId, Long prizeId, PrizeType prizeType,
                       String prizeName, String prizeImageUrl, Integer quantity, BenefitStatus status,
                       LocalDateTime obtainedAt, LocalDateTime expireAt, Long rewardDefinitionId,
                       RewardType rewardType, Long rewardQuantity, String fulfillmentNo,
                       FulfillmentStatus fulfillmentStatus) {
        this(id, drawRecordId, userId, prizeId, prizeType, prizeName, prizeImageUrl, quantity, status,
                obtainedAt, expireAt, rewardDefinitionId, rewardType, rewardQuantity, fulfillmentNo,
                fulfillmentStatus, null, null);
    }
}
