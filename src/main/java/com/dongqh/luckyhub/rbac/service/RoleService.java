package com.dongqh.luckyhub.rbac.service;

import com.dongqh.luckyhub.rbac.dto.CreateRoleCommand;
import com.dongqh.luckyhub.rbac.vo.RoleView;
import jakarta.validation.Valid;

import java.util.List;

public interface RoleService {
    RoleView createRole(@Valid CreateRoleCommand createRoleCommand);

    List<RoleView> listRole();

    RoleView getById(Long roleId);
}
