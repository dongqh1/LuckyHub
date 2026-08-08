package com.dongqh.luckyhub.payment.enums;
import com.dongqh.luckyhub.common.enums.ErrorCode; import org.springframework.http.HttpStatus;
public enum PaymentErrorCode implements ErrorCode {
 PAYMENT_NOT_FOUND(51001,"支付单不存在",HttpStatus.NOT_FOUND), PAYMENT_DUPLICATE(51002,"支付单号参数冲突",HttpStatus.CONFLICT),
 SIGNATURE_INVALID(51003,"模拟支付签名无效",HttpStatus.UNAUTHORIZED), PAYMENT_STATE_CONFLICT(51004,"支付单状态冲突",HttpStatus.CONFLICT),
 AMOUNT_MISMATCH(51005,"支付金额不匹配",HttpStatus.CONFLICT), ORDER_NOT_PAYABLE(51006,"订单当前不可支付",HttpStatus.CONFLICT);
 private final int code;private final String message;private final HttpStatus status;PaymentErrorCode(int c,String m,HttpStatus s){code=c;message=m;status=s;}public int code(){return code;}public String message(){return message;}public HttpStatus httpStatus(){return status;}
}
