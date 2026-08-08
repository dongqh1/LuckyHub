package com.dongqh.luckyhub.coupon.enums;
import com.dongqh.luckyhub.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;
public enum CouponErrorCode implements ErrorCode {
 TEMPLATE_NOT_FOUND(48001,"优惠券模板不存在",HttpStatus.NOT_FOUND), TEMPLATE_DUPLICATE(48002,"优惠券模板编码重复",HttpStatus.CONFLICT),
 COUPON_NOT_FOUND(48003,"用户优惠券不存在",HttpStatus.NOT_FOUND), ISSUE_LIMIT(48004,"超过每人领取上限",HttpStatus.CONFLICT),
 COUPON_UNAVAILABLE(48005,"优惠券当前不可用",HttpStatus.CONFLICT), COUPON_NOT_APPLICABLE(48006,"优惠券不适用于当前订单",HttpStatus.BAD_REQUEST),
 COUPON_IDEMPOTENCY_CONFLICT(48007,"优惠券幂等参数冲突",HttpStatus.CONFLICT), COUPON_STATE_CONFLICT(48008,"优惠券状态冲突",HttpStatus.CONFLICT);
 private final int code; private final String message; private final HttpStatus status;
 CouponErrorCode(int c,String m,HttpStatus s){code=c;message=m;status=s;} public int code(){return code;} public String message(){return message;} public HttpStatus httpStatus(){return status;}
}
