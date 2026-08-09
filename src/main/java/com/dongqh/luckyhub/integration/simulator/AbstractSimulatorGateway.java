package com.dongqh.luckyhub.integration.simulator;
import com.dongqh.luckyhub.fulfillment.enums.*; import com.dongqh.luckyhub.integration.gateway.*; import tools.jackson.core.JacksonException; import tools.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException; import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.security.NoSuchAlgorithmException; import java.util.HexFormat;
abstract class AbstractSimulatorGateway<R extends GatewayRequest> {
 private final FulfillmentType type; private final String table; private final String referencePrefix; private final JdbcTemplate jdbc; private final ObjectMapper json; private final SimulatorFailureRuleService rules;
 AbstractSimulatorGateway(FulfillmentType type,String table,String prefix,JdbcTemplate jdbc,ObjectMapper json,SimulatorFailureRuleService rules){this.type=type;this.table=table;this.referencePrefix=prefix;this.jdbc=jdbc;this.json=json;this.rules=rules;}
 GatewayResult executeRequest(R request){
  Serialized serialized=serialize(request); GatewayResult existing=find(request.fulfillmentNo(),serialized.fingerprint); if(existing!=null)return existing;
  SimulatorFailureMode mode=rules.consume(type);
  if(mode==SimulatorFailureMode.RETRYABLE)return GatewayResult.retryable("SIM_RETRYABLE","模拟供应方暂时繁忙");
  if(mode==SimulatorFailureMode.PERMANENT)return GatewayResult.permanent("SIM_PERMANENT","模拟供应方明确拒绝");
  if(mode==SimulatorFailureMode.UNKNOWN_BEFORE)return GatewayResult.unknown("SIM_UNKNOWN_BEFORE","模拟请求发送前结果未知");
  GatewayResult saved=save(request.fulfillmentNo(),serialized);
  return mode==SimulatorFailureMode.UNKNOWN_AFTER_SUCCESS?GatewayResult.unknown("SIM_UNKNOWN_AFTER","模拟供应方已处理但响应丢失"):saved;
 }
 GatewayResult queryRequest(String fulfillmentNo){String no=requiredNo(fulfillmentNo);return jdbc.query("SELECT external_reference FROM "+table+" WHERE fulfillment_no=?",rs->rs.next()?GatewayResult.succeeded(rs.getString(1)):GatewayResult.notFound(),no);}
 private GatewayResult find(String no,String fingerprint){return jdbc.query("SELECT request_fingerprint,external_reference FROM "+table+" WHERE fulfillment_no=?",rs->{if(!rs.next())return null;return fingerprint.equals(rs.getString(1))?GatewayResult.succeeded(rs.getString(2)):GatewayResult.permanent("SIM_IDEMPOTENCY_CONFLICT","相同履约编号的参数不一致");},no);}
 private GatewayResult save(String no,Serialized value){String reference=referencePrefix+digest(no).substring(0,24);try{jdbc.update("INSERT INTO "+table+"(fulfillment_no,request_fingerprint,request_payload,external_reference,status) VALUES(?,?,?,?, 'SUCCEEDED')",no,value.fingerprint,value.payload,reference);return GatewayResult.succeeded(reference);}catch(DuplicateKeyException race){return find(no,value.fingerprint);}}
 protected Object safePersistenceRequest(R request){return request;}
 private Serialized serialize(R request){try{String payload=json.writeValueAsString(safePersistenceRequest(request));return new Serialized(payload,digest(payload));}catch(JacksonException e){throw new IllegalArgumentException("无法序列化模拟供应方请求",e);}}
 private String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private String requiredNo(String no){if(no==null||no.isBlank())throw new IllegalArgumentException("fulfillmentNo不能为空");return no.trim();}
 private record Serialized(String payload,String fingerprint){}
}
