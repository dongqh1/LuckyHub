package com.dongqh.luckyhub.rbac.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record AssignRolePermissionsCommand(
        @NotNull(message = "权限ID集合不能为空")
        @Size(max = 500, message = "一次最多分配500个权限")
        Set<@Positive(message = "权限ID必须大于0") Long> permissionIds
) {
}
