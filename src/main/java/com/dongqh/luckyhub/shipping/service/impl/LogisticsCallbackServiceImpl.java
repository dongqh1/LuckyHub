package com.dongqh.luckyhub.shipping.service.impl;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.shipping.config.ShippingProperties;
import com.dongqh.luckyhub.shipping.crypto.LogisticsCallbackSigner;
import com.dongqh.luckyhub.shipping.dto.LogisticsCallbackCommand;
import com.dongqh.luckyhub.shipping.entity.ShippingCallbackReceipt;
import com.dongqh.luckyhub.shipping.entity.ShippingOrder;
import com.dongqh.luckyhub.shipping.entity.ShippingTrackingEvent;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.enums.ShippingStatus;
import com.dongqh.luckyhub.shipping.enums.TrackingEventType;
import com.dongqh.luckyhub.shipping.mapper.ShippingCallbackReceiptMapper;
import com.dongqh.luckyhub.shipping.mapper.ShippingOrderMapper;
import com.dongqh.luckyhub.shipping.mapper.ShippingTrackingEventMapper;
import com.dongqh.luckyhub.shipping.service.LogisticsCallbackService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class LogisticsCallbackServiceImpl implements LogisticsCallbackService {
    private static final DateTimeFormatter EVENT_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Pattern SIGNED_IDENTIFIER =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}");

    private final LogisticsCallbackSigner signer;
    private final ShippingProperties properties;
    private final ShippingCallbackReceiptMapper receipts;
    private final ShippingOrderMapper orders;
    private final ShippingTrackingEventMapper events;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public LogisticsCallbackServiceImpl(
            LogisticsCallbackSigner signer,
            ShippingProperties properties,
            ShippingCallbackReceiptMapper receipts,
            ShippingOrderMapper orders,
            ShippingTrackingEventMapper events,
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager
    ) {
        this.signer = signer;
        this.properties = properties;
        this.receipts = receipts;
        this.orders = orders;
        this.events = events;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void handle(LogisticsCallbackCommand command) {
        validate(command);
        if (!withinWindow(command.timestampEpochSecond()) || !signer.verify(command)) {
            throw error(ShippingErrorCode.CALLBACK_SIGNATURE_INVALID);
        }
        ShippingErrorCode failure = transactions.execute(status -> process(command));
        if (failure != null) throw error(failure);
    }

    private ShippingErrorCode process(LogisticsCallbackCommand command) {
        LocalDateTime receivedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        String nonceDigest = digest(command.nonce());
        String signatureDigest = digest(command.signature());
        ShippingCallbackReceipt receipt = receipt(command, nonceDigest, signatureDigest, receivedAt);
        try {
            receipts.insert(receipt);
        } catch (DuplicateKeyException duplicate) {
            ShippingCallbackReceipt existing = receipts.selectByCallbackId(command.callbackId());
            if (existing != null && sameReceipt(existing, command, nonceDigest, signatureDigest)) {
                return "REJECTED".equals(existing.getStatus())
                        ? rejectedCode(existing.getErrorCode()) : null;
            }
            return ShippingErrorCode.CALLBACK_REPLAYED;
        }

        ShippingOrder order = orders.lockByWaybillNo(command.waybillNo());
        if (order == null) {
            reject(receipt, ShippingErrorCode.SHIPPING_NOT_FOUND, receivedAt);
            return ShippingErrorCode.SHIPPING_NOT_FOUND;
        }
        if (!callbackAllowed(order.getStatus())) {
            reject(receipt, ShippingErrorCode.SHIPPING_STATE_CONFLICT, receivedAt);
            return ShippingErrorCode.SHIPPING_STATE_CONFLICT;
        }

        insertEvent(order, command, receivedAt);
        advance(order, command.eventType(), receivedAt);
        receipt.setStatus("PROCESSED");
        receipt.setProcessedAt(receivedAt);
        receipts.updateById(receipt);
        return null;
    }

    private void insertEvent(ShippingOrder order, LogisticsCallbackCommand command, LocalDateTime receivedAt) {
        ShippingTrackingEvent event = new ShippingTrackingEvent();
        event.setShippingOrderId(order.getId());
        event.setProviderEventId(providerEventId(command));
        event.setWaybillNo(command.waybillNo());
        event.setEventType(command.eventType());
        event.setLocationSummary(normalizeOptional(command.locationSummary()));
        event.setDescription(normalizeOptional(command.description()));
        event.setEventTime(databaseTime(command.eventTime()));
        event.setReceivedAt(receivedAt);
        try {
            events.insert(event);
        } catch (DuplicateKeyException ignored) {
            // The provider event identity is deterministic; a second callback is idempotent.
        }
    }

    private void advance(ShippingOrder order, TrackingEventType eventType, LocalDateTime now) {
        ShippingStatus target = targetStatus(eventType);
        if (rank(target) <= rank(order.getStatus())) return;
        LocalDateTime deliveredAt = target == ShippingStatus.DELIVERED ? now : order.getDeliveredAt();
        int changed = jdbc.update("""
                UPDATE shipping_order
                SET status=?, delivered_at=?, version=version+1, updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=? AND version=? AND status=?
                """, target.name(), deliveredAt, order.getId(), order.getVersion(), order.getStatus().name());
        if (changed != 1) throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        if (target == ShippingStatus.DELIVERED
                && order.getSourceType() == ShippingSourceType.LOTTERY_BENEFIT) {
            jdbc.update("""
                    UPDATE user_benefit SET status='DELIVERED', updated_at=CURRENT_TIMESTAMP(3)
                    WHERE id=? AND status IN ('CLAIMED','FULFILLING','SHIPPED')
                    """, Long.valueOf(order.getSourceId()));
        }
    }

    private ShippingCallbackReceipt receipt(
            LogisticsCallbackCommand command, String nonceDigest,
            String signatureDigest, LocalDateTime receivedAt
    ) {
        ShippingCallbackReceipt receipt = new ShippingCallbackReceipt();
        receipt.setCallbackId(command.callbackId());
        receipt.setNonceDigest(nonceDigest);
        receipt.setSignatureDigest(signatureDigest);
        receipt.setWaybillNo(command.waybillNo());
        receipt.setEventType(command.eventType());
        receipt.setEventTime(databaseTime(command.eventTime()));
        receipt.setStatus("RECEIVED");
        receipt.setReceivedAt(receivedAt);
        return receipt;
    }

    private void reject(ShippingCallbackReceipt receipt, ShippingErrorCode code, LocalDateTime processedAt) {
        receipt.setStatus("REJECTED");
        receipt.setErrorCode(Integer.toString(code.code()));
        receipt.setErrorMessage(code.message());
        receipt.setProcessedAt(processedAt);
        receipts.updateById(receipt);
    }

    private boolean sameReceipt(
            ShippingCallbackReceipt existing, LogisticsCallbackCommand command,
            String nonceDigest, String signatureDigest
    ) {
        return Objects.equals(existing.getNonceDigest(), nonceDigest)
                && Objects.equals(existing.getSignatureDigest(), signatureDigest)
                && Objects.equals(existing.getWaybillNo(), command.waybillNo())
                && existing.getEventType() == command.eventType()
                && Objects.equals(existing.getEventTime(), databaseTime(command.eventTime()));
    }

    private boolean withinWindow(long timestamp) {
        long now = Instant.now().getEpochSecond();
        long window = properties.callbackWindow().getSeconds();
        return timestamp >= now - window && timestamp <= now + window;
    }

    private void validate(LogisticsCallbackCommand command) {
        if (command == null
                || invalidSignedIdentifier(command.callbackId())
                || invalidSignedIdentifier(command.nonce())
                || invalidSignedIdentifier(command.waybillNo())
                || command.eventType() == null
                || command.eventTime() == null
                || invalidOptional(command.locationSummary(), 200)
                || invalidOptional(command.description(), 500)
                || invalid(command.signature(), 100)) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
    }

    private boolean invalid(String value, int max) {
        return value == null || value.isBlank() || value.length() > max;
    }

    private boolean invalidSignedIdentifier(String value) {
        return value == null || !SIGNED_IDENTIFIER.matcher(value).matches();
    }

    private boolean invalidOptional(String value, int max) {
        return value != null && value.length() > max;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private boolean callbackAllowed(ShippingStatus status) {
        return status == ShippingStatus.SHIPPED
                || status == ShippingStatus.IN_TRANSIT
                || status == ShippingStatus.DELIVERED;
    }

    private ShippingStatus targetStatus(TrackingEventType type) {
        return switch (type) {
            case PICKED_UP -> ShippingStatus.SHIPPED;
            case IN_TRANSIT, OUT_FOR_DELIVERY -> ShippingStatus.IN_TRANSIT;
            case DELIVERED -> ShippingStatus.DELIVERED;
        };
    }

    private int rank(ShippingStatus status) {
        return switch (status) {
            case READY -> 0;
            case FULFILLING -> 1;
            case SHIPPED -> 2;
            case IN_TRANSIT -> 3;
            case DELIVERED -> 4;
            case FAILED, TERMINATED -> -1;
        };
    }

    private String providerEventId(LogisticsCallbackCommand command) {
        return digest(command.waybillNo() + '\n' + command.eventType().name() + '\n'
                + EVENT_TIME_FORMAT.format(command.eventTime()));
    }

    private ShippingErrorCode rejectedCode(String storedCode) {
        for (ShippingErrorCode candidate : ShippingErrorCode.values()) {
            if (Integer.toString(candidate.code()).equals(storedCode)) {
                return candidate;
            }
        }
        return ShippingErrorCode.CALLBACK_REPLAYED;
    }

    private LocalDateTime databaseTime(LocalDateTime value) {
        return value.truncatedTo(ChronoUnit.MILLIS);
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private BusinessException error(ShippingErrorCode code) {
        return new BusinessException(code);
    }
}
