package com.dongqh.luckyhub.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoleCommand(

        @NotBlank(message = "角色编码不能为空")
        @Size(max = 50, message = "角色编码不能超过50个字符")
        @Pattern(
                regexp = "[A-Z][A-Z0-9_]{0,49}",
                message = "角色编码只能包含大写字母、数字和下划线，并且必须以大写字母开头"
        )
        String roleCode,

        @NotBlank(message = "角色名称不能为空")
        @Size(max = 50, message = "角色名称不能超过50个字符")
        String roleName
) {
}
