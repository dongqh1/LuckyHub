package com.dongqh.luckyhub.shipping.scheduler;

import com.dongqh.luckyhub.shipping.config.ShippingProperties;
import com.dongqh.luckyhub.shipping.service.PhysicalClaimExpiryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class PhysicalClaimExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(PhysicalClaimExpiryScheduler.class);
    private final PhysicalClaimExpiryService expiry;
    private final ShippingProperties properties;

    public PhysicalClaimExpiryScheduler(PhysicalClaimExpiryService expiry, ShippingProperties properties) {
        this.expiry = expiry;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${luckyhub.shipping.expiry-interval}",
            initialDelayString = "${luckyhub.shipping.expiry-initial-delay}")
    public void expireDue() {
        int count = expiry.expireDue(properties.batchSize(), LocalDateTime.now());
        if (count > 0) log.info("抽奖实物领取超时处理完成 count={}", count);
    }
}
