package com.dongqh.luckyhub.shipping.service.impl;

import com.dongqh.luckyhub.benefit.mapper.UserBenefitMapper;
import com.dongqh.luckyhub.shipping.service.PhysicalClaimExpiryService;
import com.dongqh.luckyhub.shipping.service.PhysicalClaimExpiryWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PhysicalClaimExpiryServiceImpl implements PhysicalClaimExpiryService {
    private static final Logger log = LoggerFactory.getLogger(PhysicalClaimExpiryServiceImpl.class);
    private static final int SCAN_PAGE_SIZE = 50;
    private final UserBenefitMapper benefits;
    private final PhysicalClaimExpiryWorker worker;

    public PhysicalClaimExpiryServiceImpl(UserBenefitMapper benefits,
                                          PhysicalClaimExpiryWorker worker) {
        this.benefits = benefits;
        this.worker = worker;
    }

    @Override
    public int expireDue(int limit, LocalDateTime now) {
        if (limit < 1 || limit > 1000 || now == null) throw new IllegalArgumentException("过期批次参数不合法");
        int expired = 0;
        long afterId = 0;
        while (expired < limit) {
            List<Long> ids = benefits.selectDueClaimIdsAfter(now, afterId, SCAN_PAGE_SIZE);
            if (ids.isEmpty()) break;
            for (Long id : ids) {
                afterId = id;
                try {
                    if (worker.expireOne(id, now)) expired++;
                } catch (RuntimeException exception) {
                    log.warn("Physical claim expiry candidate failed, benefitId={}", id, exception);
                }
                if (expired == limit) break;
            }
            if (ids.size() < SCAN_PAGE_SIZE) break;
        }
        return expired;
    }
}
