package com.dongqh.luckyhub.rbac.controller;


import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.rbac.dto.CreateRoleCommand;
import com.dongqh.luckyhub.rbac.service.RoleService;
import com.dongqh.luckyhub.rbac.vo.RoleView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/roles")
@Tag(name = "角色管理", description = "角色管理接口")
public class RoleController {

    @Autowired
    private RoleService roleService;

    @PostMapping
    //创建角色
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建角色")
    @RequirePermission(PermissionCodes.ROLE_CREATE)
    public ApiResponse createRole(
            @Valid @RequestBody CreateRoleCommand createRoleCommand
            ){
        RoleView rv = roleService.createRole(createRoleCommand);

        return ApiResponse.success(rv);
    }

    @GetMapping()
    //查询角色
    @Operation(summary = "查询角色列表")
    @RequirePermission(PermissionCodes.ROLE_READ)
    public ApiResponse listRole(){

        return ApiResponse.success(roleService.listRole());

    }

    @GetMapping("/{roleId}")
    @Operation(summary = "查询角色详情")
    @RequirePermission(PermissionCodes.ROLE_READ)
    public ApiResponse getById(
            @Positive(message = "角色ID必须大于0")
            @PathVariable Long roleId){

        return ApiResponse.success(roleService.getById(roleId));
    }

}
