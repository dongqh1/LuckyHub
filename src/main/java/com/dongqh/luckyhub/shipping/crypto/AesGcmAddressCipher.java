package com.dongqh.luckyhub.shipping.crypto;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.shipping.config.ShippingProperties;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

public final class AesGcmAddressCipher implements AddressCipher {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final SecretKeySpec key;
    private final String keyVersion;
    private final byte[] aad;
    private final SecureRandom secureRandom;

    public AesGcmAddressCipher(ShippingProperties properties) {
        this.key = new SecretKeySpec(Base64.getDecoder().decode(properties.addressKey()), "AES");
        this.keyVersion = properties.addressKeyVersion();
        this.aad = keyVersion.getBytes(StandardCharsets.UTF_8);
        this.secureRandom = new SecureRandom();
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            throw invalid();
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return keyVersion + "." + ENCODER.encodeToString(nonce) + "." + ENCODER.encodeToString(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw invalid();
        }
    }

    @Override
    public String decrypt(String envelope) {
        if (envelope == null || envelope.isEmpty()) {
            throw invalid();
        }
        try {
            String[] parts = envelope.split("\\.", -1);
            if (parts.length != 3 || !keyVersion.equals(parts[0])
                    || parts[1].isEmpty() || parts[2].isEmpty()
                    || parts[1].contains("=") || parts[2].contains("=")) {
                throw invalid();
            }
            byte[] nonce = DECODER.decode(parts[1]);
            byte[] ciphertext = DECODER.decode(parts[2]);
            if (nonce.length != NONCE_BYTES || ciphertext.length < TAG_BITS / 8) {
                throw invalid();
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (BusinessException exception) {
            throw exception;
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private BusinessException invalid() {
        return new BusinessException(ShippingErrorCode.ADDRESS_INVALID);
    }
}
