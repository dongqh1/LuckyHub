package com.dongqh.luckyhub.rbac.service;

import com.dongqh.luckyhub.rbac.vo.PermissionView;

import java.util.List;
import java.util.Set;

public interface RolePermissionService {

    List<PermissionView> assignPermissions(Long roleId, Set<Long> permissionIds);

    List<PermissionView> listRolePermissions(Long roleId);
}
