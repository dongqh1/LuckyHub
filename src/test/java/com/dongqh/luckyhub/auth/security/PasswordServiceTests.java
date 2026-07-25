package com.dongqh.luckyhub.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@SpringBootTest
class PasswordServiceTests {

    private static final String RAW_PASSWORD = "LuckyHub@2026";

    @Autowired
    private PasswordService passwordService;

    @Test
    void usesRandomSaltForEveryHash() {
        String firstHash = passwordService.hash(RAW_PASSWORD);
        String secondHash = passwordService.hash(RAW_PASSWORD);

        assertThat(firstHash).isNotEqualTo(secondHash);
        assertThat(firstHash).doesNotContain(RAW_PASSWORD);
        assertThat(secondHash).doesNotContain(RAW_PASSWORD);
    }

    @Test
    void matchesOnlyTheCorrectPassword() {
        String encodedPassword = passwordService.hash(RAW_PASSWORD);

        assertThat(passwordService.matches(RAW_PASSWORD, encodedPassword)).isTrue();
        assertThat(passwordService.matches("WrongPassword", encodedPassword)).isFalse();
        assertThat(passwordService.matches(null, encodedPassword)).isFalse();
        assertThat(passwordService.matches(RAW_PASSWORD, null)).isFalse();
    }

    @Test
    void usesConfiguredStrengthTwelve() {
        String encodedPassword = passwordService.hash(RAW_PASSWORD);

        assertThat(encodedPassword).startsWith("$2a$12$");
    }

    @Test
    void rejectsBlankPassword() {
        assertThatIllegalArgumentException().isThrownBy(() -> passwordService.hash(null));
        assertThatIllegalArgumentException().isThrownBy(() -> passwordService.hash("   "));
    }

    @Test
    void enforcesBcryptUtf8ByteLimit() {
        String exactly72Bytes = "a".repeat(72);
        String moreThan72Bytes = "a".repeat(73);

        assertThat(passwordService.hash(exactly72Bytes)).isNotBlank();
        assertThatIllegalArgumentException().isThrownBy(() -> passwordService.hash(moreThan72Bytes));
    }

    @Test
    void detectsHashesThatNeedAWorkFactorUpgrade() {
        String oldStrengthHash = new BCryptPasswordEncoder(10).encode(RAW_PASSWORD);
        String currentStrengthHash = passwordService.hash(RAW_PASSWORD);

        assertThat(passwordService.needsUpgrade(oldStrengthHash)).isTrue();
        assertThat(passwordService.needsUpgrade(currentStrengthHash)).isFalse();
    }
}
