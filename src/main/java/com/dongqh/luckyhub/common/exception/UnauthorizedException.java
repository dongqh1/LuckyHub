package com.dongqh.luckyhub.common.exception;

import com.dongqh.luckyhub.common.enums.CommonErrorCode;

public class UnauthorizedException extends BusinessException {

    public UnauthorizedException() {
        super(CommonErrorCode.UNAUTHORIZED);
    }

    public UnauthorizedException(String message) {
        super(CommonErrorCode.UNAUTHORIZED, message);
    }
}
