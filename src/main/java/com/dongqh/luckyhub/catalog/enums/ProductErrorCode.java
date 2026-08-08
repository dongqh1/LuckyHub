package com.dongqh.luckyhub.catalog.enums;

import com.dongqh.luckyhub.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ProductErrorCode implements ErrorCode {
    PRODUCT_NOT_FOUND(44001, "商品不存在", HttpStatus.NOT_FOUND),
    PRODUCT_CODE_DUPLICATE(44002, "商品编码已存在", HttpStatus.CONFLICT),
    SKU_CODE_DUPLICATE(44003, "SKU编码已存在", HttpStatus.CONFLICT),
    PRODUCT_CONFIG_INVALID(44004, "商品配置不合法", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ProductErrorCode(int code, String message, HttpStatus httpStatus) {
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
