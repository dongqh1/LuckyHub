package com.dongqh.luckyhub.rbac.service;

import com.dongqh.luckyhub.rbac.dto.CreatePermissionCommand;
import com.dongqh.luckyhub.rbac.vo.PermissionView;

import java.util.List;

public interface PermissionService {

    PermissionView createPermission(CreatePermissionCommand command);

    List<PermissionView> listPermissions();

    PermissionView getPermission(Long permissionId);
}
