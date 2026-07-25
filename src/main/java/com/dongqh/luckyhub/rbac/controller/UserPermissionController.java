package com.dongqh.luckyhub.rbac.controller;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.rbac.service.UserPermissionService;
import com.dongqh.luckyhub.rbac.vo.PermissionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users/{userId}/permissions")
@Tag(name = "用户权限查询", description = "查询用户通过角色获得的最终权限")
public class UserPermissionController {

    private final UserPermissionService userPermissionService;

    public UserPermissionController(UserPermissionService userPermissionService) {
        this.userPermissionService = userPermissionService;
    }

    @GetMapping
    @Operation(summary = "查询用户最终权限")
    @RequirePermission(
            PermissionCodes.USER_PERMISSION_READ
    )
    public ApiResponse<List<PermissionView>> listEffectivePermissions(
            @Positive(message = "用户ID必须大于0") @PathVariable Long userId
    ) {
        return ApiResponse.success(
                userPermissionService.listEffectivePermissions(userId)
        );
    }
}
