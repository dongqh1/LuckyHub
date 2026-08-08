package com.dongqh.luckyhub.inventory;

import com.dongqh.luckyhub.auth.model.JwtPayload;
import com.dongqh.luckyhub.auth.security.JwtService;
import com.dongqh.luckyhub.auth.security.SessionService;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.inventory.channel.dto.AllocateChannelStockCommand;
import com.dongqh.luckyhub.inventory.channel.dto.InitializeSkuStockCommand;
import com.dongqh.luckyhub.inventory.channel.dto.ReserveChannelStockCommand;
import com.dongqh.luckyhub.inventory.channel.enums.ChannelInventoryErrorCode;
import com.dongqh.luckyhub.inventory.channel.enums.InventoryReservationStatus;
import com.dongqh.luckyhub.inventory.channel.service.ChannelInventoryService;
import com.dongqh.luckyhub.inventory.channel.vo.ChannelInventoryView;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.rbac.service.UserPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChannelInventoryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChannelInventoryService service;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private UserPermissionService userPermissionService;

    @BeforeEach
    void prepareCaller() {
        when(jwtService.parse("valid-token"))
                .thenReturn(new JwtPayload(7711L, "inventory-admin", "session-1"));
        when(sessionService.isValid("session-1", 7711L)).thenReturn(true);
    }

    @Test
    void exposesInventoryLifecycleWithManagePermission() throws Exception {
        when(userPermissionService.findPermissionCodes(7711L))
                .thenReturn(Set.of(PermissionCodes.INVENTORY_MANAGE));
        when(service.initialize(any())).thenReturn(totalView());
        when(service.allocate(any())).thenReturn(channelView());
        when(service.reserve(any())).thenReturn(reservationView(InventoryReservationStatus.RESERVED));
        when(service.confirm("ORDER-1001"))
                .thenReturn(reservationView(InventoryReservationStatus.CONFIRMED));
        when(service.release("ORDER-1001"))
                .thenReturn(reservationView(InventoryReservationStatus.RELEASED));
        when(service.get(31L, "MALL")).thenReturn(channelView());

        mockMvc.perform(post("/api/admin/inventory/skus/initialize")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initializeBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalStock").value(100));

        mockMvc.perform(post("/api/admin/inventory/channels/allocate")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(allocationBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channelCode").value("MALL"))
                .andExpect(jsonPath("$.data.availableStock").value(30));

        mockMvc.perform(post("/api/admin/inventory/reservations")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reservationStatus").value("RESERVED"));

        mockMvc.perform(post("/api/admin/inventory/reservations/ORDER-1001/confirm")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reservationStatus").value("CONFIRMED"));

        mockMvc.perform(post("/api/admin/inventory/reservations/ORDER-1001/release")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reservationStatus").value("RELEASED"));

        mockMvc.perform(get("/api/admin/inventory/skus/31/channels/MALL")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allocatedStock").value(30));

        verify(service).initialize(new InitializeSkuStockCommand(31L, 100, "INIT-31"));
        verify(service).allocate(new AllocateChannelStockCommand(31L, "MALL", 30, "ALLOC-31-MALL"));
        verify(service).reserve(new ReserveChannelStockCommand(31L, "MALL", 1, "ORDER-1001"));
        verify(service).confirm("ORDER-1001");
        verify(service).release("ORDER-1001");
        verify(service).get(31L, "MALL");
    }

    @Test
    void invalidTransportInputUsesValidationEnvelope() throws Exception {
        when(userPermissionService.findPermissionCodes(7711L))
                .thenReturn(Set.of(PermissionCodes.INVENTORY_MANAGE));

        mockMvc.perform(post("/api/admin/inventory/skus/initialize")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initializeBody().replace("100", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30000));

        mockMvc.perform(get("/api/admin/inventory/skus/0/channels/MALL")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30000));

        mockMvc.perform(get("/api/admin/inventory/skus/31/channels/" + "A".repeat(101))
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30000));

        mockMvc.perform(post("/api/admin/inventory/reservations/" + "R".repeat(65) + "/confirm")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30000));
    }

    @Test
    void realSecurityChainReturns401And403ForEveryEndpoint() throws Exception {
        when(userPermissionService.findPermissionCodes(7711L)).thenReturn(Set.of());

        assertProtected(post("/api/admin/inventory/skus/initialize")
                .contentType(MediaType.APPLICATION_JSON).content(initializeBody()));
        assertProtected(post("/api/admin/inventory/channels/allocate")
                .contentType(MediaType.APPLICATION_JSON).content(allocationBody()));
        assertProtected(post("/api/admin/inventory/reservations")
                .contentType(MediaType.APPLICATION_JSON).content(reservationBody()));
        assertProtected(post("/api/admin/inventory/reservations/ORDER-1001/confirm"));
        assertProtected(post("/api/admin/inventory/reservations/ORDER-1001/release"));
        assertProtected(get("/api/admin/inventory/skus/31/channels/MALL"));
    }

    @Test
    void stableBusinessErrorsKeepTheirHttpStatusAndCode() throws Exception {
        when(userPermissionService.findPermissionCodes(7711L))
                .thenReturn(Set.of(PermissionCodes.INVENTORY_MANAGE));
        when(service.get(31L, "MALL"))
                .thenThrow(new BusinessException(ChannelInventoryErrorCode.INVENTORY_NOT_FOUND));
        when(service.reserve(any()))
                .thenThrow(new BusinessException(ChannelInventoryErrorCode.INVENTORY_INSUFFICIENT));

        mockMvc.perform(get("/api/admin/inventory/skus/31/channels/MALL")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(46001));

        mockMvc.perform(post("/api/admin/inventory/reservations")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(46002));
    }

    private void assertProtected(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mockMvc.perform(request).andExpect(status().isUnauthorized());
        mockMvc.perform(request.header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden());
    }

    private ChannelInventoryView totalView() {
        return new ChannelInventoryView(31L, null, 100, 30, null, null, null, null, null);
    }

    private ChannelInventoryView channelView() {
        return new ChannelInventoryView(31L, "MALL", 100, 30, 30, 0, 0, null, null);
    }

    private ChannelInventoryView reservationView(InventoryReservationStatus status) {
        return new ChannelInventoryView(31L, "MALL", 100, 30, 29, 1, 0, "ORDER-1001", status);
    }

    private String initializeBody() {
        return """
                {"skuId":31,"totalStock":100,"businessNo":"INIT-31"}
                """;
    }

    private String allocationBody() {
        return """
                {"skuId":31,"channelCode":"MALL","quantity":30,"businessNo":"ALLOC-31-MALL"}
                """;
    }

    private String reservationBody() {
        return """
                {"skuId":31,"channelCode":"MALL","quantity":1,"reservationNo":"ORDER-1001"}
                """;
    }
}
