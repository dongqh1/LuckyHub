package com.dongqh.luckyhub.auth.context;

import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.common.exception.UnauthorizedException;
import lombok.val;

//ThreadLocal
public final class LoginContext {

    private static final
    ThreadLocal<LoginPrincipal> HOLDER = new ThreadLocal<>();

    private LoginContext(){}
    public static void set(LoginPrincipal principal) {
        HOLDER.set(principal);
    }

    public static LoginPrincipal get() {
        return HOLDER.get();
    }

    public static LoginPrincipal require() {
        LoginPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new UnauthorizedException();
        }
        return principal;
    }

    public static void clear() {
        HOLDER.remove();
    }


}
