package com.dongqh.luckyhub.common.result;

public record ApiResponse<T>(int code, String message, T data) {

    private static final int SUCCESS_CODE = 0;
    private static final String SUCCESS_MESSAGE = "success";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }
}
