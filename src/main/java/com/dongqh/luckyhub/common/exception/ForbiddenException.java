package com.dongqh.luckyhub.common.exception;

import com.dongqh.luckyhub.common.enums.CommonErrorCode;

public class ForbiddenException extends BusinessException {

    public ForbiddenException() {
        super(CommonErrorCode.FORBIDDEN);
    }

    public ForbiddenException(String message) {
        super(CommonErrorCode.FORBIDDEN, message);
    }
}
