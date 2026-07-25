package com.dongqh.luckyhub.auth.service.Impl;

import com.dongqh.luckyhub.auth.dto.LoginCommand;
import com.dongqh.luckyhub.auth.model.AuthenticatedUser;
import com.dongqh.luckyhub.auth.security.JwtService;
import com.dongqh.luckyhub.auth.security.SessionService;
import com.dongqh.luckyhub.auth.service.AuthenticationService;
import com.dongqh.luckyhub.auth.service.LoginService;
import com.dongqh.luckyhub.auth.vo.LoginView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class LoginServiceImpl implements LoginService {

    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private SessionService sessionService;

    @Override
    public LoginView login(LoginCommand command) {
        // 验证是否可以登录
        AuthenticatedUser  user =
                authenticationService.authenticate(command);
        //生成sessionId
        String sessionId = jwtService.newSessionId();
        //将SessionId保存到redis
        sessionService.create(sessionId, user.userId(), jwtService.getExpireSeconds());
        //生成JWT
        String token = jwtService.generate(user, sessionId);
        //封装成LoginView




        return new LoginView(
                user.userId(),
                user.username(),
                user.nickname(),
                token,
                "Bearer",
                jwtService.getExpireSeconds()
        );
    }

    @Override
    public void logout(String sessionId) {
        sessionService.remove(sessionId);
    }


}
