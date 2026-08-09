package com.dongqh.luckyhub.integration.gateway;
public record CouponGrantRequest(String fulfillmentNo, Long targetUserId, String couponTemplateCode, int quantity) implements GatewayRequest {
 public CouponGrantRequest { fulfillmentNo=GatewayValidation.required(fulfillmentNo,"fulfillmentNo"); targetUserId=GatewayValidation.positive(targetUserId,"targetUserId"); couponTemplateCode=GatewayValidation.required(couponTemplateCode,"couponTemplateCode"); quantity=GatewayValidation.positive(quantity,"quantity"); }
}
