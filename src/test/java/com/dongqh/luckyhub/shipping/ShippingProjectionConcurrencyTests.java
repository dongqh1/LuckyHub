package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.fulfillment.enums.FulfillmentStatus;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType;
import com.dongqh.luckyhub.fulfillment.service.FulfillmentTaskService;
import com.dongqh.luckyhub.fulfillment.vo.FulfillmentTaskView;
import com.dongqh.luckyhub.shipping.entity.ShippingOrder;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.enums.ShippingStatus;
import com.dongqh.luckyhub.shipping.mapper.ShippingOrderMapper;
import com.dongqh.luckyhub.shipping.service.ShippingAddressSnapshotService;
import com.dongqh.luckyhub.shipping.service.impl.ShippingOrderServiceImpl;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShippingProjectionConcurrencyTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource("staleProjections")
    void staleProjectionCannotOverwriteAStateThatAdvancedAfterTheRead(
            String scenario,
            ShippingStatus staleStatus,
            FulfillmentStatus fulfillmentStatus,
            ShippingStatus advancedStatus
    ) {
        ShippingOrder stale = order(staleStatus, 7);
        ShippingOrderMapper mapper = mock(ShippingOrderMapper.class);
        when(mapper.selectOne(any())).thenReturn(stale);

        FulfillmentTaskService fulfillment = mock(FulfillmentTaskService.class);
        when(fulfillment.get("LOGISTICS-41")).thenReturn(task(fulfillmentStatus));

        AtomicReference<ShippingStatus> persistedStatus = new AtomicReference<>(advancedStatus);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            Object[] arguments = invocation.getArguments();
            Object[] parameters = arguments.length == 2 && arguments[1] instanceof Object[] values
                    ? values
                    : Arrays.copyOfRange(arguments, 1, arguments.length);
            if (sql.contains("AND version=?") && sql.contains("AND status=?")) {
                return 0; // The database row is already at advancedStatus/version 8.
            }
            persistedStatus.set(ShippingStatus.valueOf((String) parameters[3]));
            return 1;
        });

        var service = new ShippingOrderServiceImpl(
                mapper, mock(ShippingAddressSnapshotService.class), fulfillment, jdbc);

        service.projectFulfillmentState("LOGISTICS-41");

        assertThat(persistedStatus.get())
                .as("a stale %s projection must not overwrite %s", scenario, advancedStatus)
                .isEqualTo(advancedStatus);
    }

    private static Stream<Arguments> staleProjections() {
        return Stream.of(
                Arguments.of("success after transit", ShippingStatus.FULFILLING,
                        FulfillmentStatus.SUCCEEDED, ShippingStatus.IN_TRANSIT),
                Arguments.of("success after delivery", ShippingStatus.FULFILLING,
                        FulfillmentStatus.SUCCEEDED, ShippingStatus.DELIVERED),
                Arguments.of("failure after termination", ShippingStatus.FULFILLING,
                        FulfillmentStatus.QUARANTINED, ShippingStatus.TERMINATED),
                Arguments.of("termination after failure", ShippingStatus.FULFILLING,
                        FulfillmentStatus.TERMINATED, ShippingStatus.FAILED),
                Arguments.of("retry after termination", ShippingStatus.FAILED,
                        FulfillmentStatus.PENDING, ShippingStatus.TERMINATED));
    }

    private static ShippingOrder order(ShippingStatus status, int version) {
        ShippingOrder order = new ShippingOrder();
        order.setId(41L);
        order.setFulfillmentNo("LOGISTICS-41");
        order.setSourceType(ShippingSourceType.CASH_ORDER);
        order.setSourceId("ORDER-41");
        order.setStatus(status);
        order.setVersion(version);
        return order;
    }

    private static FulfillmentTaskView task(FulfillmentStatus status) {
        return new FulfillmentTaskView(1L, "LOGISTICS-41", "CASH_ORDER", "ORDER-41",
                FulfillmentType.LOGISTICS, 9L, null, "fingerprint", status, 1, 5,
                null, "WB-41", null, "PROVIDER_ERROR", "safe error", null, null, null);
    }
}
