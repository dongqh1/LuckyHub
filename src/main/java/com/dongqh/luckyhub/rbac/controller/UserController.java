package com.dongqh.luckyhub.rbac.controller;


import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.rbac.dto.CreateUserCommand;
import com.dongqh.luckyhub.rbac.service.UserService;
import com.dongqh.luckyhub.rbac.vo.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "用户管理", description = "用户管理接口")
public class UserController {

    @Autowired
    private UserService userService;


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建用户")
    @RequirePermission(PermissionCodes.USER_CREATE)
    public ApiResponse<UserView> createUser(
            @Valid @RequestBody CreateUserCommand command
    ) {
        UserView userView = userService.createUser(command);
        return ApiResponse.success(userView);
    }

    @GetMapping
    @Operation(summary = "查询用户")
    @RequirePermission(PermissionCodes.USER_READ)
    public ApiResponse listUser(){

        return ApiResponse.success(userService.listUser());
    }

}
