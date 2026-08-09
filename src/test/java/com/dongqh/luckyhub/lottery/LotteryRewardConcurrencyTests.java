package com.dongqh.luckyhub.lottery;

import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventType;
import com.dongqh.luckyhub.lottery.messaging.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.luckyhub.lottery.service.LotteryRewardDispatchService;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.reward.enums.RewardType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LotteryRewardConcurrencyTests {
    @Autowired LotteryRewardDispatchService dispatch;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String requestId;
    private String eventId;

    @AfterEach
    void clean() {
        if (eventId != null) jdbc.update("DELETE FROM message_consume_record WHERE event_id=?", eventId);
        if (requestId != null) {
            jdbc.update("DELETE FROM fulfillment_task WHERE source_id IN (SELECT CAST(id AS CHAR) FROM user_benefit WHERE draw_record_id IN (SELECT id FROM lottery_draw_record WHERE request_id=?))", requestId);
            jdbc.update("DELETE b FROM user_benefit b JOIN lottery_draw_record r ON r.id=b.draw_record_id WHERE r.request_id=?", requestId);
            jdbc.update("DELETE FROM lottery_draw_record WHERE request_id=?", requestId);
            jdbc.update("DELETE FROM lottery_draw_order WHERE request_id=?", requestId);
        }
    }

    @Test
    void concurrentDuplicateRewardEventCreatesOneTaskAndOneConsumeRecord() throws Exception {
        requestId = "reward-race-" + UUID.randomUUID();
        long userId = positiveId();
        long activityId = positiveId();
        long prizeId = positiveId();
        long definitionId = positiveId();
        String fingerprint = "b".repeat(64);
        String payloadJson = "{\"templateId\":91,\"templateCode\":\"RACE\",\"quantity\":1}";
        jdbc.update("INSERT INTO lottery_draw_order(request_id,user_id,activity_id,draw_count,draw_date,status) VALUES(?,?,?,1,CURRENT_DATE,'SUCCESS')",
                requestId, userId, activityId);
        long orderId = jdbc.queryForObject("SELECT id FROM lottery_draw_order WHERE request_id=?", Long.class, requestId);
        jdbc.update("""
                INSERT INTO lottery_draw_record
                  (order_id,request_id,sequence_no,user_id,activity_id,result_type,prize_id,prize_name,
                   prize_type,reward_definition_id,reward_type,reward_target_id,reward_quantity,
                   reward_payload,reward_fingerprint,draw_time)
                VALUES (?,?,1,?,?,'WIN',?,'并发券','COUPON',?,'COUPON',91,1,CAST(? AS JSON),?,CURRENT_TIMESTAMP(3))
                """, orderId, requestId, userId, activityId, prizeId, definitionId, payloadJson, fingerprint);
        long recordId = jdbc.queryForObject("SELECT id FROM lottery_draw_record WHERE request_id=?", Long.class, requestId);
        jdbc.update("""
                INSERT INTO user_benefit
                  (draw_record_id,user_id,prize_id,prize_type,reward_definition_id,reward_type,
                   reward_target_id,reward_quantity,reward_payload,reward_fingerprint,quantity,status,obtained_at)
                VALUES (?,?,?,'COUPON',?,'COUPON',91,1,CAST(? AS JSON),?,1,'PENDING',CURRENT_TIMESTAMP(3))
                """, recordId, userId, prizeId, definitionId, payloadJson, fingerprint);
        long benefitId = jdbc.queryForObject("SELECT id FROM user_benefit WHERE draw_record_id=?", Long.class, recordId);
        PrizeFulfillmentRequestedEvent payload = new PrizeFulfillmentRequestedEvent(
                benefitId, recordId, prizeId, PrizeType.COUPON,
                definitionId, RewardType.COUPON, fingerprint);
        DrawEventEnvelope envelope = DrawEventEnvelope.create(
                DrawEventType.PRIZE_FULFILLMENT_REQUESTED, requestId, userId, activityId,
                orderId, LocalDateTime.now(), payload, json);
        eventId = envelope.eventId().toString();

        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    dispatch.dispatch(envelope, payload);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fulfillment_task WHERE source_id=?", Long.class, Long.toString(benefitId))).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM message_consume_record WHERE event_id=?", Long.class, eventId)).isOne();
    }

    private long positiveId() {
        long value = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        return value == 0 ? 1 : value;
    }
}
