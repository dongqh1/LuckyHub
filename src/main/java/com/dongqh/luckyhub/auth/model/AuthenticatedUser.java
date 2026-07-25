package com.dongqh.luckyhub.auth.model;

public record AuthenticatedUser(
        Long userId,
        String username,
        String nickname
) {
}
