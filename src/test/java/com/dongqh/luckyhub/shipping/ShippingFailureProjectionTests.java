package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.shipping.service.ShippingProjectionWorker;
import com.dongqh.luckyhub.shipping.service.impl.ShippingAdminServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import com.dongqh.luckyhub.fulfillment.enums.*;
import com.dongqh.luckyhub.fulfillment.service.FulfillmentTaskService;
import com.dongqh.luckyhub.fulfillment.vo.FulfillmentTaskView;
import com.dongqh.luckyhub.shipping.entity.ShippingOrder;
import com.dongqh.luckyhub.shipping.enums.*;
import com.dongqh.luckyhub.shipping.mapper.ShippingOrderMapper;
import com.dongqh.luckyhub.shipping.service.ShippingAddressSnapshotService;
import com.dongqh.luckyhub.shipping.service.ShippingOrderService;
import com.dongqh.luckyhub.shipping.service.impl.ShippingOrderServiceImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.dongqh.luckyhub.shipping.model.CreateShippingOrderCommand;

@SpringBootTest
class ShippingFailureProjectionTests extends Task5ShippingTestFixture {
    @Autowired ShippingProjectionWorker actualWorker;
    @Autowired ShippingOrderService actualShippingOrders;

    @Test
    void eachProjectionUsesIndependentRealTransactionSoOneRollbackDoesNotLoseTheNextCommit() {
        long userId = createUser();
        String firstSource = unique("TX-FIRST");
        String secondSource = unique("TX-SECOND");
        var firstSnapshot = createSnapshot(userId, ShippingSourceType.CASH_ORDER, firstSource);
        var secondSnapshot = createSnapshot(userId, ShippingSourceType.CASH_ORDER, secondSource);
        var first = actualShippingOrders.create(new CreateShippingOrderCommand(ShippingSourceType.CASH_ORDER,
                firstSource, userId, firstSnapshot.getId(), "SKU-1", "礼盒1", null, 1, null));
        var second = actualShippingOrders.create(new CreateShippingOrderCommand(ShippingSourceType.CASH_ORDER,
                secondSource, userId, secondSnapshot.getId(), "SKU-2", "礼盒2", null, 1, null));
        trackFulfillment(first.fulfillmentNo());
        trackFulfillment(second.fulfillmentNo());
        jdbc.update("UPDATE fulfillment_task SET status='SUCCEEDED',request_payload='\"bad\"',external_reference='WB-BAD' WHERE fulfillment_no=?",
                first.fulfillmentNo());
        jdbc.update("UPDATE fulfillment_task SET status='SUCCEEDED',external_reference='WB-GOOD' WHERE fulfillment_no=?",
                second.fulfillmentNo());

        assertThatThrownBy(() -> actualWorker.projectOne(first.id())).isInstanceOf(RuntimeException.class);
        assertThat(actualWorker.projectOne(second.id())).isTrue();

        assertThat(jdbc.queryForObject("SELECT status FROM shipping_order WHERE id=?", String.class, first.id()))
                .isEqualTo("FULFILLING");
        assertThat(jdbc.queryForObject("SELECT status FROM shipping_order WHERE id=?", String.class, second.id()))
                .isEqualTo("SHIPPED");
    }
    @Test
    void successfulLogisticsProjectsLotteryBenefitToShipped() {
        ShippingOrderMapper orders = mock(ShippingOrderMapper.class);
        ShippingOrder row = new ShippingOrder();
        row.setId(7L); row.setFulfillmentNo("LOGISTICS-7"); row.setSourceType(ShippingSourceType.LOTTERY_BENEFIT);
        row.setSourceId("31"); row.setStatus(ShippingStatus.FULFILLING); row.setVersion(1);
        when(orders.selectOne(any())).thenReturn(row);
        FulfillmentTaskService fulfillment = mock(FulfillmentTaskService.class);
        when(fulfillment.get("LOGISTICS-7")).thenReturn(new FulfillmentTaskView(1L, "LOGISTICS-7",
                "LOTTERY_BENEFIT", "31", FulfillmentType.LOGISTICS, 9L, null, "f",
                FulfillmentStatus.SUCCEEDED, 1, 5, null, "WB-7", null, null, null, null, null, null));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        var service = new ShippingOrderServiceImpl(orders, mock(ShippingAddressSnapshotService.class), fulfillment, jdbc);

        service.projectFulfillmentState("LOGISTICS-7");

        verify(jdbc).update(contains("SET status='SHIPPED'"), eq(31L));
    }

