package com.dongqh.luckyhub.benefit.controller;

import com.dongqh.luckyhub.benefit.dto.BenefitQuery;
import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.benefit.service.BenefitQueryService;
import com.dongqh.luckyhub.benefit.vo.BenefitView;
import com.dongqh.luckyhub.shipping.dto.ClaimPhysicalBenefitCommand;
import com.dongqh.luckyhub.shipping.service.PhysicalClaimService;
import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.common.web.GlobalExceptionHandler;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.reward.enums.RewardType;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentStatus;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.rbac.interceptor.PermissionInterceptor;
import com.dongqh.luckyhub.rbac.service.UserPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BenefitControllerTests {
    private BenefitQueryService service;
    private MockMvc mockMvc;
    private UserPermissionService permissionService;
    private PhysicalClaimService claims;

    @BeforeEach
    void setUp() {
        service = mock(BenefitQueryService.class);
        permissionService = mock(UserPermissionService.class);
        claims = mock(PhysicalClaimService.class);
        LoginContext.set(new LoginPrincipal(77L, "tester", "session"));
        when(permissionService.findPermissionCodes(77L)).thenReturn(java.util.Set.of(PermissionCodes.BENEFIT_READ));
        mockMvc = MockMvcBuilders.standaloneSetup(new BenefitController(service, claims))
                .addInterceptors(new PermissionInterceptor(permissionService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach void tearDown() { LoginContext.clear(); }

    @Test
    void exposesBenefitListAndDetail() throws Exception {
        BenefitView view = new BenefitView(3L, 4L, 5L, 6L, PrizeType.COUPON, "咖啡券",
                "https://cdn/prize.png", 1, BenefitStatus.AVAILABLE, LocalDateTime.now(), null,
                7L, RewardType.COUPON, 2L, "LOTTERY-BENEFIT-3", FulfillmentStatus.SUCCEEDED);
        when(service.page(any())).thenReturn(new PageResponse<>(List.of(view), 1, 1, 20, 1));
        when(service.getById(3L)).thenReturn(view);

        mockMvc.perform(get("/api/benefits"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records[0].prizeName").value("咖啡券"))
                .andExpect(jsonPath("$.data.records[0].rewardType").value("COUPON"))
                .andExpect(jsonPath("$.data.records[0].rewardQuantity").value(2))
                .andExpect(jsonPath("$.data.records[0].fulfillmentStatus").value("SUCCEEDED"));
        mockMvc.perform(get("/api/benefits/3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(3));
    }

    @Test
    void validatesPaginationAndAssignsBasePermission() throws Exception {
        mockMvc.perform(get("/api/benefits").param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/benefits").param("page", Long.toString(Long.MAX_VALUE))).andExpect(status().isBadRequest());
        assertPermission("page", BenefitQuery.class);
        assertPermission("getById", long.class);
    }

    @Test void rejectsUnsafeBenefitDateBoundsBeforeServiceExecution() throws Exception {
        mockMvc.perform(get("/api/benefits").param("endDate", LocalDate.MAX.toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/benefits").param("endDate", "9999-12-31"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/benefits").param("startDate", "2026-08-02").param("endDate", "2026-08-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/benefits").param("endDate", "9999-12-30"))
                .andExpect(status().isOk());
    }

    @Test void rejectsAnonymousAndCallerWithoutBenefitRead() throws Exception {
        LoginContext.clear();
        mockMvc.perform(get("/api/benefits")).andExpect(status().isUnauthorized());
        LoginContext.set(new LoginPrincipal(77L, "tester", "session"));
        when(permissionService.findPermissionCodes(77L)).thenReturn(java.util.Set.of());
        mockMvc.perform(get("/api/benefits")).andExpect(status().isForbidden());
    }

    @Test
    void claimRequiresAuthenticationAndBenefitReadPermission() throws Exception {
        String body = "{\"requestId\":\"91da2b6d-30b6-46af-bbcb-1188bcdf0f66\",\"addressId\":51}";
        LoginContext.clear();
        mockMvc.perform(post("/api/benefits/31/claim").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        LoginContext.set(new LoginPrincipal(77L, "tester", "session"));
        when(permissionService.findPermissionCodes(77L)).thenReturn(java.util.Set.of());
        mockMvc.perform(post("/api/benefits/31/claim").contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        when(permissionService.findPermissionCodes(77L)).thenReturn(java.util.Set.of(PermissionCodes.BENEFIT_READ));
        mockMvc.perform(post("/api/benefits/31/claim").contentType("application/json").content(body))
                .andExpect(status().isOk());
        verify(claims).claim(77L, 31L,
                new ClaimPhysicalBenefitCommand("91da2b6d-30b6-46af-bbcb-1188bcdf0f66", 51L));
        assertPermission("claim", long.class, ClaimPhysicalBenefitCommand.class);
    }

    private void assertPermission(String name, Class<?>... types) throws Exception {
        Method method = BenefitController.class.getMethod(name, types);
        assertThat(method.getAnnotation(RequirePermission.class).value()).isEqualTo(PermissionCodes.BENEFIT_READ);
    }
}
