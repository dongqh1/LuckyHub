package com.dongqh.luckyhub.shipping.service;

import com.dongqh.luckyhub.shipping.dto.ClaimPhysicalBenefitCommand;
import com.dongqh.luckyhub.shipping.vo.ShippingOrderView;

public interface PhysicalClaimService {
    ShippingOrderView claim(long userId, long benefitId, ClaimPhysicalBenefitCommand command);
}
