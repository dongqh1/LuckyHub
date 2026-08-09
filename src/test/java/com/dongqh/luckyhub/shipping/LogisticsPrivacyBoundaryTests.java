package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.fulfillment.enums.AttemptOperation;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType;
import com.dongqh.luckyhub.fulfillment.model.FulfillmentClaim;
import com.dongqh.luckyhub.fulfillment.model.LogisticsFulfillmentPayload;
import com.dongqh.luckyhub.fulfillment.worker.FulfillmentWorker;
import com.dongqh.luckyhub.integration.gateway.GatewayResult;
import com.dongqh.luckyhub.integration.gateway.LogisticsCreateRequest;
import com.dongqh.luckyhub.integration.gateway.LogisticsGateway;
import com.dongqh.luckyhub.integration.simulator.SimulatedLogisticsGateway;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.enums.ShippingStatus;
import com.dongqh.luckyhub.shipping.integration.LogisticsRequestAssembler;
import com.dongqh.luckyhub.shipping.model.CreateShippingOrderCommand;
import com.dongqh.luckyhub.shipping.service.ShippingOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LogisticsPrivacyBoundaryTests extends Task5ShippingTestFixture {
    private static final String RECEIVER = "张三";
    private static final String PHONE = "13812345678";
    private static final String DETAIL = "文一西路1号";

    @Autowired ShippingOrderService shippingOrders;
    @Autowired LogisticsRequestAssembler assembler;
    @Autowired SimulatedLogisticsGateway simulator;
    @Autowired FulfillmentWorker worker;
    @Autowired AtomicBoolean gatewayTransactionObserved;
    @Autowired AtomicReference<LogisticsCreateRequest> capturedLogisticsRequest;

    @Test
    void assemblerDecryptsOnlyIntoRedactedTransientRequest() {
        long userId = createUser();
        String sourceId = unique("PRIVACY");
        var snapshot = createSnapshot(userId, ShippingSourceType.LOTTERY_BENEFIT, sourceId);
        var order = shippingOrders.create(new CreateShippingOrderCommand(
                ShippingSourceType.LOTTERY_BENEFIT, sourceId, userId, snapshot.getId(),
                "SKU-PRIVATE", "隐私实物", null, 1, unique("CLAIM-PRIVATE")));
        trackFulfillment(order.fulfillmentNo());
        var payload = new LogisticsFulfillmentPayload(
                order.id(), "SKU-PRIVATE", 1, "张*", "138****5678", "浙江省杭州市余杭区***");
        var claim = new FulfillmentClaim(1L, order.fulfillmentNo(), FulfillmentType.LOGISTICS,
                userId, "{}", AttemptOperation.EXECUTE, unique("LEASE"),
                LocalDateTime.now(), LocalDateTime.now().plusMinutes(1));

        LogisticsCreateRequest request = assembler.assemble(claim, payload);

        assertThat(request.receiver()).isEqualTo(RECEIVER);
        assertThat(request.phone()).isEqualTo(PHONE);
        assertThat(request.detail()).isEqualTo(DETAIL);
        assertThat(request.toString()).isEqualTo("LogisticsCreateRequest[REDACTED]")
                .doesNotContain(RECEIVER, PHONE, DETAIL);
    }

    @Test
    void simulatorPersistsAndFingerprintsOnlyMaskedSafeDto() {
        String fulfillmentNo = unique("SIM-PRIVATE");
        trackFulfillment(fulfillmentNo);
        LogisticsCreateRequest request = new LogisticsCreateRequest(
                fulfillmentNo, createUser(), 99L, "SKU-PRIVATE", 1,
                RECEIVER, PHONE, "浙江省", "杭州市", "余杭区", DETAIL,
                "张*", "138****5678", "浙江省杭州市余杭区***");

        simulator.execute(request);

        String payload = jdbc.queryForObject(
                "SELECT request_payload FROM sim_logistics_record WHERE fulfillment_no=?",
                String.class, fulfillmentNo);
        assertThat(payload).contains("张*", "138****5678", "浙江省杭州市余杭区***")
                .doesNotContain(RECEIVER, PHONE, DETAIL, "receiverCiphertext", "phoneCiphertext");
        assertThat(jdbc.queryForObject(
                "SELECT CHAR_LENGTH(request_fingerprint) FROM sim_logistics_record WHERE fulfillment_no=?",
                Integer.class, fulfillmentNo)).isEqualTo(64);
    }

    @Test
    void workerAssemblesPlaintextAndInvokesGatewayOutsideTransaction() {
        gatewayTransactionObserved.set(true);
        capturedLogisticsRequest.set(null);
        long userId = createUser();
        String sourceId = unique("WORKER-PRIVATE");
        var snapshot = createSnapshot(userId, ShippingSourceType.CASH_ORDER, sourceId);
        var order = shippingOrders.create(new CreateShippingOrderCommand(
                ShippingSourceType.CASH_ORDER, sourceId, userId, snapshot.getId(),
                "SKU-WORKER", "事务外物流", null, 1, null));
        trackFulfillment(order.fulfillmentNo());

        worker.runBatch();

        assertThat(gatewayTransactionObserved).isFalse();
        assertThat(capturedLogisticsRequest.get()).isNotNull().satisfies(request -> {
            assertThat(request.receiver()).isEqualTo(RECEIVER);
            assertThat(request.phone()).isEqualTo(PHONE);
            assertThat(request.detail()).isEqualTo(DETAIL);
        });
        var shipped = shippingOrders.getForUser(userId, order.shippingNo());
        assertThat(shipped.status()).isEqualTo(ShippingStatus.SHIPPED);
        assertThat(shipped.waybillNo()).isEqualTo("TEST-WAYBILL");
    }

    @TestConfiguration
    static class GatewayConfiguration {
        @Bean AtomicBoolean gatewayTransactionObserved() {
            return new AtomicBoolean();
        }

        @Bean AtomicReference<LogisticsCreateRequest> capturedLogisticsRequest() {
            return new AtomicReference<>();
        }

        @Bean
        @Primary
        LogisticsGateway privacyBoundaryGateway(
                AtomicBoolean observed,
                AtomicReference<LogisticsCreateRequest> captured
        ) {
            return new LogisticsGateway() {
                @Override
                public GatewayResult execute(LogisticsCreateRequest request) {
                    observed.set(TransactionSynchronizationManager.isActualTransactionActive());
                    captured.set(request);
                    return GatewayResult.succeeded("TEST-WAYBILL");
                }

                @Override
                public GatewayResult query(String fulfillmentNo) {
                    observed.set(TransactionSynchronizationManager.isActualTransactionActive());
                    return GatewayResult.notFound();
                }
            };
        }
    }
}
