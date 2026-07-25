package com.dongqh.luckyhub.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class PasswordValidator
        implements ConstraintValidator<ValidPassword, String> {

    private static final int MIN_BYTES = 8;
    private static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(
            String password,
            ConstraintValidatorContext context
    ) {
        if (password == null || password.isBlank()) {
            return false;
        }

        int byteLength =
                password.getBytes(StandardCharsets.UTF_8).length;

        return byteLength >= MIN_BYTES
                && byteLength <= MAX_BYTES;
    }
}
