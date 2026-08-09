package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.event.PrizeFulfillmentRequestedEvent;

public interface LotteryRewardDispatchService {
    void dispatch(DrawEventEnvelope envelope, PrizeFulfillmentRequestedEvent payload);
}
