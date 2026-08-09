package com.dongqh.luckyhub.fulfillment.service;
import com.dongqh.luckyhub.fulfillment.model.FulfillmentClaim;import com.dongqh.luckyhub.integration.gateway.GatewayResult;import java.time.Duration;import java.util.List;
public interface FulfillmentStateService {List<FulfillmentClaim> claimDue(int limit,Duration leaseDuration);void recordResult(FulfillmentClaim claim,GatewayResult result,long durationMs);int recoverExpiredLeases(int limit);}
