package com.dongqh.luckyhub.benefit.handler;

import com.dongqh.luckyhub.benefit.entity.UserBenefit;
import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.prize.enums.PrizeType;

public interface BenefitFulfillmentHandler {

    PrizeType prizeType();

    BenefitStatus fulfill(UserBenefit benefit, String eventId);
}
