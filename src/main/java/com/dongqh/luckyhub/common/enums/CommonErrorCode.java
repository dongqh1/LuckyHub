package com.dongqh.luckyhub.common.enums;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {

    SYSTEM_ERROR(10000, "系统繁忙，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE(10001, "服务暂时不可用，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE),
    UNAUTHORIZED(20000, "请先登录", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(20001, "无权执行此操作", HttpStatus.FORBIDDEN),
    INVALID_PARAMETER(30000, "参数校验失败", HttpStatus.BAD_REQUEST),
    MALFORMED_JSON(30001, "请求内容格式错误", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(30002, "请求的资源不存在", HttpStatus.NOT_FOUND),
    DATA_CONFLICT(30003, "数据已存在或状态冲突", HttpStatus.CONFLICT);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    CommonErrorCode(int code, String message, HttpStatus httpStatus) {
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
