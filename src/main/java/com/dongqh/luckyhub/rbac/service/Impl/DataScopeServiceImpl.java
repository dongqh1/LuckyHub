package com.dongqh.luckyhub.rbac.service.Impl;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.common.exception.ForbiddenException;
import com.dongqh.luckyhub.rbac.service.DataScopeService;
import com.dongqh.luckyhub.rbac.service.UserDataScope;
import com.dongqh.luckyhub.rbac.service.UserPermissionService;
import org.springframework.stereotype.Service;

@Service
public class DataScopeServiceImpl implements DataScopeService {

    private final UserPermissionService userPermissionService;

    public DataScopeServiceImpl(
            UserPermissionService userPermissionService
    ) {
        this.userPermissionService = userPermissionService;
    }

    @Override
    public boolean hasPermission(long userId, String code) {
        return userPermissionService.findPermissionCodes(userId)
                .contains(code);
    }

    @Override
    public UserDataScope resolveUserScope(
            Long requestedUserId,
            String readAllPermission
    ) {
        long currentUserId = LoginContext.require().userId();
        if (hasPermission(currentUserId, readAllPermission)) {
            return requestedUserId == null
                    ? UserDataScope.allUsers()
                    : UserDataScope.one(requestedUserId);
        }

        if (requestedUserId != null
                && requestedUserId.longValue() != currentUserId) {
            throw new ForbiddenException("无权查询其他用户的数据");
        }
        return UserDataScope.one(currentUserId);
    }
}
