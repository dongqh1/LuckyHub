package com.dongqh.luckyhub.lottery.controller;

import com.dongqh.luckyhub.activity.enums.ActivityStatus;
import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.common.web.GlobalExceptionHandler;
import com.dongqh.luckyhub.lottery.dto.DrawCommand;
import com.dongqh.luckyhub.lottery.dto.DrawOrderQuery;
import com.dongqh.luckyhub.lottery.dto.DrawRecordQuery;
import com.dongqh.luckyhub.lottery.enums.DrawOrderStatus;
import com.dongqh.luckyhub.lottery.enums.DrawResultType;
import com.dongqh.luckyhub.lottery.service.LotteryQueryService;
import com.dongqh.luckyhub.lottery.service.LotteryService;
import com.dongqh.luckyhub.lottery.vo.DrawOrderView;
import com.dongqh.luckyhub.lottery.vo.LotteryActivityView;
import com.dongqh.luckyhub.lottery.vo.DrawResultView;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.rbac.interceptor.PermissionInterceptor;
import com.dongqh.luckyhub.rbac.service.UserPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LotteryControllerTests {
    private LotteryService lotteryService;
    private LotteryQueryService queryService;
    private MockMvc mockMvc;
    private UserPermissionService permissionService;

    @BeforeEach
    void setUp() {
        lotteryService = mock(LotteryService.class);
        queryService = mock(LotteryQueryService.class);
        permissionService = mock(UserPermissionService.class);
        LoginContext.set(new LoginPrincipal(77L, "tester", "session"));
        when(permissionService.findPermissionCodes(77L)).thenReturn(java.util.Set.of(
                PermissionCodes.LOTTERY_ACTIVITY_READ, PermissionCodes.LOTTERY_DRAW,
                PermissionCodes.LOTTERY_DRAW_READ, PermissionCodes.LOTTERY_ORDER_READ_ALL,
                PermissionCodes.LOTTERY_RECORD_READ));
        mockMvc = MockMvcBuilders.standaloneSetup(new LotteryController(lotteryService, queryService))
                .addInterceptors(new PermissionInterceptor(permissionService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach void tearDown() { LoginContext.clear(); }

    @Test
    void exposesAllFiveLotteryRoutesAndSynchronousDrawResult() throws Exception {
        var activity = new LotteryActivityView(8L, "八月抽奖", "会员活动", ActivityStatus.RUNNING,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), 10);
        var order = new DrawOrderView(9L, "req-1", 8L, 1, LocalDate.now(),
                DrawOrderStatus.SUCCESS, null, LocalDateTime.now(), List.of());
        when(queryService.getActivity(8L)).thenReturn(activity);
        when(lotteryService.draw(any())).thenReturn(order);
        when(queryService.getDraw("req-1")).thenReturn(order);
        when(queryService.pageOrders(any())).thenReturn(new PageResponse<>(List.of(order), 1, 1, 20, 1));
        when(queryService.pageRecords(any())).thenReturn(new PageResponse<>(List.of(), 0, 1, 20, 0));

        mockMvc.perform(get("/api/lottery/activities/8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.activityName").value("八月抽奖"))
                .andExpect(jsonPath("$.data.noWinWeight").doesNotExist())
                .andExpect(jsonPath("$.data.weight").doesNotExist())
                .andExpect(jsonPath("$.data.totalStock").doesNotExist())
                .andExpect(jsonPath("$.data.remainingStock").doesNotExist());
        mockMvc.perform(post("/api/lottery/draws").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"req-1\",\"activityId\":8,\"drawCount\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUCCESS"));
        mockMvc.perform(get("/api/lottery/draws/req-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.requestId").value("req-1"));
        mockMvc.perform(get("/api/lottery/orders").param("page", "1").param("size", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/lottery/records"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void validatesDrawAndPaginationAtHttpBoundary() throws Exception {
        mockMvc.perform(post("/api/lottery/draws").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"\",\"activityId\":0,\"drawCount\":2}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(30000));
        mockMvc.perform(get("/api/lottery/records").param("page", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/lottery/orders").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsAllTenResultsSynchronouslyForTenDraw() throws Exception {
        List<DrawResultView> results = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(sequence -> new DrawResultView(sequence, sequence, DrawResultType.NO_WIN,
                        null, null, null, null, null)).toList();
        DrawOrderView order = new DrawOrderView(9L, "req-ten", 8L, 10, LocalDate.now(),
                DrawOrderStatus.SUCCESS, null, LocalDateTime.now(), results);
        when(lotteryService.draw(argThat(command -> command.drawCount() == 10))).thenReturn(order);

        mockMvc.perform(post("/api/lottery/draws").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"req-ten\",\"activityId\":8,\"drawCount\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.drawCount").value(10))
                .andExpect(jsonPath("$.data.results.length()").value(10));
    }

    @Test
    void assignsExactPermissions() throws Exception {
        assertPermission("getActivity", PermissionCodes.LOTTERY_ACTIVITY_READ, long.class);
        assertPermission("draw", PermissionCodes.LOTTERY_DRAW, DrawCommand.class);
        assertPermission("getDraw", PermissionCodes.LOTTERY_DRAW_READ, String.class);
        assertPermission("pageOrders", PermissionCodes.LOTTERY_ORDER_READ_ALL, DrawOrderQuery.class);
        assertPermission("pageRecords", PermissionCodes.LOTTERY_RECORD_READ, DrawRecordQuery.class);
    }

    @Test
    void rejectsAnonymousAndCallerWithoutRequiredPermission() throws Exception {
        LoginContext.clear();
        mockMvc.perform(get("/api/lottery/activities/8"))
                .andExpect(status().isUnauthorized());

        LoginContext.set(new LoginPrincipal(77L, "tester", "session"));
        when(permissionService.findPermissionCodes(77L)).thenReturn(java.util.Set.of());
        mockMvc.perform(get("/api/lottery/activities/8"))
                .andExpect(status().isForbidden());
    }

    private void assertPermission(String methodName, String permission, Class<?>... parameterTypes) throws Exception {
        Method method = LotteryController.class.getMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(RequirePermission.class).value()).isEqualTo(permission);
    }
}
