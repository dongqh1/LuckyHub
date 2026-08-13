package com.dongqh.luckyhub.shipping.integration;

import com.dongqh.luckyhub.fulfillment.model.FulfillmentClaim;
import com.dongqh.luckyhub.fulfillment.model.LogisticsFulfillmentPayload;
import com.dongqh.luckyhub.integration.gateway.LogisticsCreateRequest;

public interface LogisticsRequestAssembler {
    LogisticsCreateRequest assemble(FulfillmentClaim claim, LogisticsFulfillmentPayload payload);
}
