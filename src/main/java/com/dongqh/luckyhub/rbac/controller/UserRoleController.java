package com.dongqh.luckyhub.rbac.controller;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.rbac.dto.AssignUserRolesCommand;
import com.dongqh.luckyhub.rbac.service.UserRoleService;
import com.dongqh.luckyhub.rbac.vo.RoleView;
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
@RequestMapping("/api/admin/users/{userId}/roles")
@Tag(name = "用户角色管理", description = "给用户分配和查询角色")
public class UserRoleController {

    private final UserRoleService userRoleService;

    public UserRoleController(UserRoleService userRoleService) {
        this.userRoleService = userRoleService;
    }

    @PutMapping
    @Operation(summary = "替换用户的全部角色")
    @RequirePermission(PermissionCodes.USER_ROLE_ASSIGN)
    public ApiResponse<List<RoleView>> assignRoles(
            @Positive(message = "用户ID必须大于0") @PathVariable Long userId,
            @Valid @RequestBody AssignUserRolesCommand command
    ) {
        return ApiResponse.success(userRoleService.assignRoles(userId, command.roleIds()));
    }

    @GetMapping
    @Operation(summary = "查询用户角色")
    @RequirePermission(PermissionCodes.USER_ROLE_READ)
    public ApiResponse<List<RoleView>> listUserRoles(
            @Positive(message = "用户ID必须大于0") @PathVariable Long userId
    ) {
        return ApiResponse.success(userRoleService.listUserRoles(userId));
    }
}
