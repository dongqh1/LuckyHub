package com.dongqh.luckyhub.shipping.scheduler;

import com.dongqh.luckyhub.shipping.service.ShippingAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ShippingProjectionScheduler {
    private static final Logger log = LoggerFactory.getLogger(ShippingProjectionScheduler.class);
    private final ShippingAdminService service;
    public ShippingProjectionScheduler(ShippingAdminService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${luckyhub.fulfillment.poll-interval:5s}",
            initialDelayString = "${luckyhub.fulfillment.initial-delay:60s}")
    public void project() {
        int count = service.projectPending();
        if (count > 0) log.info("物流状态投影完成 count={}", count);
    }
}
