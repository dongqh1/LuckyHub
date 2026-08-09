package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.luckyhub.lottery.model.ValidatedLotteryReward;

public interface LotteryRewardIdentityService {
    ValidatedLotteryReward validate(DrawEventEnvelope envelope,
                                    PrizeFulfillmentRequestedEvent payload);
}
