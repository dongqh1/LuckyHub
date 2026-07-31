package com.dongqh.luckyhub.rbac.service;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.common.exception.ForbiddenException;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.rbac.service.Impl.DataScopeServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataScopeServiceTests {

    private UserPermissionService userPermissionService;
    private DataScopeService dataScopeService;

    @BeforeEach
    void setUp() {
        userPermissionService = mock(UserPermissionService.class);
        dataScopeService = new DataScopeServiceImpl(userPermissionService);
        LoginContext.set(new LoginPrincipal(11L, "member", "session"));
    }

    @AfterEach
    void tearDown() {
        LoginContext.clear();
    }

    @Test
    void hasPermissionUsesExistingUserPermissionService() {
        when(userPermissionService.findPermissionCodes(11L))
                .thenReturn(Set.of(PermissionCodes.BENEFIT_READ_ALL));

        assertThat(dataScopeService.hasPermission(
                11L, PermissionCodes.BENEFIT_READ_ALL
        )).isTrue();
        verify(userPermissionService).findPermissionCodes(11L);
    }

    @Test
    void lotteryPermissionConstantsExactlyMatchV5Codes() {
        assertThat(List.of(
                PermissionCodes.LOTTERY_ACTIVITY_READ,
                PermissionCodes.LOTTERY_DRAW,
                PermissionCodes.LOTTERY_DRAW_READ,
                PermissionCodes.LOTTERY_RECORD_READ,
                PermissionCodes.BENEFIT_READ,
                PermissionCodes.LOTTERY_ORDER_READ_ALL,
                PermissionCodes.LOTTERY_DRAW_READ_ALL,
                PermissionCodes.LOTTERY_RECORD_READ_ALL,
                PermissionCodes.BENEFIT_READ_ALL
        )).containsExactly(
                "lottery:activity:read",
                "lottery:draw",
                "lottery:draw:read",
                "lottery:record:read",
                "benefit:read",
                "lottery:order:read:all",
                "lottery:draw:read:all",
                "lottery:record:read:all",
                "benefit:read:all"
        );
    }

    @Test
    void ordinaryCallerWithoutRequestedUserIsScopedToSelf() {
        when(userPermissionService.findPermissionCodes(11L)).thenReturn(Set.of());

        UserDataScope scope = dataScopeService.resolveUserScope(
                null, PermissionCodes.BENEFIT_READ_ALL
        );

        assertThat(scope.all()).isFalse();
        assertThat(scope.userId()).isEqualTo(11L);
    }

    @Test
    void ordinaryCallerMayExplicitlyRequestSelf() {
        when(userPermissionService.findPermissionCodes(11L)).thenReturn(Set.of());

        assertThat(dataScopeService.resolveUserScope(
                11L, PermissionCodes.BENEFIT_READ_ALL
        )).isEqualTo(UserDataScope.one(11L));
    }

    @Test
    void ordinaryCallerExplicitlyRequestingAnotherUserIsForbidden() {
        when(userPermissionService.findPermissionCodes(11L)).thenReturn(Set.of());

        assertThatThrownBy(() -> dataScopeService.resolveUserScope(
                12L, PermissionCodes.BENEFIT_READ_ALL
        )).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void readAllCallerWithoutRequestedUserReceivesAllUsersScope() {
        when(userPermissionService.findPermissionCodes(11L))
                .thenReturn(Set.of(PermissionCodes.BENEFIT_READ_ALL));

        assertThat(dataScopeService.resolveUserScope(
                null, PermissionCodes.BENEFIT_READ_ALL
        )).isEqualTo(UserDataScope.allUsers());
    }

    @Test
    void readAllCallerMayRequestOneUser() {
        when(userPermissionService.findPermissionCodes(11L))
                .thenReturn(Set.of(PermissionCodes.BENEFIT_READ_ALL));

        assertThat(dataScopeService.resolveUserScope(
                12L, PermissionCodes.BENEFIT_READ_ALL
        )).isEqualTo(UserDataScope.one(12L));
    }
}
