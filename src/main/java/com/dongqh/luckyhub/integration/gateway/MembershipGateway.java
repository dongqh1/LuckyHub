package com.dongqh.luckyhub.integration.gateway;
public interface MembershipGateway { GatewayResult execute(MembershipGrantRequest request); GatewayResult query(String fulfillmentNo); }
