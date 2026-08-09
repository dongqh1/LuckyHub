package com.dongqh.luckyhub.shipping.enums;

import com.dongqh.luckyhub.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ShippingErrorCode implements ErrorCode {
    ADDRESS_NOT_FOUND(53001, "收货地址不存在", HttpStatus.NOT_FOUND),
    ADDRESS_ACCESS_DENIED(53002, "无权访问该收货地址", HttpStatus.FORBIDDEN),
    ADDRESS_INVALID(53003, "收货地址参数不合法", HttpStatus.BAD_REQUEST),
    ADDRESS_STATE_CONFLICT(53004, "收货地址状态冲突", HttpStatus.CONFLICT),
    SHIPPING_NOT_FOUND(53005, "发货单不存在", HttpStatus.NOT_FOUND),
    SHIPPING_IDEMPOTENCY_CONFLICT(53006, "发货请求幂等参数冲突", HttpStatus.CONFLICT),
    SHIPPING_STATE_CONFLICT(53007, "发货单状态冲突", HttpStatus.CONFLICT),
    CLAIM_NOT_ALLOWED(53008, "当前权益不可领取", HttpStatus.CONFLICT),
    CLAIM_EXPIRED(53009, "实物权益已超过领取期限", HttpStatus.CONFLICT),
    CALLBACK_SIGNATURE_INVALID(53010, "物流回调验签失败", HttpStatus.UNAUTHORIZED),
    CALLBACK_REPLAYED(53011, "物流回调已处理", HttpStatus.CONFLICT),
    SHIPPING_REQUEST_INVALID(53012, "物流请求参数不合法", HttpStatus.BAD_REQUEST);

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
