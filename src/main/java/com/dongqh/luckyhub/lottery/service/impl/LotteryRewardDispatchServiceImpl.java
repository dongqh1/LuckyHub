package com.dongqh.luckyhub.lottery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.luckyhub.benefit.enums.BenefitErrorCode;
import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.benefit.mapper.UserBenefitMapper;
import com.dongqh.luckyhub.benefit.service.BenefitFulfillmentService;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.drawchance.service.DrawChanceService;
import com.dongqh.luckyhub.fulfillment.dto.CreateFulfillmentTaskCommand;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType;
import com.dongqh.luckyhub.fulfillment.model.CouponFulfillmentPayload;
import com.dongqh.luckyhub.fulfillment.model.MembershipFulfillmentPayload;
import com.dongqh.luckyhub.fulfillment.model.PointsFulfillmentPayload;
import com.dongqh.luckyhub.fulfillment.service.FulfillmentTaskService;
import com.dongqh.luckyhub.lottery.config.MessagingProperties;
import com.dongqh.luckyhub.lottery.entity.LotteryRewardQuarantine;
import com.dongqh.luckyhub.lottery.entity.MessageConsumeRecord;
import com.dongqh.luckyhub.lottery.enums.RewardQuarantineStatus;
import com.dongqh.luckyhub.lottery.mapper.LotteryRewardQuarantineMapper;
import com.dongqh.luckyhub.lottery.mapper.MessageConsumeRecordMapper;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.luckyhub.lottery.model.ValidatedLotteryReward;
import com.dongqh.luckyhub.lottery.service.LotteryRewardDispatchService;
import com.dongqh.luckyhub.lottery.service.LotteryRewardIdentityService;
import com.dongqh.luckyhub.lottery.service.RewardIdentityMismatchException;
import com.dongqh.luckyhub.reward.model.CouponRewardPayload;
import com.dongqh.luckyhub.reward.model.MembershipRewardPayload;
import com.dongqh.luckyhub.reward.model.PointsRewardPayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

@Service
public class LotteryRewardDispatchServiceImpl implements LotteryRewardDispatchService {
    private final LotteryRewardIdentityService identities;
    private final BenefitFulfillmentService legacyFulfillment;
    private final FulfillmentTaskService tasks;
    private final DrawChanceService drawChances;
    private final UserBenefitMapper benefits;
    private final LotteryRewardQuarantineMapper quarantines;
    private final MessageConsumeRecordMapper consumeRecords;
    private final ObjectMapper json;
    private final String consumerName;

    public LotteryRewardDispatchServiceImpl(LotteryRewardIdentityService identities,
            BenefitFulfillmentService legacyFulfillment, FulfillmentTaskService tasks,
            DrawChanceService drawChances, UserBenefitMapper benefits,
            LotteryRewardQuarantineMapper quarantines, MessageConsumeRecordMapper consumeRecords,
            ObjectMapper json, MessagingProperties properties) {
        this.identities = identities; this.legacyFulfillment = legacyFulfillment;
        this.tasks = tasks; this.drawChances = drawChances; this.benefits = benefits;
        this.quarantines = quarantines; this.consumeRecords = consumeRecords;
        this.json = json; this.consumerName = properties.logicalConsumerName();
    }

