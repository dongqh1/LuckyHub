package com.dongqh.luckyhub.shipping.service.impl;

import com.dongqh.luckyhub.benefit.mapper.UserBenefitMapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
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
    private long scanAfterId;

    public PhysicalClaimExpiryServiceImpl(UserBenefitMapper benefits,
                                          PhysicalClaimExpiryWorker worker) {
        this.benefits = benefits;
        this.worker = worker;
    }

    @Override
    public synchronized int expireDue(int limit, LocalDateTime now) {
        if (limit < 1 || limit > 1000 || now == null) throw new IllegalArgumentException("过期批次参数不合法");
        int expired = 0;
        int candidates = 0;
        int candidateBudget = candidateBudget(limit);
        while (expired < limit && candidates < candidateBudget) {
            int queryLimit = Math.min(SCAN_PAGE_SIZE, candidateBudget - candidates);
            List<Long> ids = benefits.selectDueClaimIdsAfter(now, scanAfterId, queryLimit);
            if (ids.isEmpty()) {
                scanAfterId = 0;
                break;
            }
            int processedThisPage = 0;
            for (Long id : ids) {
                scanAfterId = id;
                candidates++;
                processedThisPage++;
                try {
                    if (worker.expireOne(id, now)) expired++;
                } catch (RuntimeException exception) {
                    logCandidateFailure(id, exception);
                }
                if (expired == limit || candidates == candidateBudget) break;
            }
            if (processedThisPage == ids.size() && ids.size() < queryLimit) {
                scanAfterId = 0;
                break;
            }
        }
        return expired;
    }

    private int candidateBudget(int limit) {
        return (int) Math.min(1000L, Math.max(SCAN_PAGE_SIZE, (long) limit * 4));
    }

    private void logCandidateFailure(long benefitId, RuntimeException exception) {
        String errorType = exception.getClass().getSimpleName().replaceAll("[^A-Za-z0-9_$]", "_");
        if (errorType.isEmpty()) errorType = "RuntimeException";
        if (errorType.length() > 64) errorType = errorType.substring(0, 64);
        String errorCode = exception instanceof BusinessException businessException
                ? Integer.toString(businessException.getErrorCode().code())
                : "-";
        log.warn("Physical claim expiry candidate failed, benefitId={}, errorType={}, errorCode={}",
                benefitId, errorType, errorCode);
    }
}
