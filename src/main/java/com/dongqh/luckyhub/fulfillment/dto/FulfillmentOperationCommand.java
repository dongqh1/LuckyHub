package com.dongqh.luckyhub.fulfillment.dto;
import jakarta.validation.constraints.Size;
public record FulfillmentOperationCommand(@Size(max=500) String note){}