    @Override
    @Transactional
    public void dispatch(DrawEventEnvelope envelope, PrizeFulfillmentRequestedEvent payload) {
        String eventId = envelope.eventId().toString();
        if (alreadyConsumed(eventId)) return;
        ValidatedLotteryReward reward;
        try {
            reward = identities.validate(envelope, payload);
        } catch (RewardIdentityMismatchException mismatch) {
            quarantine(envelope, payload, mismatch);
            recordConsumed(eventId);
            return;
        }
        if (reward.rewardSnapshot() == null) {
            legacyFulfillment.fulfill(reward.benefitId(), eventId);
            return;
        }
        if (reward.benefitStatus() != BenefitStatus.PENDING
                && reward.benefitStatus() != BenefitStatus.GRANT_FAILED) {
            throw new BusinessException(BenefitErrorCode.BENEFIT_STATE_CONFLICT);
        }

        String fulfillmentNo = "LOTTERY-BENEFIT-" + reward.benefitId();
        switch (reward.rewardSnapshot().rewardType()) {
            case COUPON -> {
                CouponRewardPayload frozen = couponPayload(reward);
                createTask(reward, fulfillmentNo, FulfillmentType.COUPON,
                        new CouponFulfillmentPayload(frozen.templateCode(), frozen.quantity()));
            }
            case POINTS -> {
                PointsRewardPayload frozen = pointsPayload(reward);
                createTask(reward, fulfillmentNo, FulfillmentType.POINTS,
                        new PointsFulfillmentPayload(frozen.points(), frozen.reason()));
            }
            case MEMBERSHIP -> {
                MembershipRewardPayload frozen = membershipPayload(reward);
                createTask(reward, fulfillmentNo, FulfillmentType.MEMBERSHIP,
                        new MembershipFulfillmentPayload(frozen.productCode(), frozen.durationDays()));
            }
            case PRODUCT -> transition(reward, BenefitStatus.CLAIM_PENDING);
            case DRAW_CHANCE -> {
                drawChances.credit(reward.userId(), fulfillmentNo, reward.rewardSnapshot().quantity());
                transition(reward, BenefitStatus.AVAILABLE);
            }
        }
        recordConsumed(eventId);
    }

    private void createTask(ValidatedLotteryReward reward, String fulfillmentNo,
                            FulfillmentType type,
                            com.dongqh.luckyhub.fulfillment.model.FulfillmentPayload payload) {
        tasks.create(new CreateFulfillmentTaskCommand(fulfillmentNo, "LOTTERY_BENEFIT",
                Long.toString(reward.benefitId()), type, reward.userId(), payload, null));
        benefits.bindFulfillmentNo(reward.benefitId(), fulfillmentNo);
    }

    private CouponRewardPayload couponPayload(ValidatedLotteryReward reward) {
        return read(reward, CouponRewardPayload.class);
    }
    private PointsRewardPayload pointsPayload(ValidatedLotteryReward reward) {
        return read(reward, PointsRewardPayload.class);
    }
    private MembershipRewardPayload membershipPayload(ValidatedLotteryReward reward) {
        return read(reward, MembershipRewardPayload.class);
    }
    private <T> T read(ValidatedLotteryReward reward, Class<T> type) {
        try { return json.readValue(reward.rewardSnapshot().payloadJson(), type); }
        catch (JacksonException error) { throw new IllegalStateException("冻结奖励载荷无法解析", error); }
    }

    private void transition(ValidatedLotteryReward reward, BenefitStatus target) {
        if (benefits.transitionStatus(reward.benefitId(), reward.benefitStatus(), target) != 1) {
            throw new BusinessException(BenefitErrorCode.BENEFIT_STATE_CONFLICT);
        }
    }

    private void quarantine(DrawEventEnvelope envelope, PrizeFulfillmentRequestedEvent payload,
                            RewardIdentityMismatchException mismatch) {
        LotteryRewardQuarantine row = new LotteryRewardQuarantine();
        row.setEventId(envelope.eventId().toString()); row.setRequestId(envelope.requestId());
        row.setOrderId(envelope.orderId()); row.setDrawRecordId(payload.drawRecordId());
        row.setBenefitId(payload.benefitId()); row.setPrizeId(payload.prizeId());
        row.setRewardDefinitionId(payload.rewardDefinitionId()); row.setReasonCode(mismatch.reason().name());
        row.setStatus(RewardQuarantineStatus.OPEN); row.setQuarantinedAt(LocalDateTime.now());
        quarantines.insert(row);
    }

    private boolean alreadyConsumed(String eventId) {
        return consumeRecords.selectCount(new LambdaQueryWrapper<MessageConsumeRecord>()
                .eq(MessageConsumeRecord::getEventId, eventId)
                .eq(MessageConsumeRecord::getConsumerName, consumerName)) > 0;
    }

    private void recordConsumed(String eventId) {
        MessageConsumeRecord record = new MessageConsumeRecord();
        record.setEventId(eventId); record.setConsumerName(consumerName);
        record.setConsumedAt(LocalDateTime.now()); consumeRecords.insert(record);
    }
}
