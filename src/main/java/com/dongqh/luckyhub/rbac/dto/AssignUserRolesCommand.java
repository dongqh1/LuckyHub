package com.dongqh.luckyhub.rbac.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record AssignUserRolesCommand(
        @NotNull(message = "角色ID集合不能为空")
        @Size(max = 100, message = "一次最多分配100个角色")
        Set<@Positive(message = "角色ID必须大于0") Long> roleIds
) {
}
