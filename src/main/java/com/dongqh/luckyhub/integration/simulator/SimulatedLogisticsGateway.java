package com.dongqh.luckyhub.integration.simulator;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType; import com.dongqh.luckyhub.integration.gateway.*; import tools.jackson.databind.ObjectMapper; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service;
@Service public class SimulatedLogisticsGateway extends AbstractSimulatorGateway<LogisticsCreateRequest> implements LogisticsGateway {
 public SimulatedLogisticsGateway(JdbcTemplate j,ObjectMapper o,SimulatorFailureRuleService r){super(FulfillmentType.LOGISTICS,"sim_logistics_record","SIM-L-",j,o,r);} public GatewayResult execute(LogisticsCreateRequest r){return executeRequest(r);} public GatewayResult query(String no){return queryRequest(no);}
}
