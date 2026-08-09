package com.dongqh.luckyhub.benefit.handler;

import com.dongqh.luckyhub.benefit.entity.UserBenefit;
import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import org.springframework.stereotype.Component;

@Component
public class DrawChanceFulfillmentHandler implements BenefitFulfillmentHandler {
    @Override public PrizeType prizeType() { return PrizeType.DRAW_CHANCE; }
    @Override public BenefitStatus fulfill(UserBenefit benefit, String eventId) {
        return BenefitStatus.AVAILABLE;
    }
}
