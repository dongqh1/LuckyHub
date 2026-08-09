package com.dongqh.luckyhub.drawchance.enums;

import com.dongqh.luckyhub.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public enum DrawChanceErrorCode implements ErrorCode {
    INVALID_REQUEST(46001, "抽奖次数请求不合法", HttpStatus.BAD_REQUEST),
    USER_NOT_AVAILABLE(46002, "用户不存在或已禁用", HttpStatus.BAD_REQUEST),
    IDENTITY_CONFLICT(46003, "抽奖次数请求身份冲突", HttpStatus.CONFLICT),
    RESERVATION_NOT_FOUND(46004, "抽奖次数预留不存在", HttpStatus.NOT_FOUND),
    TERMINAL_STATE_CONFLICT(46005, "抽奖次数预留终态冲突", HttpStatus.CONFLICT),
    BALANCE_INVALID(46006, "抽奖次数账户余额异常", HttpStatus.CONFLICT);

    private final int code;
    private final String message;
    private final HttpStatus status;

    DrawChanceErrorCode(int code, String message, HttpStatus status) {
        this.code = code; this.message = message; this.status = status;
    }
    public int code() { return code; }
    public String message() { return message; }
    public HttpStatus httpStatus() { return status; }
}
