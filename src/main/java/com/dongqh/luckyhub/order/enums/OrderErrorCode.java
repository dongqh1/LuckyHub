package com.dongqh.luckyhub.order.enums;
import com.dongqh.luckyhub.common.enums.ErrorCode; import org.springframework.http.HttpStatus;
public enum OrderErrorCode implements ErrorCode {
 ORDER_NOT_FOUND(50001,"现金订单不存在",HttpStatus.NOT_FOUND), IDEMPOTENCY_CONFLICT(50002,"订单幂等参数冲突",HttpStatus.CONFLICT),
 SKU_UNAVAILABLE(50003,"SKU不可现金购买",HttpStatus.BAD_REQUEST), PRICE_OVERFLOW(50004,"订单金额计算溢出",HttpStatus.BAD_REQUEST),
 STATE_CONFLICT(50005,"订单状态冲突",HttpStatus.CONFLICT), COUPON_CONFLICT(50006,"优惠券与会员不可叠加",HttpStatus.BAD_REQUEST),
 PAYMENT_DEADLINE_INVALID(50007,"支付截止时间不合法",HttpStatus.BAD_REQUEST), ACCESS_DENIED(50008,"无权访问该订单",HttpStatus.FORBIDDEN),
 PRICE_INVALID(50009,"订单价格不合法",HttpStatus.BAD_REQUEST);
 private final int code;private final String message;private final HttpStatus status;OrderErrorCode(int c,String m,HttpStatus s){code=c;message=m;status=s;}public int code(){return code;}public String message(){return message;}public HttpStatus httpStatus(){return status;}
}
