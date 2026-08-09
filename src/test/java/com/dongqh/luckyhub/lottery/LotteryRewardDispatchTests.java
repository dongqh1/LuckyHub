package com.dongqh.luckyhub.lottery;

import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.benefit.mapper.UserBenefitMapper;
import com.dongqh.luckyhub.benefit.service.BenefitFulfillmentService;
import com.dongqh.luckyhub.drawchance.service.DrawChanceService;
import com.dongqh.luckyhub.fulfillment.dto.CreateFulfillmentTaskCommand;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType;
import com.dongqh.luckyhub.fulfillment.service.FulfillmentTaskService;
import com.dongqh.luckyhub.lottery.config.MessagingProperties;
import com.dongqh.luckyhub.lottery.enums.RewardQuarantineReason;
import com.dongqh.luckyhub.lottery.entity.LotteryRewardQuarantine;
import com.dongqh.luckyhub.lottery.entity.MessageConsumeRecord;
import com.dongqh.luckyhub.lottery.mapper.LotteryRewardQuarantineMapper;
import com.dongqh.luckyhub.lottery.mapper.MessageConsumeRecordMapper;
import com.dongqh.luckyhub.lottery.messaging.event.*;
import com.dongqh.luckyhub.lottery.model.ValidatedLotteryReward;
import com.dongqh.luckyhub.lottery.service.LotteryRewardIdentityService;
import com.dongqh.luckyhub.lottery.service.RewardIdentityMismatchException;
import com.dongqh.luckyhub.lottery.service.impl.LotteryRewardDispatchServiceImpl;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.reward.enums.RewardType;
import com.dongqh.luckyhub.reward.model.RewardSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LotteryRewardDispatchTests {
    LotteryRewardIdentityService identities = mock(LotteryRewardIdentityService.class);
    BenefitFulfillmentService legacy = mock(BenefitFulfillmentService.class);
    FulfillmentTaskService tasks = mock(FulfillmentTaskService.class);
    DrawChanceService chances = mock(DrawChanceService.class);
    UserBenefitMapper benefits = mock(UserBenefitMapper.class);
    LotteryRewardQuarantineMapper quarantines = mock(LotteryRewardQuarantineMapper.class);
    MessageConsumeRecordMapper consumes = mock(MessageConsumeRecordMapper.class);
    ObjectMapper json = JsonMapper.builder().findAndAddModules().build();
    LotteryRewardDispatchServiceImpl service = new LotteryRewardDispatchServiceImpl(identities, legacy, tasks, chances,
            benefits, quarantines, consumes, json, properties());

    @Test
    void couponSnapshotCreatesTypedTaskAndBindsDeterministicNumber() {
        RewardSnapshot snapshot = new RewardSnapshot(8L, "C", RewardType.COUPON, 9L, 2,
                "{\"templateId\":9,\"templateCode\":\"WELCOME\",\"quantity\":2}", "a".repeat(64));
        var validated = new ValidatedLotteryReward(1, 2, 3, "r", 4, 5, 6,
                PrizeType.COUPON, BenefitStatus.PENDING, snapshot);
        DrawEventEnvelope envelope = envelope();
        PrizeFulfillmentRequestedEvent payload = payload();
        when(identities.validate(envelope, payload)).thenReturn(validated);

        service.dispatch(envelope, payload);

        ArgumentCaptor<CreateFulfillmentTaskCommand> captor = ArgumentCaptor.forClass(CreateFulfillmentTaskCommand.class);
        verify(tasks).create(captor.capture());
        assertThat(captor.getValue().fulfillmentNo()).isEqualTo("LOTTERY-BENEFIT-1");
        assertThat(captor.getValue().fulfillmentType()).isEqualTo(FulfillmentType.COUPON);
        assertThat(captor.getValue().payload()).extracting("couponTemplateCode", "quantity")
                .containsExactly("WELCOME", 2);
        verify(benefits).bindFulfillmentNo(1, "LOTTERY-BENEFIT-1");
        verify(consumes).insert(any(MessageConsumeRecord.class));
    }

    @Test
    void forgedIdentityIsConsumedAndQuarantinedWithZeroRewardEffects() {
        DrawEventEnvelope envelope = envelope();
        PrizeFulfillmentRequestedEvent payload = payload();
        when(identities.validate(envelope, payload)).thenThrow(
                new RewardIdentityMismatchException(RewardQuarantineReason.ORDER_IDENTITY_MISMATCH));

        service.dispatch(envelope, payload);

        verify(quarantines).insert(argThat((LotteryRewardQuarantine row) -> row.getReasonCode().equals("ORDER_IDENTITY_MISMATCH")));
        verify(consumes).insert(any(MessageConsumeRecord.class));
        verifyNoInteractions(tasks, chances, legacy);
        verify(benefits, never()).transitionStatus(anyLong(), any(), any());
    }

    private DrawEventEnvelope envelope() {
        return new DrawEventEnvelope(UUID.randomUUID(), DrawEventType.PRIZE_FULFILLMENT_REQUESTED, 1,
                "r", 4L, 5L, 3L, LocalDateTime.now(), json.valueToTree(payload()));
    }
    private PrizeFulfillmentRequestedEvent payload() {
        return new PrizeFulfillmentRequestedEvent(1L, 2L, 6L, PrizeType.COUPON,
                8L, RewardType.COUPON, "a".repeat(64));
    }
    private MessagingProperties properties() {
        return new MessagingProperties(false, "redis", "s", "g", "test-consumer", 1,
                Duration.ofMillis(10), Duration.ofMillis(20), Duration.ofSeconds(1));
    }
}
