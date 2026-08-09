package com.dongqh.luckyhub.shipping.enums;

import com.dongqh.luckyhub.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ShippingErrorCode implements ErrorCode {
    ADDRESS_NOT_FOUND(55001, "收货地址不存在", HttpStatus.NOT_FOUND),
    ADDRESS_ACCESS_DENIED(55002, "无权访问该收货地址", HttpStatus.FORBIDDEN),
    ADDRESS_INVALID(55003, "收货地址参数不合法", HttpStatus.BAD_REQUEST),
    ADDRESS_STATE_CONFLICT(55004, "收货地址状态冲突", HttpStatus.CONFLICT),
    SHIPPING_NOT_FOUND(55005, "发货单不存在", HttpStatus.NOT_FOUND),
    SHIPPING_IDEMPOTENCY_CONFLICT(55006, "发货请求幂等参数冲突", HttpStatus.CONFLICT),
    SHIPPING_STATE_CONFLICT(55007, "发货单状态冲突", HttpStatus.CONFLICT),
    CLAIM_NOT_ALLOWED(55008, "当前权益不可领取", HttpStatus.CONFLICT),
    CLAIM_EXPIRED(55009, "实物权益已超过领取期限", HttpStatus.CONFLICT),
    CALLBACK_SIGNATURE_INVALID(55010, "物流回调验签失败", HttpStatus.UNAUTHORIZED),
    CALLBACK_REPLAYED(55011, "物流回调已处理", HttpStatus.CONFLICT),
    SHIPPING_REQUEST_INVALID(55012, "物流请求参数不合法", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ShippingErrorCode(int code, String message, HttpStatus httpStatus) {
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
