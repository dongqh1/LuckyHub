package com.dongqh.luckyhub.rbac.service;

import com.dongqh.luckyhub.rbac.vo.RoleView;

import java.util.List;
import java.util.Set;

public interface UserRoleService {

    List<RoleView> assignRoles(Long userId, Set<Long> roleIds);

    List<RoleView> listUserRoles(Long userId);
}
