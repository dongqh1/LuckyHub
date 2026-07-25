package com.dongqh.luckyhub.auth.vo;

public record LoginView(
        Long userId,
        String username,
        String nickname,
        String token,
        String tokenType,
        Long expiresIn
) {
}
