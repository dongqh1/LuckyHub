package com.dongqh.luckyhub.integration.simulator;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType; import com.dongqh.luckyhub.integration.gateway.*; import tools.jackson.databind.ObjectMapper; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service;
@Service public class SimulatedMembershipGateway extends AbstractSimulatorGateway<MembershipGrantRequest> implements MembershipGateway {
 public SimulatedMembershipGateway(JdbcTemplate j,ObjectMapper o,SimulatorFailureRuleService r){super(FulfillmentType.MEMBERSHIP,"sim_membership_record","SIM-M-",j,o,r);} public GatewayResult execute(MembershipGrantRequest r){return executeRequest(r);} public GatewayResult query(String no){return queryRequest(no);}
}
