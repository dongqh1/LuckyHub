package com.dongqh.luckyhub.auth.service;

import com.dongqh.luckyhub.auth.dto.LoginCommand;
import com.dongqh.luckyhub.auth.model.AuthenticatedUser;

public interface AuthenticationService {

    AuthenticatedUser authenticate(LoginCommand command);
}
