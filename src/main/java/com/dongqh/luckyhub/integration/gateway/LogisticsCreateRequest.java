package com.dongqh.luckyhub.integration.gateway;
public record LogisticsCreateRequest(String fulfillmentNo, Long targetUserId, String skuCode, int quantity,
                                     String receiverMasked, String phoneMasked, String regionMasked) implements GatewayRequest {
 public LogisticsCreateRequest {
  fulfillmentNo=GatewayValidation.required(fulfillmentNo,"fulfillmentNo"); targetUserId=GatewayValidation.positive(targetUserId,"targetUserId");
  skuCode=GatewayValidation.required(skuCode,"skuCode"); quantity=GatewayValidation.positive(quantity,"quantity");
  receiverMasked=GatewayValidation.required(receiverMasked,"receiverMasked"); phoneMasked=GatewayValidation.required(phoneMasked,"phoneMasked"); regionMasked=GatewayValidation.required(regionMasked,"regionMasked");
  if(!receiverMasked.contains("*")||!phoneMasked.matches("\\d{3}\\*{4}\\d{4}")||!regionMasked.contains("*")) throw new IllegalArgumentException("物流收件信息必须脱敏");
 }
}
