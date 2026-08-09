package com.dongqh.luckyhub.integration;

import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType;
import com.dongqh.luckyhub.fulfillment.enums.GatewayOutcome;
import com.dongqh.luckyhub.integration.gateway.*;
import com.dongqh.luckyhub.integration.simulator.SimulatorFailureMode;
import com.dongqh.luckyhub.integration.simulator.SimulatorFailureRuleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SimulatorGatewayTests {
    @Autowired CouponGateway couponGateway; @Autowired PointsGateway pointsGateway;
    @Autowired MembershipGateway membershipGateway; @Autowired LogisticsGateway logisticsGateway;
    @Autowired SimulatorFailureRuleService rules; @Autowired JdbcTemplate jdbc;

    @AfterEach void clean() {
        jdbc.update("DELETE FROM sim_coupon_record"); jdbc.update("DELETE FROM sim_points_record");
        jdbc.update("DELETE FROM sim_membership_record"); jdbc.update("DELETE FROM sim_logistics_record");
        jdbc.update("DELETE FROM sim_failure_rule");
    }

    @Test void executesAndQueriesAllFourProviders() {
        String suffix=UUID.randomUUID().toString();
        GatewayResult coupon=couponGateway.execute(new CouponGrantRequest("C-"+suffix,1L,"NEW20",1));
        GatewayResult points=pointsGateway.execute(new PointsGrantRequest("P-"+suffix,1L,500,"奖励"));
        GatewayResult member=membershipGateway.execute(new MembershipGrantRequest("M-"+suffix,1L,"VIP",30));
        GatewayResult logistics=logisticsGateway.execute(new LogisticsCreateRequest(
                "L-"+suffix,1L,99L,"SKU-1",1,"李四","13912341234",
                "上海市","上海市","浦东新区","世纪大道1号",
                "李*","139****1234","上海市浦东新区***"));
        assertThat(coupon.outcome()).isEqualTo(GatewayOutcome.SUCCEEDED);
        assertThat(points.outcome()).isEqualTo(GatewayOutcome.SUCCEEDED);
        assertThat(member.outcome()).isEqualTo(GatewayOutcome.SUCCEEDED);
        assertThat(logistics.outcome()).isEqualTo(GatewayOutcome.SUCCEEDED);
        assertThat(couponGateway.query("C-"+suffix).externalReference()).isEqualTo(coupon.externalReference());
        assertThat(logisticsGateway.query("missing").outcome()).isEqualTo(GatewayOutcome.NOT_FOUND);
    }

    @Test void duplicateExecuteIsIdempotentButChangedPayloadConflicts() {
        CouponGrantRequest request=new CouponGrantRequest("IDEM-1",1L,"NEW20",1);
        GatewayResult first=couponGateway.execute(request); GatewayResult second=couponGateway.execute(request);
        GatewayResult conflict=couponGateway.execute(new CouponGrantRequest("IDEM-1",1L,"NEW50",1));
        assertThat(second).isEqualTo(first);
        assertThat(conflict.outcome()).isEqualTo(GatewayOutcome.PERMANENT_FAILURE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sim_coupon_record WHERE fulfillment_no='IDEM-1'",Integer.class)).isOne();
    }

    @Test void injectsRetryablePermanentAndUnknownBeforeWithoutSideEffects() {
        rules.configure(FulfillmentType.POINTS, SimulatorFailureMode.RETRYABLE,1);
        assertThat(pointsGateway.execute(new PointsGrantRequest("FAIL-R",1L,1,"test")).outcome()).isEqualTo(GatewayOutcome.RETRYABLE_FAILURE);
        rules.configure(FulfillmentType.POINTS, SimulatorFailureMode.PERMANENT,1);
        assertThat(pointsGateway.execute(new PointsGrantRequest("FAIL-P",1L,1,"test")).outcome()).isEqualTo(GatewayOutcome.PERMANENT_FAILURE);
        rules.configure(FulfillmentType.POINTS, SimulatorFailureMode.UNKNOWN_BEFORE,1);
        assertThat(pointsGateway.execute(new PointsGrantRequest("FAIL-U",1L,1,"test")).outcome()).isEqualTo(GatewayOutcome.UNKNOWN);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sim_points_record",Integer.class)).isZero();
    }

    @Test void unknownAfterSuccessCanBeReconciledByQuery() {
        rules.configure(FulfillmentType.MEMBERSHIP, SimulatorFailureMode.UNKNOWN_AFTER_SUCCESS,1);
        GatewayResult response=membershipGateway.execute(new MembershipGrantRequest("LOST-1",1L,"VIP",30));
        GatewayResult query=membershipGateway.query("LOST-1");
        assertThat(response.outcome()).isEqualTo(GatewayOutcome.UNKNOWN);
        assertThat(query.outcome()).isEqualTo(GatewayOutcome.SUCCEEDED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sim_membership_record WHERE fulfillment_no='LOST-1'",Integer.class)).isOne();
    }
}
