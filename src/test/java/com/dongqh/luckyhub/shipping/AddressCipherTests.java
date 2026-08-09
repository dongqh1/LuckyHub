package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.shipping.config.ShippingProperties;
import com.dongqh.luckyhub.shipping.crypto.AesGcmAddressCipher;
import com.dongqh.luckyhub.shipping.crypto.AddressCipher;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.support.AddressMasker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AddressCipherTests {

    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void usesVersionedUnpaddedEnvelopeRandomTwelveByteNonceAndRoundTrips() {
        AddressCipher cipher = new AesGcmAddressCipher(properties(KEY, "v1"));

        String first = cipher.encrypt("浙江省杭州市余杭区");
        String second = cipher.encrypt("浙江省杭州市余杭区");

        assertThat(first).isNotEqualTo(second);
        assertThat(first).matches("v1\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
        assertThat(first).doesNotContain("=");
        assertThat(Base64.getUrlDecoder().decode(first.split("\\.")[1])).hasSize(12);
        assertThat(Base64.getUrlDecoder().decode(first.split("\\.")[2]).length)
                .isEqualTo("浙江省杭州市余杭区".getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 16);
        assertThat(cipher.decrypt(first)).isEqualTo("浙江省杭州市余杭区");
        assertThat(cipher.decrypt(second)).isEqualTo("浙江省杭州市余杭区");
    }

    @Test
    void rejectsTamperingUnknownVersionMalformedAndEmptyInputWithoutEchoingSecrets() {
        AddressCipher cipher = new AesGcmAddressCipher(properties(KEY, "v1"));
        String envelope = cipher.encrypt("13812345678");
        String tampered = envelope.substring(0, envelope.length() - 1)
                + (envelope.endsWith("A") ? "B" : "A");

        assertSafeFailure(() -> cipher.decrypt(tampered), tampered, KEY, "13812345678");
        assertSafeFailure(() -> cipher.decrypt(envelope.replaceFirst("v1", "v2")), envelope, KEY);
        assertSafeFailure(() -> cipher.decrypt("v1.only-two"), "v1.only-two", KEY);
        assertSafeFailure(() -> cipher.decrypt(""), KEY);
        assertSafeFailure(() -> cipher.encrypt(""), KEY);
    }

    @Test
    void validatesExactAes256KeyVersionAndAllShippingBounds() {
        assertThatThrownBy(() -> properties(Base64.getEncoder().encodeToString(new byte[31]), "v1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(KEY, "bad.version"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShippingProperties(KEY, "v1", Duration.ZERO,
                "test-callback-secret-32-bytes-long", Duration.ofMinutes(5), Duration.ofMinutes(1),
                Duration.ZERO, 50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShippingProperties(KEY, "v1", Duration.ofDays(7),
                "short", Duration.ofMinutes(5), Duration.ofMinutes(1), Duration.ZERO, 50))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShippingProperties(KEY, "v1", Duration.ofDays(7),
                "test-callback-secret-32-bytes-long", Duration.ofMinutes(5), Duration.ofMinutes(1),
                Duration.ZERO, 1001)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void masksReceiverPhoneAndRegionWithoutPlaintextDetail() {
        assertThat(AddressMasker.maskReceiver("张三")).isEqualTo("张*");
        assertThat(AddressMasker.maskReceiver("张")).isEqualTo("*");
        assertThat(AddressMasker.maskPhone("13812345678")).isEqualTo("138****5678");
        assertThat(AddressMasker.maskRegion("浙江省", "杭州市", "余杭区"))
                .isEqualTo("浙江省杭州市余杭区***");
    }

    private ShippingProperties properties(String key, String version) {
        return new ShippingProperties(key, version, Duration.ofDays(7),
                "test-callback-secret-32-bytes-long", Duration.ofMinutes(5), Duration.ofMinutes(1),
                Duration.ofSeconds(60), 50);
    }

    private void assertSafeFailure(Runnable action, String... sensitiveValues) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(ShippingErrorCode.ADDRESS_INVALID);
            for (String sensitive : sensitiveValues) {
                assertThat(exception.getMessage()).doesNotContain(sensitive);
            }
        });
    }
}
