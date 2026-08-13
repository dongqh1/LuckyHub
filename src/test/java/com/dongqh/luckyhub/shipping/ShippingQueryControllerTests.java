package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.auth.model.JwtPayload;
import com.dongqh.luckyhub.auth.security.JwtService;
import com.dongqh.luckyhub.auth.security.SessionService;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.rbac.service.UserPermissionService;
import com.dongqh.luckyhub.shipping.dto.LogisticsCallbackCommand;
import com.dongqh.luckyhub.shipping.enums.ShippingStatus;
import com.dongqh.luckyhub.shipping.enums.TrackingEventType;
import com.dongqh.luckyhub.shipping.service.LogisticsCallbackService;
import com.dongqh.luckyhub.shipping.service.ShippingQueryService;
import com.dongqh.luckyhub.shipping.vo.ShippingTrackingView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ShippingQueryControllerTests.CallbackMethodProbe.class)
class ShippingQueryControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean LogisticsCallbackService callbacks;
    @MockitoBean ShippingQueryService queries;
    @MockitoBean JwtService jwt;
    @MockitoBean SessionService sessions;
    @MockitoBean UserPermissionService permissions;

    @BeforeEach
    void caller() {
        when(jwt.parse("token")).thenReturn(new JwtPayload(77L, "shipping-user", "session"));
        when(sessions.isValid("session", 77L)).thenReturn(true);
        when(permissions.findPermissionCodes(77L)).thenReturn(Set.of(PermissionCodes.SHIPPING_READ));
        when(queries.getForUser(77L, "SHIPPING-1")).thenReturn(view());
    }

    @Test
    void exactCallbackRouteNeedsOnlyValidSignedPayloadNotJwt() throws Exception {
        mvc.perform(post("/api/shipping/callbacks/logistics")
                        .contentType(MediaType.APPLICATION_JSON).content(callbackBody()))
                .andExpect(status().isOk());
        verify(callbacks).handle(any(LogisticsCallbackCommand.class));

        mvc.perform(post("/api/shipping/callbacks/logistics/extra")
                        .contentType(MediaType.APPLICATION_JSON).content(callbackBody()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/shipping/callbacks/logistics"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/shipping/orders/SHIPPING-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void callbackPermissionExemptionAppliesOnlyToPostEvenWithValidJwt() throws Exception {
        when(permissions.findPermissionCodes(77L)).thenReturn(Set.of());

        mvc.perform(get("/api/shipping/callbacks/logistics")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/shipping/callbacks/logistics")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownUserQueryReturnsOnlyMaskedSnapshotAndDeterministicTracks() throws Exception {
        mvc.perform(get("/api/shipping/orders/SHIPPING-1").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shippingNo").value("SHIPPING-1"))
                .andExpect(jsonPath("$.data.receiverMasked").value("张*"))
                .andExpect(jsonPath("$.data.phoneMasked").value("138****5678"))
                .andExpect(jsonPath("$.data.tracking[0].eventType").value("IN_TRANSIT"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("张三"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("13812345678"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ciphertext"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("nonce"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("signature"))));
        verify(queries).getForUser(77L, "SHIPPING-1");

        mvc.perform(get("/api/shipping/orders/SHIPPING-1/tracking")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventType").value("IN_TRANSIT"));
    }

    @Test
    void queryRequiresExactPermission() throws Exception {
        when(permissions.findPermissionCodes(77L)).thenReturn(Set.of(PermissionCodes.SHIPPING_ADDRESS_MANAGE));
        mvc.perform(get("/api/shipping/orders/SHIPPING-1").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    private ShippingTrackingView view() {
        LocalDateTime time = LocalDateTime.parse("2026-08-12T10:00:00");
        return new ShippingTrackingView("SHIPPING-1", ShippingStatus.IN_TRANSIT,
                "SIMULATOR", "模拟物流", "SIM-L-1", "实物礼盒", null, 1,
                "张*", "138****5678", "浙江省杭州市余杭区***",
                List.of(new ShippingTrackingView.TrackingEvent(
                        TrackingEventType.IN_TRANSIT, time, "杭州中转站", "运输中")));
    }

    private String callbackBody() {
        return """
                {"callbackId":"callback-1","nonce":"nonce-1","timestampEpochSecond":1786500930,
                 "waybillNo":"SIM-L-1","eventType":"IN_TRANSIT","eventTime":"2026-08-12T10:00:00",
                 "locationSummary":"杭州中转站","description":"运输中","signature":"signature"}
                """;
    }

    @RestController
    static class CallbackMethodProbe {
        @GetMapping("/api/shipping/callbacks/logistics")
        void getCallbackRoute() {
        }

        @PutMapping("/api/shipping/callbacks/logistics")
        void putCallbackRoute() {
        }
    }
}
