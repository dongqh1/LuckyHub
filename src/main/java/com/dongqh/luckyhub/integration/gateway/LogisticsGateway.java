package com.dongqh.luckyhub.integration.gateway;
public interface LogisticsGateway { GatewayResult execute(LogisticsCreateRequest request); GatewayResult query(String fulfillmentNo); }
