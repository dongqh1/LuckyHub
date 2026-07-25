package com.dongqh.luckyhub.rbac.vo;

import com.dongqh.luckyhub.rbac.entity.SysRole;

import java.time.LocalDateTime;

public record RoleView(Long id,
                       String roleCode,
                       String roleName,
                       Integer status,
                       LocalDateTime createdAt) {
    public static RoleView from(SysRole role) {
        return new RoleView(
                role.getId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getStatus(),
                role.getCreatedAt()
        );
}
}
