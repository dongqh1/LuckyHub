package com.dongqh.luckyhub.benefit.handler;

import com.dongqh.luckyhub.benefit.entity.UserBenefit;
import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import org.springframework.stereotype.Component;

@Component
public class CouponFulfillmentHandler implements BenefitFulfillmentHandler {
    @Override public PrizeType prizeType() { return PrizeType.COUPON; }
    @Override public BenefitStatus fulfill(UserBenefit benefit, String eventId) {
        return BenefitStatus.AVAILABLE;
    }
}
