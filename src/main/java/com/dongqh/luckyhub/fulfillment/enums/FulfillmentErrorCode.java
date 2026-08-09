package com.dongqh.luckyhub.fulfillment.enums;
import com.dongqh.luckyhub.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;
public enum FulfillmentErrorCode implements ErrorCode {
 TASK_NOT_FOUND(52001,"履约任务不存在",HttpStatus.NOT_FOUND),
 IDEMPOTENCY_CONFLICT(52002,"履约幂等参数冲突",HttpStatus.CONFLICT),
 INVALID_PAYLOAD(52003,"履约参数不合法",HttpStatus.BAD_REQUEST),
 STATE_CONFLICT(52004,"履约任务状态冲突",HttpStatus.CONFLICT),
 LEASE_CONFLICT(52005,"履约任务租约冲突",HttpStatus.CONFLICT),
 GATEWAY_UNAVAILABLE(52006,"履约供应商暂时不可用",HttpStatus.SERVICE_UNAVAILABLE),
 QUARANTINE_NOT_FOUND(52007,"履约隔离记录不存在",HttpStatus.NOT_FOUND),
 SIMULATOR_CONFLICT(52008,"模拟供应商幂等参数冲突",HttpStatus.CONFLICT),
 FAILURE_RULE_INVALID(52009,"模拟失败规则不合法",HttpStatus.BAD_REQUEST),
 OPERATION_NOT_ALLOWED(52010,"当前状态不允许该操作",HttpStatus.CONFLICT);
 private final int code; private final String message; private final HttpStatus status;
 FulfillmentErrorCode(int code,String message,HttpStatus status){this.code=code;this.message=message;this.status=status;}
 public int code(){return code;} public String message(){return message;} public HttpStatus httpStatus(){return status;}
}
