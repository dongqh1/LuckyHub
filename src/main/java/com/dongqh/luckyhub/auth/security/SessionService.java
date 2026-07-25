package com.dongqh.luckyhub.auth.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class SessionService {

    private static final String KEY_PREFIX =
            "luckyhub:auth:session:";

    @Autowired
    private StringRedisTemplate redisTemplate;


    public void create(
            String sessionId,
            Long userId,
            long expireSeconds
    ) {
        redisTemplate.opsForValue().set(
                buildKey(sessionId),
                userId.toString(),
                Duration.ofSeconds(expireSeconds)
        );
    }

    public boolean isValid(
            String sessionId,
            Long userId
    ) {
        String storedUserId = redisTemplate.opsForValue()
                .get(buildKey(sessionId));

        return userId.toString().equals(storedUserId);
    }

    public void remove(String sessionId) {
        redisTemplate.delete(buildKey(sessionId));
    }

    private String buildKey(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
