package com.dongqh.luckyhub.config;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LotterySecurityChainIntegrationTests {
    @Autowired MockMvc mockMvc;
    @MockitoBean JwtService jwtService;
    @MockitoBean SessionService sessionService;
    @MockitoBean UserPermissionService userPermissionService;

    @BeforeEach
    void prepareAuthenticatedCallerWithoutPermissions() {
        when(jwtService.parse("valid-token")).thenReturn(new JwtPayload(7711L, "limited", "session-1"));
        when(sessionService.isValid("session-1", 7711L)).thenReturn(true);
        when(userPermissionService.findPermissionCodes(7711L)).thenReturn(Set.of());
    }

    @Test
    void realFilterAndInterceptorProtectDeepUnifiedApiPaths() throws Exception {
        for (String path : new String[]{"/api/lottery/draws/request-1", "/api/benefits/1"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isForbidden());
        }
    }
}
