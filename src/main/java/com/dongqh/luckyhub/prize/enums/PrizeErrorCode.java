package com.dongqh.luckyhub.prize.enums;

import com.dongqh.luckyhub.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PrizeErrorCode implements ErrorCode {

    PRIZE_NOT_FOUND(41001, "奖品不存在", HttpStatus.NOT_FOUND),
    IMAGE_EMPTY(41002, "奖品图片不能为空", HttpStatus.BAD_REQUEST),
    IMAGE_TYPE_UNSUPPORTED(41003, "仅支持 JPEG、PNG 和 WebP 图片", HttpStatus.BAD_REQUEST),
    IMAGE_TOO_LARGE(41004, "奖品图片不能超过5 MiB", HttpStatus.CONTENT_TOO_LARGE),
    OSS_UPLOAD_FAILED(51001, "奖品图片上传失败", HttpStatus.BAD_GATEWAY),
    OSS_CONFIG_UNAVAILABLE(51002, "对象存储尚未配置", HttpStatus.SERVICE_UNAVAILABLE),
    REWARD_BINDING_INVALID(41005, "统一奖励绑定不合法", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    PrizeErrorCode(int code, String message, HttpStatus httpStatus) {
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
