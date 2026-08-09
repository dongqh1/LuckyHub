package com.dongqh.luckyhub.shipping.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@ConfigurationProperties(prefix = "luckyhub.shipping")
public record ShippingProperties(
        String addressKey,
        String addressKeyVersion,
        Duration claimPeriod,
        String callbackSecret,
        Duration callbackWindow,
        Duration expiryInterval,
        Duration expiryInitialDelay,
        int batchSize
) {
    public ShippingProperties {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(addressKey == null ? "" : addressKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("shipping address key must be valid Base64");
        }
        if (key.length != 32) {
            throw new IllegalArgumentException("shipping address key must decode to 32 bytes");
        }
        if (addressKeyVersion == null || !addressKeyVersion.matches("[A-Za-z0-9_-]{1,16}")) {
            throw new IllegalArgumentException("shipping address key version is invalid");
        }
        requirePositive(claimPeriod, "claim period");
        requirePositive(callbackWindow, "callback window");
        requirePositive(expiryInterval, "expiry interval");
        if (expiryInitialDelay == null || expiryInitialDelay.isNegative()) {
            throw new IllegalArgumentException("shipping expiry initial delay must not be negative");
        }
        if (callbackSecret == null || callbackSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("shipping callback secret must contain at least 32 UTF-8 bytes");
        }
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("shipping batch size must be between 1 and 1000");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("shipping " + name + " must be positive");
        }
    }
}
