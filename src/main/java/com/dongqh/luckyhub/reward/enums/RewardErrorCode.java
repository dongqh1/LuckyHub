package com.dongqh.luckyhub.reward.enums;

import com.dongqh.luckyhub.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public enum RewardErrorCode implements ErrorCode {
    REWARD_NOT_FOUND(45001, "奖励定义不存在", HttpStatus.NOT_FOUND),
    REWARD_CODE_DUPLICATE(45002, "奖励编码已存在", HttpStatus.CONFLICT),
    REWARD_TARGET_INVALID(45003, "奖励目标不合法", HttpStatus.BAD_REQUEST),
    REWARD_CONFIG_INVALID(45004, "奖励配置不合法", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    RewardErrorCode(int code, String message, HttpStatus httpStatus) {
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
