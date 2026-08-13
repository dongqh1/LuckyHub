package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.auth.model.JwtPayload;
import com.dongqh.luckyhub.auth.security.JwtService;
import com.dongqh.luckyhub.auth.security.SessionService;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.rbac.service.UserPermissionService;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.enums.ShippingStatus;
import com.dongqh.luckyhub.shipping.service.ShippingAdminService;
import com.dongqh.luckyhub.shipping.vo.ShippingOrderView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ShippingAdminControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean ShippingAdminService service;
    @MockitoBean JwtService jwt;
    @MockitoBean SessionService sessions;
    @MockitoBean UserPermissionService permissions;

    @BeforeEach
    void auth() {
        when(jwt.parse("token")).thenReturn(new JwtPayload(77L, "admin", "session"));
        when(sessions.isValid("session", 77L)).thenReturn(true);
        when(permissions.findPermissionCodes(77L)).thenReturn(Set.of(PermissionCodes.SHIPPING_OPERATE));
        when(service.get("SHIPPING-7")).thenReturn(view(ShippingStatus.FAILED));
        when(service.page(any())).thenReturn(new PageResponse<>(List.of(view(ShippingStatus.FAILED)), 1, 1, 20, 1));
        when(service.retry(eq("SHIPPING-7"), eq(77L), any())).thenReturn(view(ShippingStatus.FULFILLING));
        when(service.terminate(eq("SHIPPING-7"), eq(77L), any())).thenReturn(view(ShippingStatus.TERMINATED));
    }

    @Test
    void exposesExactMaskedAdminRoutes() throws Exception {
        mvc.perform(get("/api/admin/shipping/orders?status=FAILED&sourceType=LOTTERY_BENEFIT&sourceId=31&targetUserId=9&waybillNo=WB-7")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].receiverMasked").value("张*"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ciphertext"))));
        mvc.perform(get("/api/admin/shipping/orders/SHIPPING-7").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/shipping/orders/SHIPPING-7/retry").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"已核对安全数据\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/shipping/orders/SHIPPING-7/terminate").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"停止处理\"}"))
                .andExpect(status().isOk());
        verify(service).retry("SHIPPING-7", 77L, "已核对安全数据");
    }

    @Test
    void everyAdminRouteRequiresExactShippingOperatePermission() throws Exception {
        when(permissions.findPermissionCodes(77L)).thenReturn(Set.of(PermissionCodes.SHIPPING_READ));
        mvc.perform(get("/api/admin/shipping/orders").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/shipping/orders/SHIPPING-7").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/shipping/orders/SHIPPING-7/retry").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/shipping/orders/SHIPPING-7/terminate").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    private ShippingOrderView view(ShippingStatus status) {
        return new ShippingOrderView(7L, "SHIPPING-7", ShippingSourceType.LOTTERY_BENEFIT, "31", 9L,
                4L, "SKU-7", "礼盒", null, 1, "LOGISTICS-7", "SIM", "模拟物流", "WB-7", status,
                "SAFE_CODE", "安全摘要", null, null, null, null, null, null,
                "张*", "138****5678", "浙江省杭州市***");
    }
}
