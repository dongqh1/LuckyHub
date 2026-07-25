package com.dongqh.luckyhub.auth.controller;


import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.auth.dto.LoginCommand;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.auth.service.LoginService;
import com.dongqh.luckyhub.auth.vo.CurrentUserView;
import com.dongqh.luckyhub.auth.vo.LoginView;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.rbac.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "登录关联", description = "登录拦截认证接口")
public class AuthController {

    @Autowired
    private LoginService loginService;
    @Autowired
    private UserService userService;

    @PostMapping("/login")
    @Operation(summary = "登录")
    public ApiResponse<LoginView> login( @Valid @RequestBody
                                             LoginCommand command) {

        return ApiResponse.success(loginService.login(command));
    }

    @PostMapping("/logout")
    @Operation(summary = "登出")
    public ApiResponse logout() {
        LoginPrincipal user = LoginContext.require();
        loginService.logout(user.sessionId());
        return ApiResponse.success();
    }

    @GetMapping("/me")
    @Operation(summary = "主页")
    public ApiResponse me(){
        LoginPrincipal principal = LoginContext.require();
        Long id = principal.userId();

        CurrentUserView view = userService.getAllById(id);

        return ApiResponse.success(view);
    }



}
