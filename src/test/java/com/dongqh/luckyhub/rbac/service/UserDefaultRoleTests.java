package com.dongqh.luckyhub.rbac.service;

import com.dongqh.luckyhub.auth.security.PasswordService;
import com.dongqh.luckyhub.common.enums.CommonErrorCode;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.rbac.dto.CreateUserCommand;
import com.dongqh.luckyhub.rbac.entity.SysRole;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import com.dongqh.luckyhub.rbac.entity.SysUserRole;
import com.dongqh.luckyhub.rbac.mapper.SysRoleMapper;
import com.dongqh.luckyhub.rbac.mapper.SysUserMapper;
import com.dongqh.luckyhub.rbac.mapper.SysUserRoleMapper;
import com.dongqh.luckyhub.rbac.service.Impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDefaultRoleTests {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private PasswordService passwordService;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private UserPermissionService userPermissionService;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void assignsEnabledUserRoleAfterCreatingUser() {
        when(userMapper.exists(any())).thenReturn(false);
        when(passwordService.hash("Password1!")).thenReturn("encoded");
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            invocation.<SysUser>getArgument(0).setId(41L);
            return 1;
        });
        SysRole userRole = new SysRole();
        userRole.setId(7L);
        userRole.setRoleCode("USER");
        userRole.setStatus(1);
        when(roleMapper.selectOne(any())).thenReturn(userRole);

        userService.createUser(new CreateUserCommand(
                "member01", "Password1!", "会员"
        ));

        ArgumentCaptor<SysUserRole> relationCaptor =
                ArgumentCaptor.forClass(SysUserRole.class);
        verify(userRoleMapper).insert(relationCaptor.capture());
        assertThat(relationCaptor.getValue().getUserId()).isEqualTo(41L);
        assertThat(relationCaptor.getValue().getRoleId()).isEqualTo(7L);

        InOrder order = inOrder(userMapper, roleMapper, userRoleMapper);
        order.verify(userMapper).insert(any(SysUser.class));
        order.verify(roleMapper).selectOne(any());
        order.verify(userRoleMapper).insert(any(SysUserRole.class));
    }

    @Test
    void missingOrDisabledUserRoleFailsConfigurationAfterUserInsert() {
        when(userMapper.exists(any())).thenReturn(false);
        when(passwordService.hash("Password1!")).thenReturn("encoded");
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            invocation.<SysUser>getArgument(0).setId(42L);
            return 1;
        });
        when(roleMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> userService.createUser(new CreateUserCommand(
                "member02", "Password1!", null
        )))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(CommonErrorCode.SYSTEM_ERROR);
                    assertThat(exception.getMessage()).contains("USER");
                });

        verify(userMapper).insert(any(SysUser.class));
        verify(userRoleMapper, never()).insert(any(SysUserRole.class));
    }

    @Test
    void userAndDefaultRoleAssignmentShareTransactionalMethod() throws Exception {
        Transactional transactional = UserServiceImpl.class
                .getMethod("createUser", CreateUserCommand.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
    }
}
