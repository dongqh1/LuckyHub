package com.dongqh.luckyhub.membership.enums;
import com.dongqh.luckyhub.common.enums.ErrorCode; import org.springframework.http.HttpStatus;
public enum MembershipErrorCode implements ErrorCode {
 PRODUCT_NOT_FOUND(49001,"会员产品不存在",HttpStatus.NOT_FOUND), PRODUCT_DUPLICATE(49002,"会员产品编码重复",HttpStatus.CONFLICT),
 MEMBERSHIP_NOT_FOUND(49003,"用户会员不存在",HttpStatus.NOT_FOUND), IDEMPOTENCY_CONFLICT(49004,"会员购买幂等参数冲突",HttpStatus.CONFLICT),
 PRODUCT_INVALID(49005,"会员产品配置不合法",HttpStatus.BAD_REQUEST), USER_UNAVAILABLE(49006,"会员用户不存在或已禁用",HttpStatus.BAD_REQUEST);
 private final int code;private final String message;private final HttpStatus status;MembershipErrorCode(int c,String m,HttpStatus s){code=c;message=m;status=s;}public int code(){return code;}public String message(){return message;}public HttpStatus httpStatus(){return status;}
}
