package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.shipping.dto.ClaimPhysicalBenefitCommand;
import com.dongqh.luckyhub.shipping.dto.LogisticsCallbackCommand;
import com.dongqh.luckyhub.shipping.enums.TrackingEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "luckyhub.fulfillment.initial-delay=24h",
        "luckyhub.fulfillment.projection-initial-delay=24h"
})
@ExtendWith(OutputCaptureExtension.class)
class PhysicalShippingSafetyTests extends ShippingTestFixture {
    private static final String CALLBACK_SECRET = "test-callback-secret-32-bytes-long";
    private static final Set<String> ALLOWED_ENCRYPTED_COLUMNS = Set.of(
            "user_shipping_address.receiver_ciphertext",
            "user_shipping_address.phone_ciphertext",
            "user_shipping_address.detail_ciphertext",
            "shipping_address_snapshot.receiver_ciphertext",
            "shipping_address_snapshot.phone_ciphertext",
            "shipping_address_snapshot.detail_ciphertext");

    @Test
    void threeSourcesKeepPlaintextAndCallbackSecretsInsideApprovedBoundaries(CapturedOutput output) throws Exception {
        CashFlow cash = paidCashFlow();
        PointsFlow pointsFlow = pointsFlow();
        LotteryFlow lotteryFlow = lotteryFlow();
        shipAndDeliver(cash.shippingOrderId(), cash.fulfillmentNo());
        shipAndDeliver(pointsFlow.shippingOrderId(), pointsFlow.fulfillmentNo());
        shipAndDeliver(lotteryFlow.shippingOrderId(), lotteryFlow.fulfillmentNo());

        String callbackId = unique("TASK8-PRIVACY-CALLBACK");
        String nonce = unique("TASK8-PRIVACY-NONCE");
        LogisticsCallbackCommand unsigned = new LogisticsCallbackCommand(
                callbackId, nonce, Instant.now().getEpochSecond(),
                jdbc.queryForObject("SELECT waybill_no FROM shipping_order WHERE id=?", String.class,
                        cash.shippingOrderId()), TrackingEventType.DELIVERED,
                LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS), "Task8安全节点", "Task8安全摘要", "pending");
        String signature = callbackSigner.sign(unsigned);
        LogisticsCallbackCommand signed = new LogisticsCallbackCommand(
                unsigned.callbackId(), unsigned.nonce(), unsigned.timestampEpochSecond(), unsigned.waybillNo(),
                unsigned.eventType(), unsigned.eventTime(), unsigned.locationSummary(), unsigned.description(), signature);
        logisticsCallbacks.handle(signed);

        String responses = serializeResponses(cash, pointsFlow, lotteryFlow);
        assertForbiddenProbesAbsentFromBusinessColumns(List.of(
                receiverProbe, phoneProbe, detailProbe, nonce, signature, CALLBACK_SECRET));
        assertApprovedCiphertext(cash.userId(), "CASH_ORDER", Long.toString(cash.sourceId()));
        assertApprovedCiphertext(pointsFlow.userId(), "POINTS_REDEMPTION", Long.toString(pointsFlow.sourceId()));
        assertApprovedCiphertext(lotteryFlow.userId(), "LOTTERY_BENEFIT", Long.toString(lotteryFlow.benefitId()));

