package com.dongqh.luckyhub.shipping.service;

import java.time.LocalDateTime;

public interface PhysicalClaimExpiryWorker {
    boolean expireOne(long benefitId, LocalDateTime now);
}
