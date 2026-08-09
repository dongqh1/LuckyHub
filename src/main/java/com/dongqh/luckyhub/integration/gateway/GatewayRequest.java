package com.dongqh.luckyhub.integration.gateway;
public sealed interface GatewayRequest permits CouponGrantRequest, PointsGrantRequest, MembershipGrantRequest, LogisticsCreateRequest {
    String fulfillmentNo();
    Long targetUserId();
}
