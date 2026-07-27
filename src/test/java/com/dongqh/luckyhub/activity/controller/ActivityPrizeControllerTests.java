package com.dongqh.luckyhub.activity.controller;

import com.dongqh.luckyhub.activity.dto.AddActivityPrizeCommand;
import com.dongqh.luckyhub.activity.dto.UpdateActivityPrizeCommand;
import com.dongqh.luckyhub.activity.service.ActivityPrizeService;
import com.dongqh.luckyhub.activity.vo.ActivityPrizeView;
import com.dongqh.luckyhub.common.web.GlobalExceptionHandler;
import com.dongqh.luckyhub.prize.enums.PrizeLevel;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActivityPrizeControllerTests {

    private ActivityPrizeService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ActivityPrizeService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ActivityPrizeController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void exposesActivityPrizeEndpoints() throws Exception {
        ActivityPrizeView view = view();
        when(service.add(any(Long.class), any())).thenReturn(view);
        when(service.list(5L)).thenReturn(List.of(view));
        when(service.update(any(Long.class), any(Long.class), any())).thenReturn(view);

        String addBody = """
                {"prizeId":7,"weight":20,"totalStock":100,"sortOrder":1}
                """;
        String updateBody = """
                {"weight":30,"totalStock":150,"sortOrder":2}
                """;

        mockMvc.perform(post("/api/admin/activities/5/prizes")
                        .contentType(MediaType.APPLICATION_JSON).content(addBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.remainingStock").value(100));
        mockMvc.perform(get("/api/admin/activities/5/prizes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].prizeName").value("咖啡券"));
        mockMvc.perform(put("/api/admin/activities/5/prizes/7")
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/activities/5/prizes/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void assignsExactPermissions() throws NoSuchMethodException {
        assertPermission("add", PermissionCodes.ACTIVITY_PRIZE_MANAGE,
                long.class, AddActivityPrizeCommand.class);
        assertPermission("list", PermissionCodes.ACTIVITY_READ, long.class);
        assertPermission("update", PermissionCodes.ACTIVITY_PRIZE_MANAGE,
                long.class, long.class, UpdateActivityPrizeCommand.class);
        assertPermission("remove", PermissionCodes.ACTIVITY_PRIZE_MANAGE,
                long.class, long.class);
    }

    private void assertPermission(String name, String permission, Class<?>... types)
            throws NoSuchMethodException {
        Method method = ActivityPrizeController.class.getMethod(name, types);
        assertThat(method.getAnnotation(RequirePermission.class).value()).isEqualTo(permission);
    }

    private ActivityPrizeView view() {
        return new ActivityPrizeView(
                11L, 5L, 7L, "咖啡券", PrizeType.COUPON,
                PrizeLevel.FIRST, "https://cdn.example/prize.png",
                1, 20, 100, 100, 1
        );
    }
}
