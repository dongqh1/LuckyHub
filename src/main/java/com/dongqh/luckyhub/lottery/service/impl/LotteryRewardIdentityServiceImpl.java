package com.dongqh.luckyhub.lottery.service.impl;

import com.dongqh.luckyhub.benefit.mapper.UserBenefitMapper;
import com.dongqh.luckyhub.lottery.enums.DrawOrderStatus;
import com.dongqh.luckyhub.lottery.enums.RewardQuarantineReason;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.luckyhub.lottery.model.LotteryRewardIdentityRow;
import com.dongqh.luckyhub.lottery.model.ValidatedLotteryReward;
import com.dongqh.luckyhub.lottery.service.LotteryRewardIdentityService;
import com.dongqh.luckyhub.lottery.service.RewardIdentityMismatchException;
import com.dongqh.luckyhub.reward.model.RewardSnapshot;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class LotteryRewardIdentityServiceImpl implements LotteryRewardIdentityService {
    private final UserBenefitMapper benefits;

    public LotteryRewardIdentityServiceImpl(UserBenefitMapper benefits) {
        this.benefits = benefits;
    }

    @Override
    public ValidatedLotteryReward validate(DrawEventEnvelope envelope,
                                           PrizeFulfillmentRequestedEvent payload) {
        LotteryRewardIdentityRow row = benefits.lockLotteryRewardIdentity(payload.benefitId());
        if (row == null) throw mismatch(RewardQuarantineReason.IDENTITY_NOT_FOUND);
        if (!Objects.equals(envelope.requestId(), row.getRequestId())
                || !Objects.equals(envelope.orderId(), row.getOrderId())
                || !Objects.equals(envelope.userId(), row.getOrderUserId())
                || !Objects.equals(envelope.activityId(), row.getOrderActivityId())
                || row.getOrderStatus() != DrawOrderStatus.SUCCESS) {
            throw mismatch(RewardQuarantineReason.ORDER_IDENTITY_MISMATCH);
        }
        if (!Objects.equals(row.getOrderUserId(), row.getRecordUserId())
                || !Objects.equals(row.getOrderActivityId(), row.getRecordActivityId())
                || !Objects.equals(payload.drawRecordId(), row.getDrawRecordId())
                || !Objects.equals(payload.prizeId(), row.getRecordPrizeId())
                || payload.prizeType() != row.getRecordPrizeType()) {
            throw mismatch(RewardQuarantineReason.RECORD_IDENTITY_MISMATCH);
        }
        if (!Objects.equals(payload.benefitId(), row.getBenefitId())
                || !Objects.equals(row.getRecordUserId(), row.getBenefitUserId())
                || !Objects.equals(row.getRecordPrizeId(), row.getBenefitPrizeId())
                || row.getRecordPrizeType() != row.getBenefitPrizeType()) {
            throw mismatch(RewardQuarantineReason.BENEFIT_IDENTITY_MISMATCH);
        }

        RewardSnapshot snapshot = validateRewardIdentity(payload, row);
        return new ValidatedLotteryReward(row.getBenefitId(), row.getDrawRecordId(), row.getOrderId(),
                row.getRequestId(), row.getBenefitUserId(), row.getOrderActivityId(),
                row.getBenefitPrizeId(), row.getBenefitPrizeType(), row.getBenefitStatus(), snapshot);
    }

    private RewardSnapshot validateRewardIdentity(PrizeFulfillmentRequestedEvent payload,
                                                   LotteryRewardIdentityRow row) {
        if (payload.rewardDefinitionId() == null) {
            if (row.getRecordRewardDefinitionId() != null || row.getBenefitRewardDefinitionId() != null
                    || row.getRecordRewardType() != null || row.getBenefitRewardType() != null
                    || row.getRecordRewardFingerprint() != null || row.getBenefitRewardFingerprint() != null) {
                throw mismatch(RewardQuarantineReason.REWARD_DEFINITION_MISMATCH);
            }
            return null;
        }
        if (!Objects.equals(payload.rewardDefinitionId(), row.getRecordRewardDefinitionId())
                || !Objects.equals(row.getRecordRewardDefinitionId(), row.getBenefitRewardDefinitionId())) {
            throw mismatch(RewardQuarantineReason.REWARD_DEFINITION_MISMATCH);
        }
        if (payload.rewardType() != row.getRecordRewardType()
                || row.getRecordRewardType() != row.getBenefitRewardType()) {
            throw mismatch(RewardQuarantineReason.REWARD_TYPE_MISMATCH);
        }
        if (!Objects.equals(payload.rewardFingerprint(), row.getRecordRewardFingerprint())
                || !Objects.equals(row.getRecordRewardFingerprint(), row.getBenefitRewardFingerprint())) {
            throw mismatch(RewardQuarantineReason.REWARD_FINGERPRINT_MISMATCH);
        }
        if (!Objects.equals(row.getRecordRewardTargetId(), row.getBenefitRewardTargetId())
                || !Objects.equals(row.getRecordRewardQuantity(), row.getBenefitRewardQuantity())
                || !jsonEquals(row.getRecordRewardPayload(), row.getBenefitRewardPayload())) {
            throw mismatch(RewardQuarantineReason.BENEFIT_IDENTITY_MISMATCH);
        }
        if (row.getBenefitRewardQuantity() == null || row.getBenefitRewardPayload() == null) {
            throw mismatch(RewardQuarantineReason.BENEFIT_IDENTITY_MISMATCH);
        }
        return new RewardSnapshot(row.getBenefitRewardDefinitionId(), "FROZEN-" + row.getBenefitRewardDefinitionId(),
                row.getBenefitRewardType(), row.getBenefitRewardTargetId(), row.getBenefitRewardQuantity(),
                row.getBenefitRewardPayload(), row.getBenefitRewardFingerprint());
    }

    private boolean jsonEquals(String left, String right) {
        return Objects.equals(left, right);
    }

    private RewardIdentityMismatchException mismatch(RewardQuarantineReason reason) {
        return new RewardIdentityMismatchException(reason);
    }
}
