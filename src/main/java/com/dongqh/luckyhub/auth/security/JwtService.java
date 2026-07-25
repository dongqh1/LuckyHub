package com.dongqh.luckyhub.auth.security;

import com.dongqh.luckyhub.auth.enums.AuthErrorCode;
import com.dongqh.luckyhub.auth.model.AuthenticatedUser;
import com.dongqh.luckyhub.auth.model.JwtPayload;
import com.dongqh.luckyhub.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expireSeconds;

    public JwtService(
            @Value("${luckyhub.security.jwt.secret}") String secret,
            @Value("${luckyhub.security.jwt.expiration}") long expireSeconds
    ) {
        this.secretKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(secret)
        );
        this.expireSeconds = expireSeconds;
    }

    public String generate(
            AuthenticatedUser user,
            String sessionId
    ) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(expireSeconds);

        return Jwts.builder()
                .subject(user.userId().toString())
                .id(sessionId)
                .claim("username", user.username())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
    }

    public JwtPayload parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = Long.valueOf(claims.getSubject());
            String username = claims.get(
                    "username",
                    String.class
            );
            String sessionId = claims.getId();

            if (username == null
                    || username.isBlank()
                    || sessionId == null
                    || sessionId.isBlank()) {
                throw invalidToken();
            }

            return new JwtPayload(
                    userId,
                    username,
                    sessionId
            );
        } catch (JwtException
                 | IllegalArgumentException exception) {
            throw invalidToken();
        }
    }

    public String newSessionId() {
        return UUID.randomUUID().toString();
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    private BusinessException invalidToken() {
        return new BusinessException(
                AuthErrorCode.INVALID_TOKEN
        );
    }
}
