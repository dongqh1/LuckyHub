package com.dongqh.luckyhub.points;

import com.dongqh.luckyhub.auth.model.JwtPayload;
import com.dongqh.luckyhub.auth.security.JwtService;
import com.dongqh.luckyhub.auth.security.SessionService;
import com.dongqh.luckyhub.rbac.service.UserPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PointsSecurityChainIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private SessionService sessionService;
    @MockitoBean private UserPermissionService userPermissionService;

    @BeforeEach
    void prepareCallerWithoutPermissions() {
        when(jwtService.parse("valid-token"))
                .thenReturn(new JwtPayload(7711L, "limited", "session-1"));
        when(sessionService.isValid("session-1", 7711L)).thenReturn(true);
        when(userPermissionService.findPermissionCodes(7711L)).thenReturn(Set.of());
    }

    @Test
    void realFilterAndPermissionInterceptorProtectAllSevenEndpoints() throws Exception {
        assertProtected(get("/api/points/account"));
        assertProtected(get("/api/points/ledgers"));
        assertProtected(post("/api/points/redemptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"redemptionNo\":\"R-1\",\"skuId\":1,\"quantity\":1}"));
        assertProtected(get("/api/points/redemptions"));
        assertProtected(get("/api/points/redemptions/R-1"));
        assertProtected(post("/api/admin/points/adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":1,\"delta\":1,\"businessId\":\"A-1\",\"reason\":\"test\"}"));
        assertProtected(post("/api/admin/points/redemptions/R-1/reverse")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reversalNo\":\"RV-1\",\"reason\":\"test\"}"));
    }

    private void assertProtected(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(20004));
        mockMvc.perform(request.header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20001));
    }
}
