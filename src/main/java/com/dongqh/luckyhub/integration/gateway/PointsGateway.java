package com.dongqh.luckyhub.integration.gateway;
public interface PointsGateway { GatewayResult execute(PointsGrantRequest request); GatewayResult query(String fulfillmentNo); }
