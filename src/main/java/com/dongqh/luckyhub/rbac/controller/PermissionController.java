package com.dongqh.luckyhub.rbac.controller;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.rbac.dto.CreatePermissionCommand;
import com.dongqh.luckyhub.rbac.service.PermissionService;
import com.dongqh.luckyhub.rbac.vo.PermissionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/permissions")
@Tag(name = "权限管理", description = "RBAC权限管理接口")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建权限")
    @RequirePermission(PermissionCodes.PERMISSION_CREATE)
    public ApiResponse<PermissionView> createPermission(
            @Valid @RequestBody CreatePermissionCommand command
    ) {
        return ApiResponse.success(permissionService.createPermission(command));
    }

    @GetMapping
    @Operation(summary = "查询权限列表")
    @RequirePermission(PermissionCodes.PERMISSION_READ)
    public ApiResponse<List<PermissionView>> listPermissions() {
        return ApiResponse.success(permissionService.listPermissions());
    }

    @GetMapping("/{permissionId}")
    @Operation(summary = "查询权限详情")
    @RequirePermission(PermissionCodes.PERMISSION_READ)
    public ApiResponse<PermissionView> getPermission(
            @Positive(message = "权限ID必须大于0")
            @PathVariable Long permissionId
    ) {
        return ApiResponse.success(permissionService.getPermission(permissionId));
    }
}
