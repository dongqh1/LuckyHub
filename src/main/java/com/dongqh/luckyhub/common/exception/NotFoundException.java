package com.dongqh.luckyhub.common.exception;

import com.dongqh.luckyhub.common.enums.CommonErrorCode;

public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(CommonErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
