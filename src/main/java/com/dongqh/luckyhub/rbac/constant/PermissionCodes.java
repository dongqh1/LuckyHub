package com.dongqh.luckyhub.rbac.constant;

public final class PermissionCodes {

    public static final String USER_CREATE =
            "user:create";

    public static final String USER_READ =
            "user:read";

    public static final String ROLE_CREATE =
            "role:create";

    public static final String ROLE_READ =
            "role:read";

    public static final String PERMISSION_CREATE =
            "permission:create";

    public static final String PERMISSION_READ =
            "permission:read";

    public static final String USER_ROLE_ASSIGN =
            "user-role:assign";

    public static final String USER_ROLE_READ =
            "user-role:read";

    public static final String ROLE_PERMISSION_ASSIGN =
            "role-permission:assign";

    public static final String ROLE_PERMISSION_READ =
            "role-permission:read";

    public static final String USER_PERMISSION_READ =
            "user-permission:read";

    private PermissionCodes() {
    }
}
