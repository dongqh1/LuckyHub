package com.dongqh.luckyhub.shipping.service.impl;

import com.dongqh.luckyhub.activity.entity.MarketingActivityPrize;
import com.dongqh.luckyhub.activity.mapper.MarketingActivityPrizeMapper;
import com.dongqh.luckyhub.benefit.entity.UserBenefit;
import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.benefit.mapper.UserBenefitMapper;
import com.dongqh.luckyhub.inventory.service.ActivityPrizeInventoryService;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawRecord;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawRecordMapper;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.reward.enums.RewardType;
import com.dongqh.luckyhub.reward.model.ProductRewardPayload;
import com.dongqh.luckyhub.shipping.service.PhysicalClaimExpiryWorker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class PhysicalClaimExpiryWorkerImpl implements PhysicalClaimExpiryWorker {
    private final UserBenefitMapper benefits;
    private final LotteryDrawRecordMapper drawRecords;
    private final MarketingActivityPrizeMapper activityPrizes;
    private final ActivityPrizeInventoryService inventory;
    private final ObjectMapper json;

    public PhysicalClaimExpiryWorkerImpl(UserBenefitMapper benefits,
                                         LotteryDrawRecordMapper drawRecords,
                                         MarketingActivityPrizeMapper activityPrizes,
                                         ActivityPrizeInventoryService inventory,
                                         ObjectMapper json) {
        this.benefits = benefits;
        this.drawRecords = drawRecords;
        this.activityPrizes = activityPrizes;
        this.inventory = inventory;
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean expireOne(long benefitId, LocalDateTime now) {
        UserBenefit benefit = benefits.selectByIdForUpdate(benefitId);
        if (benefit == null || benefit.getStatus() != BenefitStatus.CLAIM_PENDING
                || benefit.getClaimDeadline() == null || benefit.getClaimDeadline().isAfter(now)) {
            return false;
        }
        LotteryDrawRecord draw = drawRecords.selectById(benefit.getDrawRecordId());
        ProductRewardPayload product = requireFrozenIdentity(benefit, draw);
        MarketingActivityPrize activityPrize = activityPrizes.lockByActivityAndPrize(
                draw.getActivityId(), draw.getPrizeId());
        if (activityPrize == null) throw new IllegalStateException("活动奖品关系不存在");
        inventory.returnExpiredClaim(activityPrize.getId(), product.skuId(), "CLAIM-EXPIRE-" + benefitId);
        if (benefits.markClaimExpired(benefitId, now) != 1) {
            throw new IllegalStateException("权益过期状态转换失败");
        }
        return true;
    }

    private ProductRewardPayload requireFrozenIdentity(UserBenefit benefit, LotteryDrawRecord draw) {
        if (draw == null || benefit.getPrizeType() != PrizeType.PHYSICAL
                || benefit.getRewardType() != RewardType.PRODUCT
                || !Objects.equals(draw.getUserId(), benefit.getUserId())
                || !Objects.equals(draw.getPrizeId(), benefit.getPrizeId())
                || draw.getPrizeType() != benefit.getPrizeType()
                || !Objects.equals(draw.getRewardDefinitionId(), benefit.getRewardDefinitionId())
                || draw.getRewardType() != benefit.getRewardType()
                || !Objects.equals(draw.getRewardTargetId(), benefit.getRewardTargetId())
                || !Objects.equals(draw.getRewardQuantity(), benefit.getRewardQuantity())
                || !Objects.equals(draw.getRewardPayload(), benefit.getRewardPayload())
                || !Objects.equals(draw.getRewardFingerprint(), benefit.getRewardFingerprint())) {
            throw new IllegalStateException("冻结奖励身份不一致");
        }
        try {
            ProductRewardPayload product = json.readValue(benefit.getRewardPayload(), ProductRewardPayload.class);
            if (!Objects.equals(benefit.getRewardTargetId(), product.skuId())
                    || benefit.getRewardQuantity() != product.quantity()
                    || !Objects.equals(benefit.getQuantity(), product.quantity())) {
                throw new IllegalStateException("冻结商品奖励不一致");
            }
            return product;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("冻结商品奖励不可解析", exception);
        }
    }
}
