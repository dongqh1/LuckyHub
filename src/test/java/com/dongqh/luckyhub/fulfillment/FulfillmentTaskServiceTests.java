package com.dongqh.luckyhub.fulfillment;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.fulfillment.dto.*;
import com.dongqh.luckyhub.fulfillment.enums.*;
import com.dongqh.luckyhub.fulfillment.model.*;
import com.dongqh.luckyhub.fulfillment.service.FulfillmentTaskService;
import com.dongqh.luckyhub.fulfillment.vo.FulfillmentTaskView;
import org.junit.jupiter.api.AfterEach; import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.context.SpringBootTest; import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List; import java.util.concurrent.*; import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest class FulfillmentTaskServiceTests {
 @Autowired FulfillmentTaskService service; @Autowired JdbcTemplate jdbc;
 @AfterEach void clean(){jdbc.update("DELETE FROM fulfillment_attempt");jdbc.update("DELETE FROM fulfillment_quarantine");jdbc.update("DELETE FROM fulfillment_task");}
 @Test void createsFourValidatedPayloadSnapshotsWithoutCallingProviders(){
  List<CreateFulfillmentTaskCommand> commands=List.of(
   command("TASK-C",FulfillmentType.COUPON,new CouponFulfillmentPayload("NEW20",1)),
   command("TASK-P",FulfillmentType.POINTS,new PointsFulfillmentPayload(500,"抽奖奖励")),
   command("TASK-M",FulfillmentType.MEMBERSHIP,new MembershipFulfillmentPayload("VIP",30)),
   command("TASK-L",FulfillmentType.LOGISTICS,new LogisticsFulfillmentPayload("SKU-1",1,"王*","137****9999","北京市海淀区***")));
  List<FulfillmentTaskView> views=commands.stream().map(service::create).toList();
  assertThat(views).extracting(FulfillmentTaskView::status).containsOnly(FulfillmentStatus.PENDING);
  assertThat(views.get(0).payload()).isEqualTo(new CouponFulfillmentPayload("NEW20",1));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sim_coupon_record",Integer.class)).isZero();
  assertThat(jdbc.queryForObject("SELECT request_payload FROM fulfillment_task WHERE fulfillment_no='TASK-L'",String.class)).doesNotContain("王二","13712349999");
 }
 @Test void sameCommandIsIdempotentAndChangedCommandConflicts(){
  CreateFulfillmentTaskCommand command=command("IDEM-TASK",FulfillmentType.POINTS,new PointsFulfillmentPayload(100,"奖励"));
  FulfillmentTaskView first=service.create(command), second=service.create(command);
  assertThat(second.id()).isEqualTo(first.id());assertThat(second.requestFingerprint()).isEqualTo(first.requestFingerprint());
  assertThatThrownBy(()->service.create(command("IDEM-TASK",FulfillmentType.POINTS,new PointsFulfillmentPayload(200,"奖励"))))
   .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode()).isEqualTo(FulfillmentErrorCode.IDEMPOTENCY_CONFLICT));
 }
 @Test void validatesPayloadTypeAndMaskedLogistics(){
  assertThatThrownBy(()->command("BAD",FulfillmentType.COUPON,new PointsFulfillmentPayload(1,"x"))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new LogisticsFulfillmentPayload("SKU",1,"王五","13712349999","北京市海淀区" )).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void getsAndPagesByTypeStatusAndIdentity(){
  service.create(command("PAGE-C",FulfillmentType.COUPON,new CouponFulfillmentPayload("C1",1)));
  service.create(command("PAGE-P",FulfillmentType.POINTS,new PointsFulfillmentPayload(1,"x")));
  FulfillmentTaskQuery query=new FulfillmentTaskQuery();query.setPage(1);query.setSize(10);query.setFulfillmentType(FulfillmentType.POINTS);query.setStatus(FulfillmentStatus.PENDING);query.setTargetUserId(11L);
  assertThat(service.get("PAGE-C").sourceType()).isEqualTo("LOTTERY_REWARD");
  assertThat(service.page(query).records()).extracting(FulfillmentTaskView::fulfillmentNo).containsExactly("PAGE-P");
 }
 @Test void concurrentDuplicateCreationReturnsOneTask() throws Exception {
  ExecutorService pool=Executors.newFixedThreadPool(10);try{CountDownLatch start=new CountDownLatch(1);CreateFulfillmentTaskCommand c=command("CREATE-RACE",FulfillmentType.COUPON,new CouponFulfillmentPayload("C1",1));
   List<Future<Long>> futures=IntStream.range(0,10).mapToObj(i->pool.submit(()->{start.await();return service.create(c).id();})).toList();start.countDown();
   assertThat(futures.stream().map(f->{try{return f.get(10,TimeUnit.SECONDS);}catch(Exception e){throw new RuntimeException(e);}}).distinct()).hasSize(1);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fulfillment_task WHERE fulfillment_no='CREATE-RACE'",Integer.class)).isOne();
  }finally{pool.shutdownNow();}
 }
 private CreateFulfillmentTaskCommand command(String no,FulfillmentType type,FulfillmentPayload payload){return new CreateFulfillmentTaskCommand(no,"LOTTERY_REWARD","DRAW-1",type,11L,payload,5);}
}
