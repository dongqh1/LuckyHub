package com.dongqh.luckyhub.points;

import com.dongqh.luckyhub.auth.model.JwtPayload;
import com.dongqh.luckyhub.auth.security.JwtService;
import com.dongqh.luckyhub.auth.security.SessionService;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.points.dto.AdminPointsAdjustmentCommand;
import com.dongqh.luckyhub.points.dto.PointsLedgerQuery;
import com.dongqh.luckyhub.points.enums.PointsBusinessType;
import com.dongqh.luckyhub.points.enums.PointsDirection;
import com.dongqh.luckyhub.points.enums.PointsErrorCode;
import com.dongqh.luckyhub.points.service.PointsAccountService;
import com.dongqh.luckyhub.points.service.PointsQueryService;
import com.dongqh.luckyhub.points.vo.PointsAccountView;
import com.dongqh.luckyhub.points.vo.PointsLedgerView;
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
class PointsControllerTests {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PointsQueryService queryService;
    @MockitoBean private PointsAccountService accountService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private SessionService sessionService;
    @MockitoBean private UserPermissionService userPermissionService;

    @BeforeEach
    void prepareCaller() {
        when(jwtService.parse("valid-token"))
                .thenReturn(new JwtPayload(7711L, "points-user", "session-1"));
        when(sessionService.isValid("session-1", 7711L)).thenReturn(true);
        when(userPermissionService.findPermissionCodes(7711L)).thenReturn(Set.of(
                PermissionCodes.POINTS_READ, PermissionCodes.POINTS_ADJUST));
    }

    @Test
    void exposesAccountLedgerAndAdminAdjustmentEndpoints() throws Exception {
        when(queryService.getAccount(7711L)).thenReturn(new PointsAccountView(7711L, 500L, null));
        when(queryService.pageLedgers(anyLong(), any())).thenReturn(new PageResponse<>(
                List.of(ledger()), 1, 1, 20, 1));
        when(accountService.adjust(any())).thenReturn(ledger());

        mockMvc.perform(get("/api/points/account")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(7711))
                .andExpect(jsonPath("$.data.balance").value(500));

        mockMvc.perform(get("/api/points/ledgers")
                        .param("page", "1").param("size", "20")
                        .param("businessType", "MANUAL_ADJUSTMENT")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId").value(7711));

        mockMvc.perform(post("/api/admin/points/adjustments")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":88,"delta":500,"businessId":"ADMIN-500","reason":"活动补发"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(500));

        verify(queryService).getAccount(7711L);
        verify(queryService).pageLedgers(org.mockito.ArgumentMatchers.eq(7711L), any(PointsLedgerQuery.class));
        verify(accountService).adjust(new AdminPointsAdjustmentCommand(
                88L, 500L, "ADMIN-500", "活动补发"));
    }

    @Test
    void validatesQueriesAndPreservesStablePointsErrors() throws Exception {
        mockMvc.perform(get("/api/points/ledgers")
                        .param("page", "0")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30000));

        when(accountService.adjust(any()))
                .thenThrow(new BusinessException(PointsErrorCode.POINTS_INSUFFICIENT));
        mockMvc.perform(post("/api/admin/points/adjustments")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":88,"delta":-500,"businessId":"ADMIN-DEBIT","reason":"人工扣减"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(47001));
    }

    private PointsLedgerView ledger() {
        return new PointsLedgerView(1L, 7711L, PointsBusinessType.MANUAL_ADJUSTMENT,
                "ADMIN-500", PointsDirection.CREDIT, 500L, 500L,
                null, "活动补发", LocalDateTime.now());
    }
}
