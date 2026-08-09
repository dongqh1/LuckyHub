package com.dongqh.luckyhub.integration.simulator;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType; import com.dongqh.luckyhub.integration.gateway.*; import tools.jackson.databind.ObjectMapper; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service;
@Service public class SimulatedCouponGateway extends AbstractSimulatorGateway<CouponGrantRequest> implements CouponGateway {
 public SimulatedCouponGateway(JdbcTemplate j,ObjectMapper o,SimulatorFailureRuleService r){super(FulfillmentType.COUPON,"sim_coupon_record","SIM-C-",j,o,r);} public GatewayResult execute(CouponGrantRequest r){return executeRequest(r);} public GatewayResult query(String no){return queryRequest(no);}
}
