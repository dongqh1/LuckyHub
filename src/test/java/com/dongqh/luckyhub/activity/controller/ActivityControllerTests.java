package com.dongqh.luckyhub.activity.controller;

import com.dongqh.luckyhub.activity.dto.ActivityQuery;
import com.dongqh.luckyhub.activity.dto.CreateActivityCommand;
import com.dongqh.luckyhub.activity.dto.UpdateActivityCommand;
import com.dongqh.luckyhub.activity.enums.ActivityStatus;
import com.dongqh.luckyhub.activity.service.ActivityService;
import com.dongqh.luckyhub.activity.vo.ActivityView;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.common.web.GlobalExceptionHandler;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActivityControllerTests {

    private ActivityService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ActivityService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ActivityController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void exposesLifecycleEndpoints() throws Exception {
        ActivityView view = view(ActivityStatus.DRAFT);
        when(service.create(any())).thenReturn(view);
        when(service.getById(7L)).thenReturn(view);
        when(service.page(any())).thenReturn(new PageResponse<>(List.of(view), 1, 1, 20, 1));
        when(service.update(any(Long.class), any())).thenReturn(view);
        when(service.publish(7L)).thenReturn(view(ActivityStatus.SCHEDULED));
        when(service.restore(7L)).thenReturn(view);

        String body = """
                {
                  "activityName": "八月抽奖",
                  "description": "会员活动",
                  "startTime": "2026-08-01T10:00:00",
                  "endTime": "2026-08-10T22:00:00",
                  "dailyLimit": 3,
                  "noWinWeight": 25
                }
                """;

        mockMvc.perform(post("/api/admin/activities").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(7));
        mockMvc.perform(get("/api/admin/activities/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityName").value("八月抽奖"))
                .andExpect(jsonPath("$.data.noWinWeight").value(25));
        mockMvc.perform(get("/api/admin/activities").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(put("/api/admin/activities/7").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/admin/activities/7/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
        mockMvc.perform(patch("/api/admin/activities/7/disable"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/admin/activities/7/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void rejectsInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/api/admin/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30000));
    }

    @Test
    void rejectsMissingAndNegativeNoWinWeightButAllowsZero() throws Exception {
        String withoutNoWinWeight = validBody("");
        String negativeNoWinWeight = validBody(", \"noWinWeight\": -1");
        String zeroNoWinWeight = validBody(", \"noWinWeight\": 0");

        mockMvc.perform(post("/api/admin/activities")
                        .contentType(MediaType.APPLICATION_JSON).content(withoutNoWinWeight))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/activities")
                        .contentType(MediaType.APPLICATION_JSON).content(negativeNoWinWeight))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/activities/7")
                        .contentType(MediaType.APPLICATION_JSON).content(withoutNoWinWeight))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/activities/7")
                        .contentType(MediaType.APPLICATION_JSON).content(negativeNoWinWeight))
                .andExpect(status().isBadRequest());

        when(service.create(any())).thenReturn(view(ActivityStatus.DRAFT));
        when(service.update(any(Long.class), any())).thenReturn(view(ActivityStatus.DRAFT));
        mockMvc.perform(post("/api/admin/activities")
                        .contentType(MediaType.APPLICATION_JSON).content(zeroNoWinWeight))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/admin/activities/7")
                        .contentType(MediaType.APPLICATION_JSON).content(zeroNoWinWeight))
                .andExpect(status().isOk());
    }

    @Test
    void assignsExactPermissions() throws NoSuchMethodException {
        assertPermission("create", PermissionCodes.ACTIVITY_CREATE, CreateActivityCommand.class);
        assertPermission("getById", PermissionCodes.ACTIVITY_READ, long.class);
        assertPermission("page", PermissionCodes.ACTIVITY_READ, ActivityQuery.class);
        assertPermission("update", PermissionCodes.ACTIVITY_UPDATE, long.class, UpdateActivityCommand.class);
        assertPermission("publish", PermissionCodes.ACTIVITY_PUBLISH, long.class);
        assertPermission("disable", PermissionCodes.ACTIVITY_DISABLE, long.class);
        assertPermission("restore", PermissionCodes.ACTIVITY_RESTORE, long.class);
    }

    private void assertPermission(String name, String permission, Class<?>... types)
            throws NoSuchMethodException {
        Method method = ActivityController.class.getMethod(name, types);
        assertThat(method.getAnnotation(RequirePermission.class).value()).isEqualTo(permission);
    }

    private ActivityView view(ActivityStatus status) {
        return new ActivityView(
                7L, "八月抽奖", "会员活动", status,
                LocalDateTime.of(2026, 8, 1, 10, 0),
                LocalDateTime.of(2026, 8, 10, 22, 0),
                3, 25, 9L, null, null
        );
    }

    private String validBody(String noWinWeightProperty) {
        return """
                {
                  "activityName": "八月抽奖",
                  "startTime": "2026-08-01T10:00:00",
                  "endTime": "2026-08-10T22:00:00",
                  "dailyLimit": 3%s
                }
                """.formatted(noWinWeightProperty);
    }
}
