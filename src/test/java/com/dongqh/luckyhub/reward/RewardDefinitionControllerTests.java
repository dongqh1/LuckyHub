package com.dongqh.luckyhub.reward;

import com.dongqh.luckyhub.auth.model.JwtPayload;
import com.dongqh.luckyhub.auth.security.JwtService;
import com.dongqh.luckyhub.auth.security.SessionService;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.rbac.service.UserPermissionService;
import com.dongqh.luckyhub.reward.enums.RewardType;
import com.dongqh.luckyhub.reward.service.RewardDefinitionService;
import com.dongqh.luckyhub.reward.vo.RewardDefinitionView;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RewardDefinitionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RewardDefinitionService service;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private UserPermissionService userPermissionService;

    @BeforeEach
    void prepareCaller() {
        when(jwtService.parse("valid-token")).thenReturn(new JwtPayload(7711L, "reward-admin", "session-1"));
        when(sessionService.isValid("session-1", 7711L)).thenReturn(true);
    }

    @Test
    void createsAndReadsRewardDefinitionWithManagePermission() throws Exception {
        when(userPermissionService.findPermissionCodes(7711L)).thenReturn(Set.of(PermissionCodes.REWARD_MANAGE));
        when(service.create(any())).thenReturn(view());
        when(service.get(9L)).thenReturn(view());

        mockMvc.perform(post("/api/admin/reward-definitions")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.rewardType").value("POINTS"));

        mockMvc.perform(get("/api/admin/reward-definitions/9")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(500));
    }

    @Test
    void invalidRequestUsesValidationEnvelope() throws Exception {
        when(userPermissionService.findPermissionCodes(7711L)).thenReturn(Set.of(PermissionCodes.REWARD_MANAGE));

        mockMvc.perform(post("/api/admin/reward-definitions")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("500积分", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30000));
    }

    @Test
    void realSecurityChainReturns401And403() throws Exception {
        when(userPermissionService.findPermissionCodes(7711L)).thenReturn(Set.of());

        mockMvc.perform(get("/api/admin/reward-definitions/9"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/reward-definitions/9")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/reward-definitions")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
    }

    private RewardDefinitionView view() {
        return new RewardDefinitionView(
                9L, "POINTS-500", "500积分", RewardType.POINTS,
                null, 500L, "{\"source\":\"lottery\"}", 1, null, null
        );
    }

    private String validBody() {
        return """
                {
                  "rewardCode": "POINTS-500",
                  "rewardName": "500积分",
                  "rewardType": "POINTS",
                  "quantity": 500,
                  "configSnapshot": "{\\\"source\\\":\\\"lottery\\\"}"
                }
                """;
    }
}