    @Test
    void succeededFulfillmentNeverRevivesATerminatedShipmentAndCasKeepsTerminalGuard() {
        ShippingOrderMapper orders = mock(ShippingOrderMapper.class);
        ShippingOrder terminated = new ShippingOrder();
        terminated.setId(8L); terminated.setFulfillmentNo("LOGISTICS-8");
        terminated.setSourceType(ShippingSourceType.CASH_ORDER); terminated.setSourceId("ORDER-8");
        terminated.setStatus(ShippingStatus.TERMINATED); terminated.setVersion(3);
        when(orders.selectOne(any())).thenReturn(terminated);
        FulfillmentTaskService fulfillment = mock(FulfillmentTaskService.class);
        when(fulfillment.get("LOGISTICS-8")).thenReturn(new FulfillmentTaskView(8L, "LOGISTICS-8",
                "CASH_ORDER", "ORDER-8", FulfillmentType.LOGISTICS, 9L, null, "f",
                FulfillmentStatus.SUCCEEDED, 1, 5, null, "WB-8", null, null, null, null, null, null));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var service = new ShippingOrderServiceImpl(orders, mock(ShippingAddressSnapshotService.class), fulfillment, jdbc);

        service.projectFulfillmentState("LOGISTICS-8");

        verifyNoInteractions(jdbc);

        ShippingOrder fulfilling = new ShippingOrder();
        fulfilling.setId(9L); fulfilling.setFulfillmentNo("LOGISTICS-9");
        fulfilling.setSourceType(ShippingSourceType.CASH_ORDER); fulfilling.setSourceId("ORDER-9");
        fulfilling.setStatus(ShippingStatus.FULFILLING); fulfilling.setVersion(4);
        when(orders.selectOne(any())).thenReturn(fulfilling);
        when(fulfillment.get("LOGISTICS-9")).thenReturn(new FulfillmentTaskView(9L, "LOGISTICS-9",
                "CASH_ORDER", "ORDER-9", FulfillmentType.LOGISTICS, 9L, null, "f",
                FulfillmentStatus.SUCCEEDED, 1, 5, null, "WB-9", null, null, null, null, null, null));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        service.projectFulfillmentState("LOGISTICS-9");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(Object[].class));
        assertThat(sql.getValue()).contains("status NOT IN ('DELIVERED','TERMINATED')");
    }

    @Test
    void projectorUsesAscendingBoundedCandidatesAndIsolatesEachFailure() {
        var orders = mock(com.dongqh.luckyhub.shipping.mapper.ShippingOrderMapper.class);
        var worker = mock(ShippingProjectionWorker.class);
        var ids = LongStream.rangeClosed(1, 100).boxed().toList();
        when(orders.selectProjectionCandidateIds(100)).thenReturn(ids);
        when(worker.projectOne(anyLong())).thenReturn(true);
        when(worker.projectOne(2L)).thenThrow(new IllegalStateException("raw provider exception with secret"));
        var service = new ShippingAdminServiceImpl(orders, null, null, null, worker);

        int projected = service.projectPending();

        assertThat(projected).isEqualTo(99);
        verify(worker).projectOne(1L);
        verify(worker).projectOne(2L);
        verify(worker).projectOne(100L);
        verify(worker, never()).projectOne(101L);
    }

    @Test
    void projectorPreservesFulfillmentTaskQueueOrderWhenShippingIdsAreReversed() {
        var orders = mock(ShippingOrderMapper.class);
        var worker = mock(ShippingProjectionWorker.class);
        when(orders.selectProjectionCandidateIds(100)).thenReturn(java.util.List.of(200L, 100L));
        when(worker.projectOne(anyLong())).thenReturn(true);
        var service = new ShippingAdminServiceImpl(orders, null, null, null, worker);

        assertThat(service.projectPending()).isEqualTo(2);

        var inOrder = inOrder(worker);
        inOrder.verify(worker).projectOne(200L);
        inOrder.verify(worker).projectOne(100L);
    }
}
