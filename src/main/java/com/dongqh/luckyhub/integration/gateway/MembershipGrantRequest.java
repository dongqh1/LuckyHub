package com.dongqh.luckyhub.integration.gateway;
public record MembershipGrantRequest(String fulfillmentNo, Long targetUserId, String membershipCode, int durationDays) implements GatewayRequest {
 public MembershipGrantRequest { fulfillmentNo=GatewayValidation.required(fulfillmentNo,"fulfillmentNo"); targetUserId=GatewayValidation.positive(targetUserId,"targetUserId"); membershipCode=GatewayValidation.required(membershipCode,"membershipCode"); durationDays=GatewayValidation.positive(durationDays,"durationDays"); }
}
