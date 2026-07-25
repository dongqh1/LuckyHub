package com.dongqh.luckyhub.auth.model;

public record LoginPrincipal(
        Long userId,
        String username,
        String sessionId
) {
}
