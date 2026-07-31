package com.dongqh.luckyhub.lottery.algorithm;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SecureDrawRandomSource implements DrawRandomSource {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public long nextLong(long bound) {
        return secureRandom.nextLong(bound);
    }
}
