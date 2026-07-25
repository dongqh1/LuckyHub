package com.dongqh.luckyhub.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePermissionCommand(
        @NotBlank(message = "权限编码不能为空")
        @Size(max = 100, message = "权限编码不能超过100个字符")
        @Pattern(
                regexp = "[a-z][a-z0-9_-]*:[a-z][a-z0-9_-]*",
                message = "权限编码格式必须为资源:操作，例如 user:create"
        )
        String permissionCode,

        @NotBlank(message = "权限名称不能为空")
        @Size(max = 100, message = "权限名称不能超过100个字符")
        String permissionName
) {
}
