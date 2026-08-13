package com.dongqh.luckyhub.shipping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "luckyhub.fulfillment.initial-delay=24h",
        "luckyhub.fulfillment.projection-initial-delay=24h"
})
@ExtendWith(OutputCaptureExtension.class)
class PhysicalShippingSafetyTests extends ShippingTestFixture {

    @Test
    void plaintextPiiStaysOnlyInEncryptedAddressColumnsAndTransientGatewayMemory(CapturedOutput output) {
        CashFlow flow = paidCashFlow();
        shipAndDeliver(flow.shippingOrderId(), flow.fulfillmentNo());

        List<String> persisted = jdbc.queryForList("""
                SELECT CONCAT_WS('|', o.product_name,o.image_url,o.carrier_code,o.carrier_name,o.waybill_no,
                  t.request_payload,t.last_error_code,t.last_error_message,
                  a.external_reference,a.error_category,a.error_code,a.error_message,
                  e.location_summary,e.description,r.error_code,r.error_message,
                  s.request_payload,s.external_reference,s.status,
                  o.last_error_code,o.last_error_message)
                FROM shipping_order o
                LEFT JOIN fulfillment_task t ON t.fulfillment_no=o.fulfillment_no
                LEFT JOIN fulfillment_attempt a ON a.fulfillment_no=o.fulfillment_no
                LEFT JOIN shipping_tracking_event e ON e.shipping_order_id=o.id
                LEFT JOIN shipping_callback_receipt r ON r.waybill_no=o.waybill_no
                LEFT JOIN sim_logistics_record s ON s.fulfillment_no=o.fulfillment_no
                WHERE o.id=?
                """, String.class, flow.shippingOrderId());
        persisted.addAll(jdbc.queryForList("""
                SELECT CONCAT_WS('|',reason,error_category,error_code,error_message,resolution,resolution_note)
                FROM fulfillment_quarantine WHERE fulfillment_no=?
                """, String.class, flow.fulfillmentNo()));
        persisted.addAll(jdbc.queryForList("""
                SELECT CONCAT_WS('|',callback_id,nonce_digest,signature_digest,waybill_no,event_type,status,error_code,error_message)
                FROM shipping_callback_receipt WHERE waybill_no=(SELECT waybill_no FROM shipping_order WHERE id=?)
                """, String.class, flow.shippingOrderId()));
        String exposed = String.join("\n", persisted) + "\n" + output.getAll()
                + "\n" + json.writeValueAsString(cashOrders.get(flow.userId(), flow.orderNo()))
                + "\n" + json.writeValueAsString(shippingQueries.getForUser(flow.userId(), flow.shippingNo()))
                + "\n" + json.writeValueAsString(shippingAdmin.get(flow.shippingNo()));

        assertThat(exposed).doesNotContain(RECEIVER, PHONE, DETAIL)
                .doesNotContain("\"signature\"", "\"nonce\"", "ciphertext", "rawResponse");
        assertThat(exposed).contains("顾*", "139****4321");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM shipping_callback_receipt
                WHERE waybill_no=(SELECT waybill_no FROM shipping_order WHERE id=?)
                  AND (nonce_digest NOT REGEXP '^[0-9a-f]{64}$' OR signature_digest NOT REGEXP '^[0-9a-f]{64}$')
                """, Integer.class, flow.shippingOrderId())).isZero();
    }
}
