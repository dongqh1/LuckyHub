package com.dongqh.luckyhub.benefit.handler;

import com.dongqh.luckyhub.prize.enums.PrizeType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class BenefitFulfillmentRouter {

    private final Map<PrizeType, BenefitFulfillmentHandler> handlers;

    public BenefitFulfillmentRouter(List<BenefitFulfillmentHandler> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        EnumMap<PrizeType, BenefitFulfillmentHandler> indexed = new EnumMap<>(PrizeType.class);
        for (BenefitFulfillmentHandler handler : candidates) {
            BenefitFulfillmentHandler previous = indexed.putIfAbsent(
                    Objects.requireNonNull(handler.prizeType(), "handler prizeType must not be null"), handler);
            if (previous != null) {
                throw new IllegalStateException("Multiple fulfillment handlers for " + handler.prizeType());
            }
        }
        if (indexed.size() != PrizeType.values().length) {
            throw new IllegalStateException("Exactly one fulfillment handler is required for every prize type");
        }
        this.handlers = Map.copyOf(indexed);
    }

    public BenefitFulfillmentHandler route(PrizeType prizeType) {
        BenefitFulfillmentHandler handler = handlers.get(prizeType);
        if (handler == null) {
            throw new IllegalStateException("No fulfillment handler for " + prizeType);
        }
        return handler;
    }
}
