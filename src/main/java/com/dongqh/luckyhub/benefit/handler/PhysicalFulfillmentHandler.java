package com.dongqh.luckyhub.benefit.handler;

import com.dongqh.luckyhub.benefit.entity.UserBenefit;
import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import org.springframework.stereotype.Component;

@Component
public class PhysicalFulfillmentHandler implements BenefitFulfillmentHandler {
    @Override public PrizeType prizeType() { return PrizeType.PHYSICAL; }
    @Override public BenefitStatus fulfill(UserBenefit benefit, String eventId) {
        return BenefitStatus.CLAIM_PENDING;
    }
}
