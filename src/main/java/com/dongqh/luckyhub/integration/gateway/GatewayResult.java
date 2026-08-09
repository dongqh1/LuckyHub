package com.dongqh.luckyhub.integration.gateway;
import com.dongqh.luckyhub.fulfillment.enums.GatewayOutcome;
public record GatewayResult(GatewayOutcome outcome, String externalReference, String errorCode, String safeMessage) {
 public GatewayResult {
  if(outcome==null) throw new IllegalArgumentException("outcome不能为空");
  externalReference=GatewayValidation.optional(externalReference); errorCode=GatewayValidation.bounded(errorCode,64); safeMessage=GatewayValidation.bounded(safeMessage,500);
  if(outcome==GatewayOutcome.SUCCEEDED && externalReference==null) throw new IllegalArgumentException("成功结果必须包含externalReference");
 }
 public static GatewayResult succeeded(String reference){return new GatewayResult(GatewayOutcome.SUCCEEDED,reference,null,null);}
 public static GatewayResult retryable(String code,String message){return new GatewayResult(GatewayOutcome.RETRYABLE_FAILURE,null,code,message);}
 public static GatewayResult permanent(String code,String message){return new GatewayResult(GatewayOutcome.PERMANENT_FAILURE,null,code,message);}
 public static GatewayResult unknown(String code,String message){return new GatewayResult(GatewayOutcome.UNKNOWN,null,code,message);}
 public static GatewayResult notFound(){return new GatewayResult(GatewayOutcome.NOT_FOUND,null,null,null);}
}
