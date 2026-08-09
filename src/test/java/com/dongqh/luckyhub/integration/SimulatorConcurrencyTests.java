package com.dongqh.luckyhub.integration;

import com.dongqh.luckyhub.fulfillment.enums.GatewayOutcome;
import com.dongqh.luckyhub.integration.gateway.CouponGateway;
import com.dongqh.luckyhub.integration.gateway.CouponGrantRequest;
import com.dongqh.luckyhub.integration.gateway.GatewayResult;
import org.junit.jupiter.api.AfterEach; import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.context.SpringBootTest; import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List; import java.util.concurrent.*; import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest class SimulatorConcurrencyTests {
 @Autowired CouponGateway gateway; @Autowired JdbcTemplate jdbc;
 @AfterEach void clean(){jdbc.update("DELETE FROM sim_coupon_record");jdbc.update("DELETE FROM sim_failure_rule");}
 @Test void twentyConcurrentDuplicatesCreateOneProviderEffect() throws Exception {
  ExecutorService pool=Executors.newFixedThreadPool(20);
  try {
   CountDownLatch ready=new CountDownLatch(20), start=new CountDownLatch(1);
   List<Future<GatewayResult>> futures=IntStream.range(0,20).mapToObj(i->pool.submit(()->{ready.countDown();start.await();return gateway.execute(new CouponGrantRequest("RACE-1",9L,"C20",1));})).toList();
   assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
   for(Future<GatewayResult> future:futures) assertThat(future.get(10,TimeUnit.SECONDS).outcome()).isEqualTo(GatewayOutcome.SUCCEEDED);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sim_coupon_record WHERE fulfillment_no='RACE-1'",Integer.class)).isOne();
  } finally {pool.shutdownNow();}
 }
}
