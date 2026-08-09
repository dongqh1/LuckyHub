package com.dongqh.luckyhub.integration.simulator;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType;
public interface SimulatorFailureRuleService {
 void configure(FulfillmentType type, SimulatorFailureMode mode, int count);
 SimulatorFailureMode consume(FulfillmentType type);
}
