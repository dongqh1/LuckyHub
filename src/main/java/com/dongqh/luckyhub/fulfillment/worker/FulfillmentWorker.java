package com.dongqh.luckyhub.fulfillment.worker;

import com.dongqh.luckyhub.fulfillment.config.FulfillmentProperties;
import com.dongqh.luckyhub.fulfillment.enums.AttemptOperation;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType;
import com.dongqh.luckyhub.fulfillment.model.*;
import com.dongqh.luckyhub.fulfillment.service.FulfillmentStateService;
import com.dongqh.luckyhub.integration.gateway.*;
import com.dongqh.luckyhub.shipping.integration.LogisticsRequestAssembler;
import com.dongqh.luckyhub.shipping.service.ShippingOrderService;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class FulfillmentWorker {
    private final FulfillmentStateService state;
    private final CouponGateway coupons;
    private final PointsGateway points;
    private final MembershipGateway memberships;
    private final LogisticsGateway logistics;
    private final LogisticsRequestAssembler logisticsAssembler;
    private final ShippingOrderService shippingOrders;
    private final ObjectMapper json;
    private final FulfillmentProperties properties;

    public FulfillmentWorker(
            FulfillmentStateService state,
            CouponGateway coupons,
            PointsGateway points,
            MembershipGateway memberships,
            LogisticsGateway logistics,
            LogisticsRequestAssembler logisticsAssembler,
            ShippingOrderService shippingOrders,
            ObjectMapper json,
            FulfillmentProperties properties
    ) {
        this.state = state;
        this.coupons = coupons;
        this.points = points;
        this.memberships = memberships;
        this.logistics = logistics;
        this.logisticsAssembler = logisticsAssembler;
        this.shippingOrders = shippingOrders;
        this.json = json;
        this.properties = properties;
    }

    public int runBatch() {
        var claims = state.claimDue(properties.batchSize(), properties.leaseDuration());
        for (FulfillmentClaim claim : claims) {
            long start = System.nanoTime();
            GatewayResult result;
            try {
                result = route(claim);
            } catch (RuntimeException exception) {
                result = GatewayResult.retryable("GATEWAY_EXCEPTION", "Gateway调用异常");
            }
            long duration = Math.max(0, (System.nanoTime() - start) / 1_000_000);
            state.recordResult(claim, result, duration);
            if (claim.fulfillmentType() == FulfillmentType.LOGISTICS) {
                shippingOrders.projectFulfillmentState(claim.fulfillmentNo());
            }
        }
        return claims.size();
    }

    private GatewayResult route(FulfillmentClaim claim) {
        if (claim.operation() == AttemptOperation.QUERY) {
            return switch (claim.fulfillmentType()) {
                case COUPON -> coupons.query(claim.fulfillmentNo());
                case POINTS -> points.query(claim.fulfillmentNo());
                case MEMBERSHIP -> memberships.query(claim.fulfillmentNo());
                case LOGISTICS -> logistics.query(claim.fulfillmentNo());
            };
        }
        try {
            return switch (claim.fulfillmentType()) {
                case COUPON -> {
                    CouponFulfillmentPayload payload = json.readValue(
                            claim.requestPayload(), CouponFulfillmentPayload.class);
                    yield coupons.execute(new CouponGrantRequest(claim.fulfillmentNo(),
                            claim.targetUserId(), payload.couponTemplateCode(), payload.quantity()));
                }
                case POINTS -> {
                    PointsFulfillmentPayload payload = json.readValue(
                            claim.requestPayload(), PointsFulfillmentPayload.class);
                    yield points.execute(new PointsGrantRequest(claim.fulfillmentNo(),
                            claim.targetUserId(), payload.points(), payload.reason()));
                }
                case MEMBERSHIP -> {
                    MembershipFulfillmentPayload payload = json.readValue(
                            claim.requestPayload(), MembershipFulfillmentPayload.class);
                    yield memberships.execute(new MembershipGrantRequest(claim.fulfillmentNo(),
                            claim.targetUserId(), payload.membershipCode(), payload.durationDays()));
                }
                case LOGISTICS -> {
                    LogisticsFulfillmentPayload payload = json.readValue(
                            claim.requestPayload(), LogisticsFulfillmentPayload.class);
                    yield logistics.execute(logisticsAssembler.assemble(claim, payload));
                }
            };
        } catch (JacksonException exception) {
            return GatewayResult.permanent("INVALID_SNAPSHOT", "履约快照无法解析");
        }
    }
}
