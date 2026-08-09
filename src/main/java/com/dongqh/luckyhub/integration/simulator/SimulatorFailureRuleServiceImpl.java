package com.dongqh.luckyhub.integration.simulator;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType; import org.springframework.dao.EmptyResultDataAccessException; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
@Service public class SimulatorFailureRuleServiceImpl implements SimulatorFailureRuleService {
 private final JdbcTemplate jdbc; public SimulatorFailureRuleServiceImpl(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Override @Transactional public void configure(FulfillmentType type,SimulatorFailureMode mode,int count){
  if(type==null||mode==null||count<0||count>10000)throw new IllegalArgumentException("失败规则不合法");
  jdbc.update("INSERT INTO sim_failure_rule(fulfillment_type,failure_mode,remaining_count) VALUES(?,?,?) " +
   "ON DUPLICATE KEY UPDATE failure_mode=VALUES(failure_mode),remaining_count=VALUES(remaining_count)",type.name(),mode.name(),count);
 }
 @Override @Transactional public SimulatorFailureMode consume(FulfillmentType type){
  try {
   Rule row=jdbc.queryForObject("SELECT failure_mode,remaining_count FROM sim_failure_rule WHERE fulfillment_type=? FOR UPDATE",(rs,n)->new Rule(SimulatorFailureMode.valueOf(rs.getString(1)),rs.getInt(2)),type.name());
   if(row==null||row.remaining<=0)return SimulatorFailureMode.SUCCESS;
   jdbc.update("UPDATE sim_failure_rule SET remaining_count=remaining_count-1 WHERE fulfillment_type=?",type.name()); return row.mode;
  } catch(EmptyResultDataAccessException ignored){return SimulatorFailureMode.SUCCESS;}
 }
 private record Rule(SimulatorFailureMode mode,int remaining){}
}
