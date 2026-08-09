package com.dongqh.luckyhub.fulfillment;

import com.dongqh.luckyhub.fulfillment.entity.FulfillmentAttempt;
import com.dongqh.luckyhub.fulfillment.entity.FulfillmentQuarantine;
import com.dongqh.luckyhub.fulfillment.entity.FulfillmentTask;
import com.dongqh.luckyhub.fulfillment.enums.AttemptOperation;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentErrorCode;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentStatus;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType;
import com.dongqh.luckyhub.fulfillment.enums.GatewayOutcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FulfillmentDomainContractTests {

    @Test
    void exposesStableFulfillmentTypesAndStates() {
        assertThat(FulfillmentType.values()).containsExactly(
                FulfillmentType.COUPON, FulfillmentType.POINTS,
                FulfillmentType.MEMBERSHIP, FulfillmentType.LOGISTICS);
        assertThat(FulfillmentStatus.values()).containsExactly(
                FulfillmentStatus.PENDING, FulfillmentStatus.PROCESSING,
                FulfillmentStatus.RETRY_WAITING, FulfillmentStatus.RECONCILING,
                FulfillmentStatus.SUCCEEDED, FulfillmentStatus.QUARANTINED,
                FulfillmentStatus.TERMINATED);
    }

    @Test
    void exposesWorkerOperationsOutcomesAndTenStableErrorCodes() {
        assertThat(AttemptOperation.values()).containsExactly(AttemptOperation.EXECUTE, AttemptOperation.QUERY);
        assertThat(GatewayOutcome.values()).containsExactly(
                GatewayOutcome.SUCCEEDED, GatewayOutcome.RETRYABLE_FAILURE,
                GatewayOutcome.PERMANENT_FAILURE, GatewayOutcome.UNKNOWN, GatewayOutcome.NOT_FOUND);
        assertThat(FulfillmentErrorCode.values()).hasSize(10);
    }

    @Test
    void providesPersistentAggregateTypes() {
        assertThat(FulfillmentTask.class).isNotNull();
        assertThat(FulfillmentAttempt.class).isNotNull();
        assertThat(FulfillmentQuarantine.class).isNotNull();
    }
}
