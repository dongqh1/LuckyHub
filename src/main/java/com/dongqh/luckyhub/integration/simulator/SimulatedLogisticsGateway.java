package com.dongqh.luckyhub.integration.simulator;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType; import com.dongqh.luckyhub.integration.gateway.*; import tools.jackson.databind.ObjectMapper; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service;
@Service public class SimulatedLogisticsGateway extends AbstractSimulatorGateway<LogisticsCreateRequest> implements LogisticsGateway {
 public SimulatedLogisticsGateway(JdbcTemplate j,ObjectMapper o,SimulatorFailureRuleService r){super(FulfillmentType.LOGISTICS,"sim_logistics_record","SIM-L-",j,o,r);} public GatewayResult execute(LogisticsCreateRequest r){return executeRequest(r);} public GatewayResult query(String no){return queryRequest(no);}
 @Override protected Object safePersistenceRequest(LogisticsCreateRequest request){return new SafeLogisticsRequest(request.fulfillmentNo(),request.targetUserId(),request.shippingOrderId(),request.skuCode(),request.quantity(),request.receiverMasked(),request.phoneMasked(),request.regionMasked());}
 private record SafeLogisticsRequest(String fulfillmentNo,Long targetUserId,long shippingOrderId,String skuCode,int quantity,String receiverMasked,String phoneMasked,String regionMasked){}
}
