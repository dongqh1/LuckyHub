package com.dongqh.luckyhub.activity.scheduler;

import com.dongqh.luckyhub.activity.service.ActivityStatusService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ActivityStatusScheduler {

    private final ActivityStatusService service;

    public ActivityStatusScheduler(ActivityStatusService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${luckyhub.activity.status-refresh-interval:30000}",
            initialDelayString = "${luckyhub.activity.status-refresh-initial-delay:0}"
    )
    public void refreshActivityStatuses() {
        service.refreshStatuses();
    }
}
