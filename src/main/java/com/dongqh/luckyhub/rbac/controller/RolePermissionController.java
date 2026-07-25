package com.dongqh.luckyhub.rbac.controller;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.rbac.dto.AssignRolePermissionsCommand;
import com.dongqh.luckyhub.rbac.service.RolePermissionService;
import com.dongqh.luckyhub.rbac.vo.PermissionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/roles/{roleId}/permissions")
@Tag(name = "角色权限管理", description = "给角色分配和查询权限")
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;

    public RolePermissionController(RolePermissionService rolePermissionService) {
        this.rolePermissionService = rolePermissionService;
    }

    @PutMapping
    @Operation(summary = "替换角色的全部权限")
    @RequirePermission(
            PermissionCodes.ROLE_PERMISSION_ASSIGN
    )
    public ApiResponse<List<PermissionView>> assignPermissions(
            @Positive(message = "角色ID必须大于0") @PathVariable Long roleId,
            @Valid @RequestBody AssignRolePermissionsCommand command
    ) {
        return ApiResponse.success(
                rolePermissionService.assignPermissions(roleId, command.permissionIds())
        );
    }

    @GetMapping
    @Operation(summary = "查询角色权限")
    @RequirePermission(
            PermissionCodes.ROLE_PERMISSION_READ
    )
    public ApiResponse<List<PermissionView>> listRolePermissions(
            @Positive(message = "角色ID必须大于0") @PathVariable Long roleId
    ) {
        return ApiResponse.success(rolePermissionService.listRolePermissions(roleId));
    }
}
