package com.dongqh.luckyhub.rbac.service.Impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongqh.luckyhub.common.enums.CommonErrorCode;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.exception.NotFoundException;
import com.dongqh.luckyhub.rbac.cache.PermissionCacheService;
import com.dongqh.luckyhub.rbac.entity.SysRole;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import com.dongqh.luckyhub.rbac.entity.SysUserRole;
import com.dongqh.luckyhub.rbac.mapper.SysRoleMapper;
import com.dongqh.luckyhub.rbac.mapper.SysUserMapper;
import com.dongqh.luckyhub.rbac.mapper.SysUserRoleMapper;
import com.dongqh.luckyhub.rbac.service.UserRoleService;
import com.dongqh.luckyhub.rbac.vo.RoleView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserRoleServiceImpl implements UserRoleService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PermissionCacheService permissionCacheService;


    public UserRoleServiceImpl(
            SysUserMapper userMapper,
            SysRoleMapper roleMapper,
            SysUserRoleMapper userRoleMapper,
            PermissionCacheService permissionCacheService
    ) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.permissionCacheService = permissionCacheService;
    }

    @Override
    @Transactional
    public List<RoleView> assignRoles(Long userId, Set<Long> roleIds) {
        requireUser(userId);
        List<SysRole> roles = requireEnabledRoles(roleIds);

        userRoleMapper.delete(
                Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getUserId, userId)
        );

        for (SysRole role : roles) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(role.getId());
            userRoleMapper.insert(relation);
        }

        permissionCacheService.evictNowAndAfterCommit(
                Set.of(userId)
        );

        return roles.stream()
                .map(RoleView::from)
                .toList();
    }

    @Override
    public List<RoleView> listUserRoles(Long userId) {
        requireUser(userId);

        Set<Long> roleIds = userRoleMapper.selectList(
                        Wrappers.<SysUserRole>lambdaQuery()
                                .eq(SysUserRole::getUserId, userId)
                ).stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toSet());

        if (roleIds.isEmpty()) {
            return List.of();
        }

        return roleMapper.selectList(
                        Wrappers.<SysRole>lambdaQuery()
                                .in(SysRole::getId, roleIds)
                                .orderByAsc(SysRole::getRoleCode)
                ).stream()
                .map(RoleView::from)
                .toList();
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        return user;
    }

    private List<SysRole> requireEnabledRoles(Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }

        List<SysRole> roles = roleMapper.selectList(
                Wrappers.<SysRole>lambdaQuery()
                        .in(SysRole::getId, roleIds)
                        .eq(SysRole::getStatus, 1)
                        .orderByAsc(SysRole::getRoleCode)
        );

        if (roles.size() != roleIds.size()) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_PARAMETER,
                    "存在无效或已禁用的角色"
            );
        }
        return roles;
    }
}
