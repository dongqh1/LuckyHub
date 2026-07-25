package com.dongqh.luckyhub.auth.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class PasswordService {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final PasswordEncoder passwordEncoder;

    public PasswordService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public String hash(String rawPassword) {
        validateForHashing(rawPassword);
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        if (!isValidRawPassword(rawPassword) || encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        try {
            return passwordEncoder.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public boolean needsUpgrade(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        try {
            return passwordEncoder.upgradeEncoding(encodedPassword);
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }

    private void validateForHashing(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (utf8Length(rawPassword) > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("密码不能超过72个UTF-8字节");
        }
    }

    private boolean isValidRawPassword(String rawPassword) {
        return rawPassword != null
                && !rawPassword.isBlank()
                && utf8Length(rawPassword) <= BCRYPT_MAX_PASSWORD_BYTES;
    }

    private int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
