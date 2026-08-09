package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.auth.model.JwtPayload;
import com.dongqh.luckyhub.auth.security.JwtService;
import com.dongqh.luckyhub.auth.security.SessionService;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.rbac.service.UserPermissionService;
import com.dongqh.luckyhub.shipping.enums.AddressStatus;
import com.dongqh.luckyhub.shipping.service.ShippingAddressService;
import com.dongqh.luckyhub.shipping.vo.ShippingAddressView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ShippingAddressControllerTests {

    @Autowired MockMvc mvc;
    @MockitoBean ShippingAddressService service;
    @MockitoBean JwtService jwt;
    @MockitoBean SessionService sessions;
    @MockitoBean UserPermissionService permissions;

    @BeforeEach
    void caller() {
        when(jwt.parse("token")).thenReturn(new JwtPayload(77L, "shipping-user", "session"));
        when(sessions.isValid("session", 77L)).thenReturn(true);
    }

    @Test
    void exposesAllAddressOperationsWithOnlyMaskedResponseFields() throws Exception {
        when(permissions.findPermissionCodes(77L)).thenReturn(Set.of(PermissionCodes.SHIPPING_ADDRESS_MANAGE));
        when(service.create(eq(77L), any())).thenReturn(view());
        when(service.list(77L)).thenReturn(List.of(view()));
        when(service.update(eq(77L), eq(9L), any())).thenReturn(view());
        when(service.makeDefault(77L, 9L)).thenReturn(view());

        assertMasked(mvc.perform(post("/api/shipping/addresses").header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isCreated()));
        assertMasked(mvc.perform(get("/api/shipping/addresses").header("Authorization", "Bearer token"))
                .andExpect(status().isOk()), "$.data[0]");
        assertMasked(mvc.perform(put("/api/shipping/addresses/9").header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isOk()));
        assertMasked(mvc.perform(put("/api/shipping/addresses/9/default").header("Authorization", "Bearer token"))
                .andExpect(status().isOk()));
        mvc.perform(delete("/api/shipping/addresses/9").header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());

        verify(service).delete(77L, 9L);
    }

    @Test
    void requiresAuthenticationAndExactPermissionOnEveryAddressRoute() throws Exception {
        when(permissions.findPermissionCodes(77L)).thenReturn(Set.of());
        for (MockHttpServletRequestBuilder request : List.of(
                post("/api/shipping/addresses").contentType(MediaType.APPLICATION_JSON).content(body()),
                get("/api/shipping/addresses"),
                put("/api/shipping/addresses/9").contentType(MediaType.APPLICATION_JSON).content(body()),
                delete("/api/shipping/addresses/9"),
                put("/api/shipping/addresses/9/default"))) {
            mvc.perform(request).andExpect(status().isUnauthorized());
            mvc.perform(request.header("Authorization", "Bearer token")).andExpect(status().isForbidden());
        }
        when(permissions.findPermissionCodes(77L)).thenReturn(Set.of(PermissionCodes.SHIPPING_READ));
        mvc.perform(get("/api/shipping/addresses").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesCommandsAndPositiveIds() throws Exception {
        when(permissions.findPermissionCodes(77L)).thenReturn(Set.of(PermissionCodes.SHIPPING_ADDRESS_MANAGE));
        mvc.perform(post("/api/shipping/addresses").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON).content(body().replace("13812345678", "123")))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/shipping/addresses/0").header("Authorization", "Bearer token"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions assertMasked(
            org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return assertMasked(result, "$.data");
    }

    private org.springframework.test.web.servlet.ResultActions assertMasked(
            org.springframework.test.web.servlet.ResultActions result, String path) throws Exception {
        return result.andExpect(jsonPath(path + ".receiverMasked").value("张*"))
                .andExpect(jsonPath(path + ".phoneMasked").value("138****5678"))
                .andExpect(jsonPath(path + ".regionMasked").value("浙江省杭州市余杭区***"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("张三"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("13812345678"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ciphertext"))));
    }

    private ShippingAddressView view() {
        return new ShippingAddressView(9L, "张*", "138****5678", "浙江省杭州市余杭区***",
                true, AddressStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
    }

    private String body() {
        return """
                {"receiverName":"张三","phone":"13812345678","province":"浙江省",
                 "city":"杭州市","district":"余杭区","detail":"文一西路1号","defaultAddress":true}
                """;
    }
}
