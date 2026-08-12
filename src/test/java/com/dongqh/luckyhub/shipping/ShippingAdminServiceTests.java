package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentStatus;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType;
import com.dongqh.luckyhub.fulfillment.service.FulfillmentTaskService;
import com.dongqh.luckyhub.fulfillment.vo.FulfillmentTaskView;
import com.dongqh.luckyhub.shipping.entity.ShippingAddressSnapshot;
import com.dongqh.luckyhub.shipping.entity.ShippingOrder;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.enums.ShippingStatus;
import com.dongqh.luckyhub.shipping.mapper.ShippingAddressSnapshotMapper;
import com.dongqh.luckyhub.shipping.mapper.ShippingOrderMapper;
import com.dongqh.luckyhub.shipping.service.ShippingOrderService;
import com.dongqh.luckyhub.shipping.service.impl.ShippingAdminServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShippingAdminServiceTests {
    private final ShippingOrderMapper orders = mock(ShippingOrderMapper.class);
    private final ShippingAddressSnapshotMapper snapshots = mock(ShippingAddressSnapshotMapper.class);
    private final FulfillmentTaskService fulfillment = mock(FulfillmentTaskService.class);
    private final ShippingOrderService shipping = mock(ShippingOrderService.class);
    private final ShippingAdminServiceImpl service = new ShippingAdminServiceImpl(orders, snapshots, fulfillment, shipping);

    @Test
    void failedOrQuarantinedShipmentCanRetryAndReprojectWithoutChangingIdentity() {
        ShippingOrder failed = order(ShippingStatus.FAILED);
        when(orders.lockByShippingNo("SHIPPING-7")).thenReturn(failed);
        when(orders.selectByShippingNo("SHIPPING-7")).thenReturn(failed);
        when(snapshots.selectById(4L)).thenReturn(snapshot());
        when(fulfillment.get("LOGISTICS-7")).thenReturn(task(FulfillmentStatus.QUARANTINED));
        when(fulfillment.retryQuarantined(eq("LOGISTICS-7"), eq(77L), anyString()))
                .thenReturn(task(FulfillmentStatus.PENDING));

        service.retry("SHIPPING-7", 77L, "  " + "a".repeat(700) + "  ");

        verify(fulfillment).retryQuarantined("LOGISTICS-7", 77L, "a".repeat(500));
        verify(shipping).projectFulfillmentState("LOGISTICS-7");
    }

    @Test
    void retryAndTerminateRejectUnsafeOrFinalShipmentStates() {
        for (ShippingStatus status : new ShippingStatus[]{ShippingStatus.FULFILLING, ShippingStatus.SHIPPED,
                ShippingStatus.IN_TRANSIT, ShippingStatus.DELIVERED, ShippingStatus.TERMINATED}) {
            ShippingOrder row = order(status);
            when(orders.lockByShippingNo("SHIPPING-7")).thenReturn(row);
            assertThatThrownBy(() -> service.retry("SHIPPING-7", 77L, "safe"))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ShippingErrorCode.SHIPPING_STATE_CONFLICT));
        }
        for (ShippingStatus status : new ShippingStatus[]{ShippingStatus.SHIPPED,
                ShippingStatus.IN_TRANSIT, ShippingStatus.DELIVERED, ShippingStatus.TERMINATED}) {
            ShippingOrder row = order(status);
            when(orders.lockByShippingNo("SHIPPING-7")).thenReturn(row);
            assertThatThrownBy(() -> service.terminate("SHIPPING-7", 77L, "safe"))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ShippingErrorCode.SHIPPING_STATE_CONFLICT));
        }
        verifyNoInteractions(fulfillment, shipping);
    }

    private ShippingOrder order(ShippingStatus status) {
        ShippingOrder order = new ShippingOrder();
        order.setId(7L); order.setShippingNo("SHIPPING-7"); order.setSourceType(ShippingSourceType.LOTTERY_BENEFIT);
        order.setSourceId("31"); order.setTargetUserId(9L); order.setAddressSnapshotId(4L);
        order.setSkuCode("SKU-7"); order.setProductName("礼盒"); order.setQuantity(1);
        order.setFulfillmentNo("LOGISTICS-7"); order.setStatus(status); order.setVersion(1);
        return order;
    }

    private ShippingAddressSnapshot snapshot() {
        ShippingAddressSnapshot value = new ShippingAddressSnapshot();
        value.setId(4L); value.setReceiverMasked("张*"); value.setPhoneMasked("138****5678");
        value.setRegionMasked("浙江省杭州市***"); return value;
    }

    private FulfillmentTaskView task(FulfillmentStatus status) {
        return new FulfillmentTaskView(1L, "LOGISTICS-7", "LOTTERY_BENEFIT", "31", FulfillmentType.LOGISTICS,
                9L, null, "fingerprint", status, 5, 5, null, null, null, "SAFE", "安全摘要", null, null, null);
    }
}
