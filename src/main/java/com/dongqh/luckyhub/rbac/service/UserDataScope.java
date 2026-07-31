package com.dongqh.luckyhub.rbac.service;

public record UserDataScope(boolean all, Long userId) {

    public static UserDataScope allUsers() {
        return new UserDataScope(true, null);
    }

    public static UserDataScope one(long userId) {
        return new UserDataScope(false, userId);
    }
}
