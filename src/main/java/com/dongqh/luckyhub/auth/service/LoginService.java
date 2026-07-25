package com.dongqh.luckyhub.auth.service;

import com.dongqh.luckyhub.auth.dto.LoginCommand;
import com.dongqh.luckyhub.auth.vo.LoginView;
import jakarta.validation.Valid;

public interface LoginService {
    LoginView login(@Valid LoginCommand command);

    void logout(String sessionId);
}
