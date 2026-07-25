package com.dongqh.luckyhub.rbac.service;

import com.dongqh.luckyhub.auth.vo.CurrentUserView;
import com.dongqh.luckyhub.rbac.dto.CreateUserCommand;
import com.dongqh.luckyhub.rbac.vo.UserView;
import jakarta.validation.Valid;

import java.util.List;

public interface UserService {
    /**
     * 创建用户
     *
     * @param command
     * @return
     */
    UserView createUser(@Valid CreateUserCommand command);


    CurrentUserView getAllById(Long id);

    List<UserView> listUser();
}
