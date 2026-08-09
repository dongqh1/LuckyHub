package com.dongqh.luckyhub.fulfillment.model;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType;
public record PointsFulfillmentPayload(long points,String reason) implements FulfillmentPayload {public PointsFulfillmentPayload{points=PayloadValidation.positive(points,"points");reason=PayloadValidation.required(reason,"reason");}public FulfillmentType fulfillmentType(){return FulfillmentType.POINTS;}}
