package com.dongqh.luckyhub.commerce;

import com.dongqh.luckyhub.auth.model.JwtPayload;
import com.dongqh.luckyhub.auth.security.JwtService;
import com.dongqh.luckyhub.auth.security.SessionService;
import com.dongqh.luckyhub.coupon.service.CouponService;
import com.dongqh.luckyhub.membership.service.MembershipService;
import com.dongqh.luckyhub.order.service.CashOrderService;
import com.dongqh.luckyhub.payment.service.PaymentService;
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

import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Phase3ControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean CouponService coupons;
    @MockitoBean MembershipService memberships;
    @MockitoBean CashOrderService orders;
    @MockitoBean PaymentService payments;
    @MockitoBean JwtService jwt;
    @MockitoBean SessionService sessions;
    @MockitoBean UserPermissionService permissions;

    @BeforeEach void login() {
        when(jwt.parse("token")).thenReturn(new JwtPayload(77L, "buyer", "s1"));
        when(sessions.isValid("s1", 77L)).thenReturn(true);
        when(permissions.findPermissionCodes(77L)).thenReturn(Set.of(
                PermissionCodes.COUPON_READ, PermissionCodes.MEMBERSHIP_READ,
                PermissionCodes.ORDER_CREATE, PermissionCodes.ORDER_READ,
                PermissionCodes.ORDER_CANCEL, PermissionCodes.PAYMENT_CREATE));
    }

    @Test void exposesSelfScopedReadsAndCommands() throws Exception {
        when(memberships.getMine(77L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/coupons").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/memberships/me").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/orders").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"O-1\",\"skuId\":1,\"quantity\":1}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/payments").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentNo\":\"P-1\",\"orderNo\":\"O-1\"}"))
                .andExpect(status().isCreated());
        verify(memberships).getMine(77L);
        verify(orders).create(anyLong(), org.mockito.ArgumentMatchers.any());
        verify(payments).create(anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test void callbackUsesSignatureInsteadOfJwt() throws Exception {
        mvc.perform(post("/callbacks/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentNo\":\"P-1\",\"result\":\"SUCCESS\",\"signature\":\"signed\"}"))
                .andExpect(status().isOk());
        verify(payments).callback(org.mockito.ArgumentMatchers.any());
    }
}
