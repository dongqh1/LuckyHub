package com.dongqh.luckyhub.shipping.crypto;

import com.dongqh.luckyhub.shipping.config.ShippingProperties;
import com.dongqh.luckyhub.shipping.dto.LogisticsCallbackCommand;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Component
public final class LogisticsCallbackSigner {
    private static final String ALGORITHM = "HmacSHA256";
    private static final DateTimeFormatter EVENT_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final byte[] secret;

    public LogisticsCallbackSigner(ShippingProperties properties) {
        this.secret = properties.callbackSecret().getBytes(StandardCharsets.UTF_8).clone();
    }

    public String canonical(LogisticsCallbackCommand command) {
        return command.callbackId() + '\n'
                + command.nonce() + '\n'
                + command.timestampEpochSecond() + '\n'
                + command.waybillNo() + '\n'
                + command.eventType().name() + '\n'
                + EVENT_TIME_FORMAT.format(command.eventTime());
    }

    public String sign(LogisticsCallbackCommand command) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac(canonical(command)));
    }

    public boolean verify(LogisticsCallbackCommand command) {
        byte[] supplied;
        try {
            supplied = Base64.getUrlDecoder().decode(command.signature());
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return supplied.length == 32
                && MessageDigest.isEqual(mac(canonical(command)), supplied);
    }

    private byte[] mac(String canonical) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256不可用", exception);
        }
    }
}
