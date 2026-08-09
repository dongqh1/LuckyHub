package com.dongqh.luckyhub.fulfillment.model;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType;
public record MembershipFulfillmentPayload(String membershipCode,int durationDays) implements FulfillmentPayload {public MembershipFulfillmentPayload{membershipCode=PayloadValidation.required(membershipCode,"membershipCode");durationDays=PayloadValidation.positive(durationDays,"durationDays");}public FulfillmentType fulfillmentType(){return FulfillmentType.MEMBERSHIP;}}
