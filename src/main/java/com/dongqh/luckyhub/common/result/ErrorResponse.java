package com.dongqh.luckyhub.common.result;

import com.dongqh.luckyhub.common.enums.ErrorCode;

public record ErrorResponse(
        int code,
        String message,
        Object data,
        long timestamp,
        String requestId
) {

    public static ErrorResponse of(ErrorCode errorCode, String message, String requestId, long timestamp) {
        return new ErrorResponse(errorCode.code(), message, null, timestamp, requestId);
    }

    public static ErrorResponse of(ErrorCode errorCode, String requestId) {
        return of(errorCode, errorCode.message(), requestId, System.currentTimeMillis());
    }
}
