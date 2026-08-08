package com.dongqh.luckyhub.points;

import com.dongqh.luckyhub.auth.model.JwtPayload;
import com.dongqh.luckyhub.auth.security.JwtService;
import com.dongqh.luckyhub.auth.security.SessionService;
import com.dongqh.luckyhub.catalog.enums.ProductType;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.points.dto.CreatePointsRedemptionCommand;
import com.dongqh.luckyhub.points.dto.PointsRedemptionQuery;
import com.dongqh.luckyhub.points.dto.ReversePointsRedemptionCommand;
import com.dongqh.luckyhub.points.enums.PointsErrorCode;
import com.dongqh.luckyhub.points.enums.PointsRedemptionStatus;
import com.dongqh.luckyhub.points.service.PointsRedemptionService;
import com.dongqh.luckyhub.points.vo.PointsRedemptionView;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PointsRedemptionControllerTests {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PointsRedemptionService service;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private SessionService sessionService;
    @MockitoBean private UserPermissionService userPermissionService;

    @BeforeEach
    void prepareCaller() {
        when(jwtService.parse("valid-token"))
                .thenReturn(new JwtPayload(7711L, "points-user", "session-1"));
        when(sessionService.isValid("session-1", 7711L)).thenReturn(true);
        when(userPermissionService.findPermissionCodes(7711L)).thenReturn(Set.of(
                PermissionCodes.POINTS_READ,
                PermissionCodes.POINTS_REDEEM,
                PermissionCodes.POINTS_ADJUST));
    }

    @Test
    void exposesCreateListDetailAndAdminReverse() throws Exception {
        when(service.create(anyLong(), any())).thenReturn(view(PointsRedemptionStatus.COMPLETED));
        when(service.page(anyLong(), any())).thenReturn(new PageResponse<>(
                List.of(view(PointsRedemptionStatus.COMPLETED)), 1, 1, 20, 1));
        when(service.get(7711L, "REDEEM-1")).thenReturn(view(PointsRedemptionStatus.COMPLETED));
        when(service.reverse(any(), any())).thenReturn(view(PointsRedemptionStatus.REVERSED));

        mockMvc.perform(post("/api/points/redemptions")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"redemptionNo":"REDEEM-1","skuId":31,"quantity":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(get("/api/points/redemptions")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].redemptionNo").value("REDEEM-1"));

        mockMvc.perform(get("/api/points/redemptions/REDEEM-1")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(7711));

        mockMvc.perform(post("/api/admin/points/redemptions/REDEEM-1/reverse")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reversalNo":"REV-1","reason":"履约失败"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVERSED"));

        verify(service).create(7711L, new CreatePointsRedemptionCommand("REDEEM-1", 31L, 1));
        verify(service).page(org.mockito.ArgumentMatchers.eq(7711L), any(PointsRedemptionQuery.class));
        verify(service).get(7711L, "REDEEM-1");
        verify(service).reverse("REDEEM-1",
                new ReversePointsRedemptionCommand("REV-1", "履约失败"));
    }

    @Test
    void validatesTransportAndPreservesRedemptionErrors() throws Exception {
        mockMvc.perform(post("/api/points/redemptions")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"redemptionNo":"REDEEM-1","skuId":31,"quantity":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30000));

        when(service.create(anyLong(), any()))
                .thenThrow(new BusinessException(PointsErrorCode.POINTS_INSUFFICIENT));
        mockMvc.perform(post("/api/points/redemptions")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"redemptionNo":"REDEEM-2","skuId":31,"quantity":1}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(47001));
    }

    private PointsRedemptionView view(PointsRedemptionStatus status) {
        return new PointsRedemptionView(1L, "REDEEM-1", 7711L, 31L, 1,
                3_000L, 3_000L, "P-1", "积分商品", "S-1", "默认SKU",
                ProductType.PHYSICAL, null, status,
                status == PointsRedemptionStatus.REVERSED ? "REV-1" : null,
                status == PointsRedemptionStatus.REVERSED ? "履约失败" : null,
                LocalDateTime.now(), LocalDateTime.now());
    }
}
