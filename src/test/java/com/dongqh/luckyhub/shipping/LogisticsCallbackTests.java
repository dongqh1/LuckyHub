package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.shipping.crypto.LogisticsCallbackSigner;
import com.dongqh.luckyhub.shipping.dto.LogisticsCallbackCommand;
import com.dongqh.luckyhub.shipping.dto.SimulateTrackingEventCommand;
import com.dongqh.luckyhub.shipping.entity.ShippingOrder;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.enums.ShippingStatus;
import com.dongqh.luckyhub.shipping.enums.TrackingEventType;
import com.dongqh.luckyhub.shipping.model.CreateShippingOrderCommand;
import com.dongqh.luckyhub.shipping.service.LogisticsCallbackService;
import com.dongqh.luckyhub.shipping.service.ShippingOrderService;
import com.dongqh.luckyhub.shipping.service.ShippingQueryService;
import com.dongqh.luckyhub.integration.simulator.SimulatorFailureRuleService;
import com.dongqh.luckyhub.integration.simulator.controller.SimulatorAdminController;
import com.dongqh.luckyhub.shipping.mapper.ShippingOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LogisticsCallbackTests extends Task5ShippingTestFixture {
    @Autowired LogisticsCallbackSigner signer;
    @Autowired LogisticsCallbackService callbacks;
    @Autowired ShippingOrderService shippingOrders;
    @Autowired ShippingQueryService shippingQueries;

    @Test
    void canonicalPayloadAndBase64UrlHmacAreStable() {
        LocalDateTime eventTime = LocalDateTime.parse("2026-08-12T10:15:30.123");
        LogisticsCallbackCommand unsigned = new LogisticsCallbackCommand(
                "callback-1", "nonce-1", 1786500930L, "SIM-L-123",
                TrackingEventType.IN_TRANSIT, eventTime, "杭州中转站", "运输中", null);

        assertThat(signer.canonical(unsigned)).isEqualTo(
                "callback-1\nnonce-1\n1786500930\nSIM-L-123\nIN_TRANSIT\n2026-08-12T10:15:30.123");
        assertThat(signer.sign(unsigned)).isEqualTo("ZBHOGNuoj3qW9fP8pcVtKGdeHzFxeyoly2k2aLfaOR8");
    }

    @Test
    void acceptsValidSignatureAndAdvancesStatus() {
        Fixture fixture = shipped(ShippingSourceType.CASH_ORDER);
        LogisticsCallbackCommand command = signed(fixture.waybillNo(), TrackingEventType.IN_TRANSIT,
                LocalDateTime.now().minusMinutes(1), unique("CALLBACK"), unique("NONCE"));

        callbacks.handle(command);

        assertThat(status(fixture.shippingNo())).isEqualTo(ShippingStatus.IN_TRANSIT);
        assertThat(count("shipping_tracking_event", "waybill_no", fixture.waybillNo())).isOne();
        assertThat(count("shipping_callback_receipt", "callback_id", command.callbackId())).isOne();
    }

    @Test
    void rejectsCanonicalDelimiterInjectionSoOneSignatureCannotRepresentDifferentTuples() {
        Fixture fixture = shipped(ShippingSourceType.CASH_ORDER);
        long timestamp = System.currentTimeMillis() / 1000;
        LocalDateTime eventTime = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        LogisticsCallbackCommand first = new LogisticsCallbackCommand(
                "part-a\npart-b", "part-c", timestamp, fixture.waybillNo(),
                TrackingEventType.IN_TRANSIT, eventTime, "杭州", "运输中", null);
        LogisticsCallbackCommand second = new LogisticsCallbackCommand(
                "part-a", "part-b\npart-c", timestamp, fixture.waybillNo(),
                TrackingEventType.IN_TRANSIT, eventTime, "杭州", "运输中", null);
        LogisticsCallbackCommand signedFirst = withSignature(first);
        LogisticsCallbackCommand signedSecond = withSignature(second);

        assertThat(signedFirst.signature()).isEqualTo(signedSecond.signature());
        assertError(() -> callbacks.handle(signedFirst), ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        assertError(() -> callbacks.handle(signedSecond), ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        assertThat(count("shipping_callback_receipt", "waybill_no", fixture.waybillNo())).isZero();
    }

    @Test
    void rejectsIdentifierWhitespaceAndControlCharactersWithoutTrimmedPersistence() {
        Fixture fixture = shipped(ShippingSourceType.CASH_ORDER);
        LocalDateTime eventTime = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        LogisticsCallbackCommand paddedCallback = signed(
                fixture.waybillNo(), TrackingEventType.IN_TRANSIT, eventTime,
                " " + unique("CALLBACK"), unique("NONCE"));
        LogisticsCallbackCommand controlledWaybill = signed(
                fixture.waybillNo() + "\r", TrackingEventType.IN_TRANSIT, eventTime,
                unique("CALLBACK"), unique("NONCE"));

        assertError(() -> callbacks.handle(paddedCallback), ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        assertError(() -> callbacks.handle(paddedCallback), ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        assertError(() -> callbacks.handle(controlledWaybill), ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        assertThat(count("shipping_callback_receipt", "waybill_no", fixture.waybillNo())).isZero();
    }

    @Test
    void rejectsTamperingAndOldOrFutureTimestampsWithoutPersistingSecrets() {
        Fixture fixture = shipped(ShippingSourceType.CASH_ORDER);
        LogisticsCallbackCommand valid = signed(fixture.waybillNo(), TrackingEventType.PICKED_UP,
                LocalDateTime.now(), unique("CALLBACK"), unique("NONCE"));
        LogisticsCallbackCommand tampered = new LogisticsCallbackCommand(
                valid.callbackId(), valid.nonce(), valid.timestampEpochSecond(), valid.waybillNo(),
                TrackingEventType.DELIVERED, valid.eventTime(), valid.locationSummary(),
                valid.description(), valid.signature());

        assertError(() -> callbacks.handle(tampered), ShippingErrorCode.CALLBACK_SIGNATURE_INVALID);
        assertError(() -> callbacks.handle(signedAt(fixture.waybillNo(), -301)),
                ShippingErrorCode.CALLBACK_SIGNATURE_INVALID);
        assertError(() -> callbacks.handle(signedAt(fixture.waybillNo(), 301)),
                ShippingErrorCode.CALLBACK_SIGNATURE_INVALID);
        assertThat(count("shipping_callback_receipt", "waybill_no", fixture.waybillNo())).isZero();
    }

    @Test
    void duplicateCallbackAndProviderEventAreIdempotentButNonceReplayIsRejected() {
        Fixture fixture = shipped(ShippingSourceType.CASH_ORDER);
        LocalDateTime eventTime = LocalDateTime.now().minusSeconds(10).truncatedTo(ChronoUnit.MILLIS);
        LogisticsCallbackCommand first = signed(fixture.waybillNo(), TrackingEventType.IN_TRANSIT,
                eventTime, unique("CALLBACK"), unique("NONCE"));
        callbacks.handle(first);
        callbacks.handle(first);

        LogisticsCallbackCommand sameEvent = signed(fixture.waybillNo(), TrackingEventType.IN_TRANSIT,
                eventTime, unique("CALLBACK"), unique("NONCE"));
        callbacks.handle(sameEvent);
        LogisticsCallbackCommand replay = signed(fixture.waybillNo(), TrackingEventType.DELIVERED,
                eventTime.plusMinutes(1), unique("CALLBACK"), first.nonce());

        assertError(() -> callbacks.handle(replay), ShippingErrorCode.CALLBACK_REPLAYED);
        assertThat(count("shipping_tracking_event", "waybill_no", fixture.waybillNo())).isOne();
        assertThat(count("shipping_callback_receipt", "waybill_no", fixture.waybillNo())).isEqualTo(2);
    }

    @Test
    void unknownWaybillIsSafelyRejectedAndAudited() {
        LogisticsCallbackCommand command = signed(unique("UNKNOWN-WAYBILL"), TrackingEventType.PICKED_UP,
                LocalDateTime.now(), unique("CALLBACK"), unique("NONCE"));

        assertError(() -> callbacks.handle(command), ShippingErrorCode.SHIPPING_NOT_FOUND);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM shipping_callback_receipt WHERE callback_id=?",
                String.class, command.callbackId())).isEqualTo("REJECTED");
    }

    @Test
    void rejectedStateConflictRetryKeepsOriginalSafeErrorCode() {
        Fixture fixture = shipped(ShippingSourceType.CASH_ORDER);
        jdbc.update("UPDATE shipping_order SET status='FAILED' WHERE shipping_no=?", fixture.shippingNo());
        LogisticsCallbackCommand command = signed(
                fixture.waybillNo(), TrackingEventType.IN_TRANSIT,
                LocalDateTime.now(), unique("CALLBACK"), unique("NONCE"));

        assertError(() -> callbacks.handle(command), ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        assertError(() -> callbacks.handle(command), ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        assertThat(jdbc.queryForObject(
                "SELECT error_code FROM shipping_callback_receipt WHERE callback_id=?",
                String.class, command.callbackId()))
                .isEqualTo(Integer.toString(ShippingErrorCode.SHIPPING_STATE_CONFLICT.code()));
    }

    @Test
    void deliveredThenDelayedInTransitKeepsImmutableTrackWithoutRegression() {
        Fixture fixture = shipped(ShippingSourceType.CASH_ORDER);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        callbacks.handle(signed(fixture.waybillNo(), TrackingEventType.DELIVERED,
                now, unique("CALLBACK"), unique("NONCE")));
        callbacks.handle(signed(fixture.waybillNo(), TrackingEventType.IN_TRANSIT,
                now.minusHours(2), unique("CALLBACK"), unique("NONCE")));

        assertThat(status(fixture.shippingNo())).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(count("shipping_tracking_event", "waybill_no", fixture.waybillNo())).isEqualTo(2);
    }

    @Test
    void supportsAllProviderEventLevelsAndFiveMinuteFutureBoundary() {
        Fixture fixture = shipped(ShippingSourceType.CASH_ORDER);
        LocalDateTime base = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MILLIS);

        callbacks.handle(signedAt(fixture.waybillNo(), TrackingEventType.PICKED_UP, base, 300));
        assertThat(status(fixture.shippingNo())).isEqualTo(ShippingStatus.SHIPPED);
        callbacks.handle(signed(fixture.waybillNo(), TrackingEventType.IN_TRANSIT,
                base.plusSeconds(1), unique("CALLBACK"), unique("NONCE")));
        callbacks.handle(signed(fixture.waybillNo(), TrackingEventType.OUT_FOR_DELIVERY,
                base.plusSeconds(2), unique("CALLBACK"), unique("NONCE")));
        assertThat(status(fixture.shippingNo())).isEqualTo(ShippingStatus.IN_TRANSIT);
        callbacks.handle(signed(fixture.waybillNo(), TrackingEventType.DELIVERED,
                base.plusSeconds(3), unique("CALLBACK"), unique("NONCE")));

        assertThat(status(fixture.shippingNo())).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(jdbc.queryForList("""
                SELECT event_type FROM shipping_tracking_event
                WHERE waybill_no=? ORDER BY event_time,id
                """, String.class, fixture.waybillNo())).containsExactly(
                "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED");
    }

    @Test
    void deliveredLotteryShippingProjectsBenefitButCashStateRemainsSeparate() {
        long userId = createUser();
        long benefitId = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L) + 1_000_000_000L;
        long drawRecordId = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000_000L) + 1_000_000_000L;
        jdbc.update("""
                INSERT INTO user_benefit
                (id,draw_record_id,user_id,prize_id,prize_type,quantity,status,obtained_at)
                VALUES (?,?,?,?,'PHYSICAL',1,'SHIPPED',NOW(3))
                """, benefitId, drawRecordId, userId, benefitId);
        try {
            String sourceId = Long.toString(benefitId);
            var snapshot = createSnapshot(userId, ShippingSourceType.LOTTERY_BENEFIT, sourceId);
            var order = shippingOrders.create(new CreateShippingOrderCommand(
                    ShippingSourceType.LOTTERY_BENEFIT, sourceId, userId, snapshot.getId(),
                    "SKU-LOTTERY", "抽奖实物", null, 1, unique("CLAIM")));
            trackFulfillment(order.fulfillmentNo());
            String waybillNo = unique("SIM-L-LOTTERY");
            jdbc.update("UPDATE shipping_order SET status='SHIPPED', waybill_no=? WHERE id=?",
                    waybillNo, order.id());

            callbacks.handle(signed(waybillNo, TrackingEventType.DELIVERED,
                    LocalDateTime.now(), unique("CALLBACK"), unique("NONCE")));

            assertThat(jdbc.queryForObject("SELECT status FROM user_benefit WHERE id=?",
                    String.class, benefitId)).isEqualTo("DELIVERED");
        } finally {
            jdbc.update("DELETE FROM user_benefit WHERE id=?", benefitId);
        }
    }

    @Test
    void queryReadsOnlyOwnedMaskedSnapshotAndOrdersImmutableTracks() {
        Fixture fixture = shipped(ShippingSourceType.CASH_ORDER);
        LocalDateTime later = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MILLIS);
        callbacks.handle(signed(fixture.waybillNo(), TrackingEventType.OUT_FOR_DELIVERY,
                later, unique("CALLBACK"), unique("NONCE")));
        callbacks.handle(signed(fixture.waybillNo(), TrackingEventType.IN_TRANSIT,
                later.minusMinutes(5), unique("CALLBACK"), unique("NONCE")));

        var view = shippingQueries.getForUser(fixture.userId(), fixture.shippingNo());

        assertThat(view.receiverMasked()).isEqualTo("张*");
        assertThat(view.phoneMasked()).isEqualTo("138****5678");
        assertThat(view.regionMasked()).endsWith("***");
        assertThat(view.tracking()).extracting(event -> event.eventType().name())
                .containsExactly("IN_TRANSIT", "OUT_FOR_DELIVERY");
        assertError(() -> shippingQueries.getForUser(createUser(), fixture.shippingNo()),
                ShippingErrorCode.SHIPPING_NOT_FOUND);
    }

    @Test
    void simulatorBuildsRandomSignedCallbackAndDelegatesWithoutShippingWrites() {
        SimulatorFailureRuleService failureRules = org.mockito.Mockito.mock(SimulatorFailureRuleService.class);
        ShippingOrderMapper orderMapper = org.mockito.Mockito.mock(ShippingOrderMapper.class);
        LogisticsCallbackSigner callbackSigner = org.mockito.Mockito.mock(LogisticsCallbackSigner.class);
        LogisticsCallbackService callbackService = org.mockito.Mockito.mock(LogisticsCallbackService.class);
        ShippingOrder order = new ShippingOrder();
        order.setWaybillNo("SIM-L-DELEGATED");
        org.mockito.Mockito.when(orderMapper.selectByFulfillmentNo("LOGISTICS-1")).thenReturn(order);
        org.mockito.Mockito.when(callbackSigner.sign(org.mockito.ArgumentMatchers.any()))
                .thenReturn("signed-by-local-secret");
        SimulatorAdminController controller = new SimulatorAdminController(
                failureRules, orderMapper, callbackSigner, callbackService);

        controller.trackingEvent("LOGISTICS-1", new SimulateTrackingEventCommand(
                TrackingEventType.IN_TRANSIT, LocalDateTime.parse("2026-08-12T10:00:00"),
                "杭州", "运输中"));

        var unsigned = org.mockito.ArgumentCaptor.forClass(LogisticsCallbackCommand.class);
        org.mockito.Mockito.verify(callbackSigner).sign(unsigned.capture());
        UUID.fromString(unsigned.getValue().callbackId());
        UUID.fromString(unsigned.getValue().nonce());
        var signed = org.mockito.ArgumentCaptor.forClass(LogisticsCallbackCommand.class);
        org.mockito.Mockito.verify(callbackService).handle(signed.capture());
        assertThat(signed.getValue().signature()).isEqualTo("signed-by-local-secret");
        assertThat(signed.getValue().waybillNo()).isEqualTo("SIM-L-DELEGATED");
        org.mockito.Mockito.verify(orderMapper).selectByFulfillmentNo("LOGISTICS-1");
        org.mockito.Mockito.verifyNoMoreInteractions(orderMapper);
    }

    private LogisticsCallbackCommand signedAt(String waybillNo, long offsetSeconds) {
        return signedAt(waybillNo, TrackingEventType.PICKED_UP, LocalDateTime.now(), offsetSeconds);
    }

    private LogisticsCallbackCommand signedAt(
            String waybillNo, TrackingEventType type, LocalDateTime eventTime, long offsetSeconds
    ) {
        LogisticsCallbackCommand unsigned = new LogisticsCallbackCommand(
                unique("CALLBACK"), unique("NONCE"), System.currentTimeMillis() / 1000 + offsetSeconds,
                waybillNo, type, eventTime, "杭州", "安全轨迹摘要", null);
        return withSignature(unsigned);
    }

    private LogisticsCallbackCommand signed(String waybillNo, TrackingEventType type,
                                            LocalDateTime eventTime, String callbackId, String nonce) {
        LogisticsCallbackCommand unsigned = new LogisticsCallbackCommand(
                callbackId, nonce, System.currentTimeMillis() / 1000, waybillNo,
                type, eventTime, "杭州中转站", "安全轨迹摘要", null);
        return withSignature(unsigned);
    }

    private LogisticsCallbackCommand withSignature(LogisticsCallbackCommand command) {
        return new LogisticsCallbackCommand(command.callbackId(), command.nonce(), command.timestampEpochSecond(),
                command.waybillNo(), command.eventType(), command.eventTime(), command.locationSummary(),
                command.description(), signer.sign(command));
    }

    private Fixture shipped(ShippingSourceType sourceType) {
        long userId = createUser();
        String sourceId = unique("TASK6-SOURCE");
        var snapshot = createSnapshot(userId, sourceType, sourceId);
        var order = shippingOrders.create(new CreateShippingOrderCommand(
                sourceType, sourceId, userId, snapshot.getId(), "SKU-TASK6", "阶段6物流", null, 1, null));
        trackFulfillment(order.fulfillmentNo());
        String waybillNo = unique("SIM-L");
        jdbc.update("UPDATE shipping_order SET status='SHIPPED', waybill_no=?, carrier_code='SIMULATOR', carrier_name='模拟物流', shipped_at=NOW(3) WHERE id=?",
                waybillNo, order.id());
        return new Fixture(userId, order.shippingNo(), waybillNo);
    }

    private ShippingStatus status(String shippingNo) {
        return ShippingStatus.valueOf(jdbc.queryForObject(
                "SELECT status FROM shipping_order WHERE shipping_no=?", String.class, shippingNo));
    }

    private int count(String table, String column, String value) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?",
                Integer.class, value);
    }

    private void assertError(Runnable operation, ShippingErrorCode code) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }

    private record Fixture(long userId, String shippingNo, String waybillNo) {}
}