        String exposed = output.getAll() + "\n" + responses;
        assertThat(exposed).doesNotContain(receiverProbe, phoneProbe, detailProbe, nonce, signature, CALLBACK_SECRET);
        assertThat(responses).contains(phoneProbe.substring(0, 3), phoneProbe.substring(7));
        assertDigestOnlyReceipt(callbackId, nonce, signature);
    }

    private String serializeResponses(CashFlow cash, PointsFlow pointsFlow, LotteryFlow lotteryFlow) {
        List<Object> responses = new ArrayList<>();
        responses.add(cashOrders.get(cash.userId(), cash.orderNo()));
        responses.add(redemptions.get(pointsFlow.userId(), pointsFlow.redemptionNo()));
        responses.add(claims.claim(lotteryFlow.userId(), lotteryFlow.benefitId(),
                new ClaimPhysicalBenefitCommand(lotteryFlow.claimRequestId(),
                        jdbc.queryForObject("SELECT address_id FROM shipping_address_snapshot WHERE source_type='LOTTERY_BENEFIT' AND source_id=?",
                                Long.class, Long.toString(lotteryFlow.benefitId())))));
        LoginContext.set(new LoginPrincipal(lotteryFlow.userId(), "task8-privacy-query", "task8-session"));
        try {
            responses.add(benefitQueries.getById(lotteryFlow.benefitId()));
        } finally {
            LoginContext.clear();
        }
        for (FlowShipping shipping : List.of(
                new FlowShipping(cash.userId(), cash.shippingNo()),
                new FlowShipping(pointsFlow.userId(), pointsFlow.shippingNo()),
                new FlowShipping(lotteryFlow.userId(), lotteryFlow.shippingNo()))) {
            responses.add(shippingQueries.getForUser(shipping.userId(), shipping.shippingNo()));
            responses.add(shippingAdmin.get(shipping.shippingNo()));
        }
        return json.writeValueAsString(responses);
    }

    private void assertForbiddenProbesAbsentFromBusinessColumns(List<String> probes) {
        List<BusinessTextColumn> columns = jdbc.query("""
                SELECT table_name,column_name FROM information_schema.columns
                WHERE table_schema=DATABASE()
                  AND table_name <> 'flyway_schema_history'
                  AND data_type IN ('char','varchar','tinytext','text','mediumtext','longtext','json')
                ORDER BY table_name,ordinal_position
                """, (rs, row) -> new BusinessTextColumn(rs.getString(1), rs.getString(2)));
        assertThat(columns).isNotEmpty();
        for (BusinessTextColumn column : columns) {
            if (ALLOWED_ENCRYPTED_COLUMNS.contains(column.qualifiedName())) continue;
            String sql = "SELECT COUNT(*) FROM `" + identifier(column.table()) + "` WHERE "
                    + "CAST(`" + identifier(column.column()) + "` AS CHAR) LIKE ?";
            for (String probe : probes) {
                assertThat(jdbc.queryForObject(sql, Integer.class, "%" + probe + "%"))
                        .as("plaintext/secret probe %s in %s", probe, column.qualifiedName()).isZero();
            }
        }
    }

    private void assertApprovedCiphertext(long userId, String sourceType, String sourceId) {
        Map<String, Object> address = jdbc.queryForMap("""
                SELECT receiver_ciphertext receiver,phone_ciphertext phone,detail_ciphertext detail
                FROM user_shipping_address WHERE user_id=?
                """, userId);
        Map<String, Object> snapshot = jdbc.queryForMap("""
                SELECT receiver_ciphertext receiver,phone_ciphertext phone,detail_ciphertext detail
                FROM shipping_address_snapshot WHERE source_type=? AND source_id=?
                """, sourceType, sourceId);
        assertEnvelope((String) address.get("receiver"), receiverProbe);
        assertEnvelope((String) address.get("phone"), phoneProbe);
        assertEnvelope((String) address.get("detail"), detailProbe);
        assertEnvelope((String) snapshot.get("receiver"), receiverProbe);
        assertEnvelope((String) snapshot.get("phone"), phoneProbe);
        assertEnvelope((String) snapshot.get("detail"), detailProbe);
    }

    private void assertEnvelope(String envelope, String plaintext) {
        assertThat(envelope).isNotEqualTo(plaintext).startsWith("v1.");
        assertThat(addressCipher.decrypt(envelope)).isEqualTo(plaintext);
    }

    private void assertDigestOnlyReceipt(String callbackId, String nonce, String signature) {
        Map<String, Object> receipt = jdbc.queryForMap("""
                SELECT nonce_digest,signature_digest FROM shipping_callback_receipt WHERE callback_id=?
                """, callbackId);
        assertThat(receipt.get("nonce_digest")).asString().matches("[0-9a-f]{64}").isNotEqualTo(nonce);
        assertThat(receipt.get("signature_digest")).asString().matches("[0-9a-f]{64}").isNotEqualTo(signature);
    }

    private String identifier(String value) {
        return value.replace("`", "``");
    }

    private record BusinessTextColumn(String table, String column) {
        String qualifiedName() { return table + "." + column; }
    }

    private record FlowShipping(long userId, String shippingNo) { }
}
