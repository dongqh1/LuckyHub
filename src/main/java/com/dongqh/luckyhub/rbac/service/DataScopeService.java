package com.dongqh.luckyhub.rbac.service;

public interface DataScopeService {

    boolean hasPermission(long userId, String code);

    UserDataScope resolveUserScope(
            Long requestedUserId,
            String readAllPermission
    );
}
