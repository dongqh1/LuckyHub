package com.dongqh.luckyhub.integration.simulator;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType; import com.dongqh.luckyhub.integration.gateway.*; import tools.jackson.databind.ObjectMapper; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service;
@Service public class SimulatedPointsGateway extends AbstractSimulatorGateway<PointsGrantRequest> implements PointsGateway {
 public SimulatedPointsGateway(JdbcTemplate j,ObjectMapper o,SimulatorFailureRuleService r){super(FulfillmentType.POINTS,"sim_points_record","SIM-P-",j,o,r);} public GatewayResult execute(PointsGrantRequest r){return executeRequest(r);} public GatewayResult query(String no){return queryRequest(no);}
}
