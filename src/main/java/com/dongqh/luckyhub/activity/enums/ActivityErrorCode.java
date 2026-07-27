package com.dongqh.luckyhub.activity.enums;

import com.dongqh.luckyhub.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ActivityErrorCode implements ErrorCode {

    ACTIVITY_NOT_FOUND(42001, "活动不存在", HttpStatus.NOT_FOUND),
    ACTIVITY_TIME_INVALID(42002, "活动结束时间必须晚于开始时间", HttpStatus.BAD_REQUEST),
    ACTIVITY_STATE_CONFLICT(42003, "当前活动状态不允许执行该操作", HttpStatus.CONFLICT),
    ACTIVITY_PRIZE_NOT_FOUND(42004, "活动奖品不存在", HttpStatus.NOT_FOUND),
    ACTIVITY_PRIZE_DUPLICATE(42005, "活动已关联该奖品", HttpStatus.CONFLICT),
    ACTIVITY_HAS_NO_PRIZE(42006, "活动至少需要配置一个奖品", HttpStatus.CONFLICT),
    ACTIVITY_HAS_DISABLED_PRIZE(42007, "活动包含已禁用奖品", HttpStatus.CONFLICT),
    ACTIVITY_HAS_NO_AVAILABLE_STOCK(42008, "活动没有可用奖品库存", HttpStatus.CONFLICT),
    ACTIVITY_STOCK_BELOW_CONSUMED(42009, "奖品总库存不能小于已消耗库存", HttpStatus.CONFLICT);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ActivityErrorCode(int code, String message, HttpStatus httpStatus) {
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
