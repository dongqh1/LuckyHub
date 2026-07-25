package com.dongqh.luckyhub.auth.model;

public record JwtPayload( Long userId,
                          String username,
                          String sessionId) {
}
