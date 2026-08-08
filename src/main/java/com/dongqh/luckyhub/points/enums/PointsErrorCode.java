package com.dongqh.luckyhub.points.enums;

import com.dongqh.luckyhub.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PointsErrorCode implements ErrorCode {
    POINTS_INSUFFICIENT(47001, "积分余额不足", HttpStatus.CONFLICT),
    POINTS_IDEMPOTENCY_CONFLICT(47002, "积分幂等参数冲突", HttpStatus.CONFLICT),
    POINTS_LEDGER_NOT_FOUND(47003, "积分流水不存在", HttpStatus.NOT_FOUND),
    POINTS_REVERSAL_CONFLICT(47004, "积分冲正状态冲突", HttpStatus.CONFLICT),
    POINTS_AMOUNT_INVALID(47005, "积分数量不合法", HttpStatus.BAD_REQUEST),
    REDEMPTION_NOT_FOUND(47006, "积分兑换单不存在", HttpStatus.NOT_FOUND),
    REDEMPTION_SKU_UNAVAILABLE(47007, "SKU不可用于积分兑换", HttpStatus.BAD_REQUEST),
    REDEMPTION_STATE_CONFLICT(47008, "积分兑换单状态冲突", HttpStatus.CONFLICT),
    POINTS_USER_UNAVAILABLE(47009, "积分账户用户不存在或已禁用", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    PointsErrorCode(int code, String message, HttpStatus httpStatus) {
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
