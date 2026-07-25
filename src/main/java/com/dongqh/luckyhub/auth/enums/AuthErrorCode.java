package com.dongqh.luckyhub.auth.enums;

import com.dongqh.luckyhub.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {

    INVALID_CREDENTIALS(
            20002,
            "用户名或密码错误",
            HttpStatus.UNAUTHORIZED
    ),

    USER_DISABLED(
            20003,
            "用户已被禁用",
            HttpStatus.FORBIDDEN
    ),

    INVALID_TOKEN(
            20004,
            "登录凭证无效或已过期",
            HttpStatus.UNAUTHORIZED
    );

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    AuthErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
