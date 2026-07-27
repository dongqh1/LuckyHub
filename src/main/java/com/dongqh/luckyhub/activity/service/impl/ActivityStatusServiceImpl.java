package com.dongqh.luckyhub.activity.service.impl;

import com.dongqh.luckyhub.activity.mapper.MarketingActivityMapper;
import com.dongqh.luckyhub.activity.service.ActivityStatusRefreshResult;
import com.dongqh.luckyhub.activity.service.ActivityStatusService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityStatusServiceImpl implements ActivityStatusService {

    private final MarketingActivityMapper mapper;

    public ActivityStatusServiceImpl(MarketingActivityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ActivityStatusRefreshResult refreshStatuses() {
        int runningCount = mapper.promoteScheduledToRunning();
        int endedCount = mapper.finishExpiredActivities();
        return new ActivityStatusRefreshResult(runningCount, endedCount);
    }
}
