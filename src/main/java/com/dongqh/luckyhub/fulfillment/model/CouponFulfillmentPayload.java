package com.dongqh.luckyhub.fulfillment.model;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType;
public record CouponFulfillmentPayload(String couponTemplateCode,int quantity) implements FulfillmentPayload {public CouponFulfillmentPayload{couponTemplateCode=PayloadValidation.required(couponTemplateCode,"couponTemplateCode");quantity=PayloadValidation.positive(quantity,"quantity");}public FulfillmentType fulfillmentType(){return FulfillmentType.COUPON;}}
