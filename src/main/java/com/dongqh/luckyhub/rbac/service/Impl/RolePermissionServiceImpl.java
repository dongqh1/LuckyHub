package com.dongqh.luckyhub.rbac.service.Impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongqh.luckyhub.common.enums.CommonErrorCode;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.exception.NotFoundException;
import com.dongqh.luckyhub.rbac.cache.PermissionCacheService;
import com.dongqh.luckyhub.rbac.entity.SysPermission;
import com.dongqh.luckyhub.rbac.entity.SysRole;
import com.dongqh.luckyhub.rbac.entity.SysRolePermission;
import com.dongqh.luckyhub.rbac.entity.SysUserRole;
import com.dongqh.luckyhub.rbac.mapper.SysPermissionMapper;
import com.dongqh.luckyhub.rbac.mapper.SysRoleMapper;
import com.dongqh.luckyhub.rbac.mapper.SysRolePermissionMapper;
import com.dongqh.luckyhub.rbac.mapper.SysUserRoleMapper;
import com.dongqh.luckyhub.rbac.service.RolePermissionService;
import com.dongqh.luckyhub.rbac.vo.PermissionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RolePermissionServiceImpl implements RolePermissionService {

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserRoleMapper userRoleMapper;

    private final PermissionCacheService
            permissionCacheService;

    public RolePermissionServiceImpl(
            SysRoleMapper roleMapper,
            SysPermissionMapper permissionMapper,
            SysRolePermissionMapper rolePermissionMapper,
            SysUserRoleMapper userRoleMapper,
            PermissionCacheService permissionCacheService
    ) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper =
                rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.permissionCacheService =
                permissionCacheService;
    }


    @Override
    @Transactional
    public List<PermissionView> assignPermissions(Long roleId, Set<Long> permissionIds) {
        requireEnabledRole(roleId);
        List<SysPermission> permissions = requirePermissions(permissionIds);

        /*
         * 角色权限改变会影响所有拥有该角色的用户，
         * 所以先找出全部受影响用户。
         */
        Set<Long> affectedUserIds =
                userRoleMapper.selectList(
                                Wrappers.<SysUserRole>lambdaQuery()
                                        .eq(
                                                SysUserRole::getRoleId,
                                                roleId
                                        )
                        ).stream()
                        .map(SysUserRole::getUserId)
                        .collect(Collectors.toSet());


        rolePermissionMapper.delete(
                Wrappers.<SysRolePermission>lambdaQuery()
                        .eq(SysRolePermission::getRoleId, roleId)
        );

        for (SysPermission permission : permissions) {
            SysRolePermission relation = new SysRolePermission();
            relation.setRoleId(roleId);
            relation.setPermissionId(permission.getId());
            rolePermissionMapper.insert(relation);
        }

        permissionCacheService
                .evictNowAndAfterCommit(
                        affectedUserIds
                );

        return permissions.stream()
                .map(PermissionView::from)
                .toList();
    }

    @Override
    public List<PermissionView> listRolePermissions(Long roleId) {
        requireRole(roleId);

        Set<Long> permissionIds = rolePermissionMapper.selectList(
                        Wrappers.<SysRolePermission>lambdaQuery()
                                .eq(SysRolePermission::getRoleId, roleId)
                ).stream()
                .map(SysRolePermission::getPermissionId)
                .collect(Collectors.toSet());

        if (permissionIds.isEmpty()) {
            return List.of();
        }

        return permissionMapper.selectList(
                        Wrappers.<SysPermission>lambdaQuery()
                                .in(SysPermission::getId, permissionIds)
                                .orderByAsc(SysPermission::getPermissionCode)
                ).stream()
                .map(PermissionView::from)
                .toList();
    }

    private SysRole requireRole(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new NotFoundException("角色不存在");
        }
        return role;
    }

    private void requireEnabledRole(Long roleId) {
        SysRole role = requireRole(roleId);
        if (!Integer.valueOf(1).equals(role.getStatus())) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_PARAMETER,
                    "角色已禁用，不能分配权限"
            );
        }
    }

    private List<SysPermission> requirePermissions(Set<Long> permissionIds) {
        if (permissionIds.isEmpty()) {
            return List.of();
        }

        List<SysPermission> permissions = permissionMapper.selectList(
                Wrappers.<SysPermission>lambdaQuery()
                        .in(SysPermission::getId, permissionIds)
                        .orderByAsc(SysPermission::getPermissionCode)
        );

        if (permissions.size() != permissionIds.size()) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_PARAMETER,
                    "存在无效的权限"
            );
        }
        return permissions;
    }
}
