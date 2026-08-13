package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentStatus;
import com.dongqh.luckyhub.fulfillment.model.LogisticsFulfillmentPayload;
import com.dongqh.luckyhub.fulfillment.service.FulfillmentTaskService;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.enums.ShippingStatus;
import com.dongqh.luckyhub.shipping.model.CreateShippingOrderCommand;
import com.dongqh.luckyhub.shipping.service.ShippingOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ShippingOrderServiceTests extends Task5ShippingTestFixture {
    @Autowired ShippingOrderService shippingOrders;
    @Autowired FulfillmentTaskService fulfillmentTasks;

    @Test
    void createsOneStableOrderAndMaskedTaskPerSource() {
        long userId = createUser();
        String sourceId = unique("SOURCE");
        var snapshot = createSnapshot(userId, ShippingSourceType.LOTTERY_BENEFIT, sourceId);
        var command = new CreateShippingOrderCommand(
                ShippingSourceType.LOTTERY_BENEFIT, sourceId, userId, snapshot.getId(),
                "SKU-PHYSICAL", "实物礼盒", "https://cdn.example/gift.png", 2, unique("CLAIM"));

        var first = shippingOrders.create(command);
        trackFulfillment(first.fulfillmentNo());
        var repeated = shippingOrders.create(command);

        assertThat(repeated).isEqualTo(first);
        assertThat(first.shippingNo()).startsWith("SHIPPING-");
        assertThat(first.fulfillmentNo()).isEqualTo("LOGISTICS-" + first.id());
        assertThat(first.status()).isEqualTo(ShippingStatus.FULFILLING);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shipping_order WHERE source_type=? AND source_id=?",
                Integer.class, ShippingSourceType.LOTTERY_BENEFIT.name(), sourceId)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM fulfillment_task WHERE fulfillment_no=?",
                Integer.class, first.fulfillmentNo())).isOne();

        var task = fulfillmentTasks.get(first.fulfillmentNo());
        assertThat(task.status()).isEqualTo(FulfillmentStatus.PENDING);
        assertThat(task.payload()).isInstanceOfSatisfying(LogisticsFulfillmentPayload.class, payload -> {
            assertThat(payload.shippingOrderId()).isEqualTo(first.id());
            assertThat(payload.receiverMasked()).isEqualTo("张*");
            assertThat(payload.phoneMasked()).isEqualTo("138****5678");
            assertThat(payload.regionMasked()).contains("***");
        });
    }

    @Test
    void rejectsSourceReuseWhenAnyIdentityFieldChangesAndProtectsOwnerQuery() {
        long owner = createUser();
        long stranger = createUser();
        String sourceId = unique("IDENTITY");
        var snapshot = createSnapshot(owner, ShippingSourceType.CASH_ORDER, sourceId);
        var original = command(ShippingSourceType.CASH_ORDER, sourceId, owner, snapshot.getId(), 1);
        var created = shippingOrders.create(original);
        trackFulfillment(created.fulfillmentNo());

        assertThatThrownBy(() -> shippingOrders.create(
                command(ShippingSourceType.CASH_ORDER, sourceId, owner, snapshot.getId(), 2)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ShippingErrorCode.SHIPPING_IDEMPOTENCY_CONFLICT));
        assertThatThrownBy(() -> shippingOrders.getForUser(stranger, created.shippingNo()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ShippingErrorCode.SHIPPING_NOT_FOUND));
        assertThat(shippingOrders.getForUser(owner, created.shippingNo())).isEqualTo(created);
    }

    @Test
    void projectsQuarantineAndRetryWithoutChangingShippingIdentity() {
        long userId = createUser();
        String sourceId = unique("PROJECT");
        var snapshot = createSnapshot(userId, ShippingSourceType.CASH_ORDER, sourceId);
        var created = shippingOrders.create(
                command(ShippingSourceType.CASH_ORDER, sourceId, userId, snapshot.getId(), 1));
        trackFulfillment(created.fulfillmentNo());
        jdbc.update("""
                UPDATE fulfillment_task
                SET status='QUARANTINED', last_error_code=?, last_error_message=?
                WHERE fulfillment_no=?
                """, "PROVIDER_REJECTED", "安全失败摘要", created.fulfillmentNo());

        shippingOrders.projectFulfillmentState(created.fulfillmentNo());
        var failed = shippingOrders.getForUser(userId, created.shippingNo());
        assertThat(failed.status()).isEqualTo(ShippingStatus.FAILED);
        assertThat(failed.lastErrorCode()).isEqualTo("PROVIDER_REJECTED");
        assertThat(failed.lastErrorMessage()).isEqualTo("安全失败摘要");

        jdbc.update("UPDATE fulfillment_task SET status='PENDING' WHERE fulfillment_no=?",
                created.fulfillmentNo());
        shippingOrders.projectFulfillmentState(created.fulfillmentNo());
        var retrying = shippingOrders.getForUser(userId, created.shippingNo());
        assertThat(retrying.id()).isEqualTo(created.id());
        assertThat(retrying.fulfillmentNo()).isEqualTo(created.fulfillmentNo());
        assertThat(retrying.status()).isEqualTo(ShippingStatus.FULFILLING);
        assertThat(retrying.lastErrorCode()).isNull();

        jdbc.update("""
                UPDATE fulfillment_task
                SET status='TERMINATED', last_error_code=NULL, last_error_message=NULL
                WHERE fulfillment_no=?
                """, created.fulfillmentNo());
        shippingOrders.projectFulfillmentState(created.fulfillmentNo());
        var terminated = shippingOrders.getForUser(userId, created.shippingNo());
        assertThat(terminated.status()).isEqualTo(ShippingStatus.TERMINATED);
        assertThat(terminated.lastErrorCode()).isEqualTo("FULFILLMENT_TERMINATED");
        assertThat(terminated.lastErrorMessage()).isEqualTo("物流履约已安全终止");
    }

    private CreateShippingOrderCommand command(
            ShippingSourceType type, String sourceId, long userId, long snapshotId, int quantity
    ) {
        return new CreateShippingOrderCommand(type, sourceId, userId, snapshotId,
                "SKU-IDEM", "幂等实物", null, quantity, null);
    }
}
