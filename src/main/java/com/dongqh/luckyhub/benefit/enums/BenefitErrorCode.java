package com.dongqh.luckyhub.benefit.enums;

import com.dongqh.luckyhub.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public enum BenefitErrorCode implements ErrorCode {

    BENEFIT_NOT_FOUND(44001, "权益不存在", HttpStatus.NOT_FOUND),
    BENEFIT_ACCESS_DENIED(44002, "无权访问该权益", HttpStatus.FORBIDDEN),
    BENEFIT_STATE_CONFLICT(44003, "当前权益状态不允许执行该操作", HttpStatus.CONFLICT),
    BENEFIT_GRANT_FAILED(54001, "权益发放失败", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    BenefitErrorCode(int code, String message, HttpStatus httpStatus) {
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
