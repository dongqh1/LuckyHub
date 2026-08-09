package com.dongqh.luckyhub.fulfillment.model;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType;
public sealed interface FulfillmentPayload permits CouponFulfillmentPayload, PointsFulfillmentPayload, MembershipFulfillmentPayload, LogisticsFulfillmentPayload { FulfillmentType fulfillmentType(); }
