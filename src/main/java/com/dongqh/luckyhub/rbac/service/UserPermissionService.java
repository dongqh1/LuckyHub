package com.dongqh.luckyhub.rbac.service;

import com.dongqh.luckyhub.rbac.vo.PermissionView;

import java.util.List;
import java.util.Set;

public interface UserPermissionService {

    List<PermissionView> listEffectivePermissions(Long userId);

    Set<String> findPermissionCodes(Long userId);
}
