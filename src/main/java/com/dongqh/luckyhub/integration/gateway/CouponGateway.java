package com.dongqh.luckyhub.integration.gateway;
public interface CouponGateway { GatewayResult execute(CouponGrantRequest request); GatewayResult query(String fulfillmentNo); }
