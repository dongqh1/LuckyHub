package com.dongqh.luckyhub.lottery;

import com.dongqh.luckyhub.benefit.mapper.UserBenefitMapper;
import com.dongqh.luckyhub.lottery.enums.RewardQuarantineReason;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventType;
import com.dongqh.luckyhub.lottery.messaging.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.luckyhub.lottery.service.RewardIdentityMismatchException;
import com.dongqh.luckyhub.lottery.service.impl.LotteryRewardIdentityServiceImpl;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class LotteryRewardIdentityTests {
    @Test
    void missingLockedIdentityUsesBoundedQuarantineReason() {
        UserBenefitMapper benefits = mock(UserBenefitMapper.class);
        var service = new LotteryRewardIdentityServiceImpl(benefits);
        var payload = new PrizeFulfillmentRequestedEvent(1L, 2L, 3L, PrizeType.COUPON);
        var envelope = new DrawEventEnvelope(UUID.randomUUID(), DrawEventType.PRIZE_FULFILLMENT_REQUESTED,
                1, "request", 4L, 5L, 6L, LocalDateTime.now(),
                JsonMapper.builder().build().valueToTree(payload));

        assertThatThrownBy(() -> service.validate(envelope, payload))
                .isInstanceOfSatisfying(RewardIdentityMismatchException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.reason())
                                .isEqualTo(RewardQuarantineReason.IDENTITY_NOT_FOUND));
    }
}
