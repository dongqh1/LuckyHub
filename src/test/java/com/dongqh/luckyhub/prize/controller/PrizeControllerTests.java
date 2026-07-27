package com.dongqh.luckyhub.prize.controller;

import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.common.web.GlobalExceptionHandler;
import com.dongqh.luckyhub.prize.dto.CreatePrizeCommand;
import com.dongqh.luckyhub.prize.dto.PrizeQuery;
import com.dongqh.luckyhub.prize.dto.UpdatePrizeCommand;
import com.dongqh.luckyhub.prize.enums.PrizeLevel;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.prize.service.PrizeService;
import com.dongqh.luckyhub.prize.vo.PrizeView;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PrizeControllerTests {

    private PrizeService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(PrizeService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PrizeController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void exposesPrizeCrudEndpoints() throws Exception {
        PrizeView view = view(7L);
        when(service.create(any())).thenReturn(view);
        when(service.getById(7L)).thenReturn(view);
        when(service.update(any(Long.class), any())).thenReturn(view);
        when(service.page(any())).thenReturn(new PageResponse<>(List.of(view), 1, 1, 20, 1));

        String body = """
                {
                  "prizeName": "咖啡券",
                  "prizeType": "COUPON",
                  "prizeLevel": "FIRST",
                  "imageUrl": "https://cdn.example/prize.jpg",
                  "description": "测试奖品",
                  "stackable": true
                }
                """;

        mockMvc.perform(post("/api/admin/prizes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(7));
        mockMvc.perform(get("/api/admin/prizes/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prizeName").value("咖啡券"));
        mockMvc.perform(get("/api/admin/prizes").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(put("/api/admin/prizes/7").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/admin/prizes/7/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void rejectsInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/api/admin/prizes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prizeName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30000));
    }

    @Test
    void assignsExactPermissions() throws NoSuchMethodException {
        assertPermission("create", PermissionCodes.PRIZE_CREATE, CreatePrizeCommand.class);
        assertPermission("getById", PermissionCodes.PRIZE_READ, long.class);
        assertPermission("page", PermissionCodes.PRIZE_READ, PrizeQuery.class);
        assertPermission("update", PermissionCodes.PRIZE_UPDATE, long.class, UpdatePrizeCommand.class);
        assertPermission("disable", PermissionCodes.PRIZE_DISABLE, long.class);
    }

    private void assertPermission(String methodName, String permission, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = PrizeController.class.getMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(RequirePermission.class).value()).isEqualTo(permission);
    }

    private PrizeView view(long id) {
        return new PrizeView(
                id,
                "咖啡券",
                PrizeType.COUPON,
                PrizeLevel.FIRST,
                "https://cdn.example/prize.jpg",
                "测试奖品",
                true,
                1,
                null,
                null
        );
    }
}
