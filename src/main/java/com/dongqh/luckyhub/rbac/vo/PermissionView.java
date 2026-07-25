package com.dongqh.luckyhub.rbac.vo;

import com.dongqh.luckyhub.rbac.entity.SysPermission;

import java.time.LocalDateTime;

public record PermissionView(
        Long id,
        String permissionCode,
        String permissionName,
        LocalDateTime createdAt
) {
    public static PermissionView from(SysPermission permission) {
        return new PermissionView(
                permission.getId(),
                permission.getPermissionCode(),
                permission.getPermissionName(),
                permission.getCreatedAt()
        );
    }
}
