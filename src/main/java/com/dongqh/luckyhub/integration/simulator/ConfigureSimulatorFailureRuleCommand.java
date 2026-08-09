package com.dongqh.luckyhub.integration.simulator;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType;import jakarta.validation.constraints.*;
public record ConfigureSimulatorFailureRuleCommand(@NotNull FulfillmentType fulfillmentType,@NotNull SimulatorFailureMode failureMode,@Min(0) @Max(10000) int count){}
