package com.dongqh.luckyhub.commerce;

import com.dongqh.luckyhub.auth.model.JwtPayload;
import com.dongqh.luckyhub.auth.security.JwtService;
import com.dongqh.luckyhub.auth.security.SessionService;
import com.dongqh.luckyhub.rbac.service.UserPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Phase3SecurityChainIntegrationTests {
    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwt;
    @MockitoBean SessionService sessions;
    @MockitoBean UserPermissionService permissions;

    @BeforeEach void callerWithoutPermission() {
        when(jwt.parse("token")).thenReturn(new JwtPayload(77L, "limited", "s1"));
        when(sessions.isValid("s1", 77L)).thenReturn(true);
        when(permissions.findPermissionCodes(77L)).thenReturn(Set.of());
    }

    @Test void protectsAllNewUserApiGroups() throws Exception {
        assertProtected(get("/api/coupons"));
        assertProtected(get("/api/memberships/me"));
        assertProtected(get("/api/orders"));
        assertProtected(post("/api/payments"));
    }

    private void assertProtected(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request).andExpect(status().isUnauthorized());
        mvc.perform(request.header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }
}
