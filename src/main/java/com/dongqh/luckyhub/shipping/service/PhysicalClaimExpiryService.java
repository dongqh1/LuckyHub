package com.dongqh.luckyhub.shipping.service;

import java.time.LocalDateTime;

public interface PhysicalClaimExpiryService {
    int expireDue(int limit, LocalDateTime now);
}
