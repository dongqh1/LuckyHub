package com.dongqh.luckyhub.lottery;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.benefit.service.LotteryRewardProjectionService;
import com.dongqh.luckyhub.fulfillment.worker.FulfillmentWorker;
import com.dongqh.luckyhub.lottery.algorithm.DrawRandomSource;
import com.dongqh.luckyhub.lottery.config.MessagingProperties;
import com.dongqh.luckyhub.lottery.dto.DrawCommand;
import com.dongqh.luckyhub.lottery.entity.MessageOutbox;
import com.dongqh.luckyhub.lottery.mapper.MessageOutboxMapper;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.port.DrawEventPublisher;
import com.dongqh.luckyhub.lottery.messaging.redis.RedisStreamDrawEventConsumer;
import com.dongqh.luckyhub.lottery.messaging.redis.RedisStreamDrawEventPublisher;
import com.dongqh.luckyhub.lottery.messaging.redis.RedisStreamInitializer;
import com.dongqh.luckyhub.lottery.quota.DrawQuotaKeys;
import com.dongqh.luckyhub.lottery.service.LotteryService;
import com.dongqh.luckyhub.lottery.service.MessageConsumeService;
import com.dongqh.luckyhub.lottery.service.OutboxRelayService;
import com.dongqh.luckyhub.reward.enums.RewardType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "luckyhub.messaging.consumer-initial-delay=24h",
        "luckyhub.fulfillment.initial-delay=24h",
        "luckyhub.fulfillment.projection-initial-delay=24h"
})
class LotteryFiveRewardEndToEndTests {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired LotteryService lottery;
    @Autowired MessageConsumeService consumerService;
    @Autowired FulfillmentWorker worker;
    @Autowired LotteryRewardProjectionService projection;
    @Autowired MessageOutboxMapper outboxes;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper json;
    @MockitoBean DrawRandomSource random;

    private final String marker = "p5e2e-" + UUID.randomUUID().toString().substring(0, 8);
    private final List<String> requests = new ArrayList<>();
    private final List<Long> activities = new ArrayList<>();
    private final List<String> streams = new ArrayList<>();
    private long userId;

    @BeforeEach
    void setUp() {
        Mockito.reset(random);
        when(random.nextLong(anyLong())).thenReturn(0L);
        userId = insert("INSERT INTO sys_user(username,password,nickname,status) VALUES(?,?,?,1)",
                marker, "x", "五类奖励用户");
        LoginContext.set(new LoginPrincipal(userId, marker, "phase5-session"));
    }

    @AfterEach
    void clean() {
        LoginContext.clear();
        List<String> eventIds = new ArrayList<>();
        requests.forEach(request -> eventIds.addAll(jdbc.queryForList(
                "SELECT event_id FROM message_outbox WHERE payload ->> '$.requestId'=?",
                String.class, request)));
        eventIds.forEach(id -> jdbc.update("DELETE FROM message_consume_record WHERE event_id=?", id));
        List<String> fulfillmentNos = jdbc.queryForList(
                "SELECT fulfillment_no FROM user_benefit WHERE user_id=? AND fulfillment_no IS NOT NULL",
                String.class, userId);
        for (String no : fulfillmentNos) {
            jdbc.update("DELETE FROM fulfillment_attempt WHERE fulfillment_no=?", no);
            jdbc.update("DELETE FROM fulfillment_quarantine WHERE fulfillment_no=?", no);
            jdbc.update("DELETE FROM sim_coupon_record WHERE fulfillment_no=?", no);
            jdbc.update("DELETE FROM sim_points_record WHERE fulfillment_no=?", no);
            jdbc.update("DELETE FROM sim_membership_record WHERE fulfillment_no=?", no);
            jdbc.update("DELETE FROM sim_logistics_record WHERE fulfillment_no=?", no);
            jdbc.update("DELETE FROM fulfillment_task WHERE fulfillment_no=?", no);
        }
        jdbc.update("DELETE FROM coupon_issue_record WHERE user_id=?", userId);
        jdbc.update("DELETE FROM user_coupon WHERE user_id=?", userId);
        jdbc.update("DELETE FROM membership_grant_record WHERE user_id=?", userId);
        jdbc.update("DELETE FROM user_membership WHERE user_id=?", userId);
        jdbc.update("DELETE FROM points_ledger WHERE user_id=?", userId);
        jdbc.update("DELETE FROM points_account WHERE user_id=?", userId);
        jdbc.update("DELETE FROM draw_chance_reservation WHERE user_id=?", userId);
        jdbc.update("DELETE FROM draw_chance_ledger WHERE user_id=?", userId);
        jdbc.update("DELETE FROM draw_chance_account WHERE user_id=?", userId);
        jdbc.update("DELETE FROM user_benefit WHERE user_id=?", userId);
        jdbc.update("DELETE FROM lottery_draw_record WHERE user_id=?", userId);
        jdbc.update("DELETE FROM lottery_draw_order WHERE user_id=?", userId);
        requests.forEach(id -> jdbc.update("DELETE FROM message_outbox WHERE payload ->> '$.requestId'=?", id));
        activities.forEach(id -> {
            redis.delete(DrawQuotaKeys.quota(id, userId, LocalDate.now(SHANGHAI)));
            jdbc.update("DELETE FROM marketing_activity_prize WHERE activity_id=?", id);
            jdbc.update("DELETE FROM marketing_activity WHERE id=?", id);
        });
        requests.forEach(id -> {
            redis.delete(DrawQuotaKeys.reservation(id));
            redis.opsForZSet().remove(DrawQuotaKeys.reservationTimeouts(), id);
        });
        streams.forEach(redis::delete);
        jdbc.update("DELETE FROM marketing_prize WHERE prize_name LIKE ?", marker + "%");
        jdbc.update("DELETE FROM reward_definition WHERE reward_code LIKE ?", marker + "%");
        jdbc.update("DELETE FROM product_sku WHERE sku_code LIKE ?", marker + "%");
        jdbc.update("DELETE FROM product WHERE product_code LIKE ?", marker + "%");
        jdbc.update("DELETE FROM coupon_template WHERE template_code LIKE ?", marker + "%");
        jdbc.update("DELETE FROM membership_product WHERE product_code LIKE ?", marker + "%");
        jdbc.update("DELETE FROM sys_user WHERE id=?", userId);
    }

    @Test
    void allFiveRewardsCompleteTheirRealApprovedTimelines() throws Exception {
        Result coupon = execute(RewardType.COUPON, 2);
        assertThat(count("sim_coupon_record", coupon.no())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_coupon WHERE user_id=?", Long.class, userId)).isEqualTo(2);
        assertAvailable(coupon.benefitId());

        Result points = execute(RewardType.POINTS, 300);
        assertThat(count("sim_points_record", points.no())).isOne();
        assertThat(jdbc.queryForObject("SELECT balance FROM points_account WHERE user_id=?", Long.class, userId)).isEqualTo(300L);
        assertAvailable(points.benefitId());

        Result membership = execute(RewardType.MEMBERSHIP, 2);
        assertThat(count("sim_membership_record", membership.no())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM membership_grant_record WHERE user_id=?", Long.class, userId)).isEqualTo(2);
        assertAvailable(membership.benefitId());

        Result product = execute(RewardType.PRODUCT, 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sim_logistics_record WHERE fulfillment_no=?", Long.class,
                "LOTTERY-BENEFIT-" + product.benefitId())).isZero();
        assertThat(product.no()).isNull();
        assertThat(status(product.benefitId())).isEqualTo("CLAIM_PENDING");

        Result chances = execute(RewardType.DRAW_CHANCE, 3);
        assertThat(jdbc.queryForObject("SELECT available_balance FROM draw_chance_account WHERE user_id=?", Long.class, userId)).isEqualTo(3L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM draw_chance_ledger WHERE user_id=? AND business_type='LOTTERY_REWARD'", Long.class, userId)).isOne();
        assertAvailable(chances.benefitId());
    }

    private Result execute(RewardType type, long quantity) throws Exception {
        Fixture fixture = fixture(type, quantity);
        String requestId = UUID.randomUUID().toString();
        requests.add(requestId);
        var draw = lottery.draw(new DrawCommand(requestId, fixture.activityId(), 1));
        long benefitId = draw.results().get(0).benefitId();
        MessageOutbox outbox = jdbc.queryForObject(
                "SELECT id FROM message_outbox WHERE payload ->> '$.requestId'=? AND event_type='PRIZE_FULFILLMENT_REQUESTED'",
                (rs, ignored) -> outboxes.selectById(rs.getLong(1)), requestId);
        DrawEventEnvelope envelope = json.readValue(outbox.getPayload(), DrawEventEnvelope.class);
        deliver(outbox);

        String no = jdbc.queryForObject("SELECT fulfillment_no FROM user_benefit WHERE id=?", String.class, benefitId);
        if (type == RewardType.COUPON || type == RewardType.POINTS || type == RewardType.MEMBERSHIP) {
            assertThat(no).isEqualTo("LOTTERY-BENEFIT-" + benefitId);
            assertThat(worker.runBatch()).isGreaterThanOrEqualTo(1);
            projection.project(benefitId);
        }
        consumerService.consume(envelope);
        worker.runBatch();
        projection.project(benefitId);
        return new Result(benefitId, no);
    }

    private Fixture fixture(RewardType type, long quantity) {
        Long targetId = switch (type) {
            case COUPON -> insert("""
                    INSERT INTO coupon_template(template_code,template_name,coupon_type,threshold_cent,
                      discount_cent,valid_from,valid_until,per_user_limit,stackable_with_membership,status)
                    VALUES(?,?,'NO_THRESHOLD',0,100,NOW(),DATE_ADD(NOW(),INTERVAL 30 DAY),10,1,1)
                    """, marker + "-coupon", marker + "券");
            case MEMBERSHIP -> insert("""
                    INSERT INTO membership_product(product_code,product_name,membership_level,card_type,
                      duration_days,price_cent,discount_basis_points,daily_draw_bonus,points_multiplier_basis_points,status)
                    VALUES(?,?,'GOLD','MONTH',30,0,9000,1,11000,1)
                    """, marker + "-member", marker + "会员");
            case PRODUCT -> productSku();
            default -> null;
        };
        long rewardId = insert("""
                INSERT INTO reward_definition(reward_code,reward_name,reward_type,target_id,quantity,status)
                VALUES(?,?,?,?,?,1)
                """, marker + "-" + type, marker + type, type.name(), targetId, quantity);
        String prizeType = switch (type) {
            case PRODUCT -> "PHYSICAL";
            default -> type.name();
        };
        long prizeId = insert("""
                INSERT INTO marketing_prize(prize_name,prize_type,prize_level,status,reward_definition_id)
                VALUES(?,?,'FIRST',1,?)
                """, marker + type, prizeType, rewardId);
        long activityId = insert("""
                INSERT INTO marketing_activity(activity_name,status,start_time,end_time,daily_limit,no_win_weight,created_by)
                VALUES(?,'RUNNING',NOW(3)-INTERVAL 1 HOUR,NOW(3)+INTERVAL 1 HOUR,10,0,?)
                """, marker + type, userId);
        activities.add(activityId);
        insert("""
                INSERT INTO marketing_activity_prize(activity_id,prize_id,weight,total_stock,remaining_stock,sort_order)
                VALUES(?,?,100,1,1,0)
                """, activityId, prizeId);
        return new Fixture(activityId);
    }

    private long productSku() {
        long productId = insert("INSERT INTO product(product_code,product_name,product_type,status) VALUES(?,?,'PHYSICAL',1)",
                marker + "-product", marker + "实物");
        return insert("""
                INSERT INTO product_sku(product_id,sku_code,sku_name,cash_enabled,points_enabled,status)
                VALUES(?,?,?,0,0,1)
                """, productId, marker + "-sku", "默认规格");
    }

    private void deliver(MessageOutbox outbox) throws Exception {
        MessagingProperties properties = messaging();
        new RedisStreamInitializer(redis, properties).initialize();
        DrawEventPublisher publisher = new RedisStreamDrawEventPublisher(redis, json, properties);
        RedisStreamDrawEventConsumer consumer = new RedisStreamDrawEventConsumer(redis, json, consumerService, properties);
        new OutboxRelayService(scoped(outbox.getId()), publisher, json, 1).relayBatch();
        assertThat(consumer.pollOnce()).isOne();
    }

    private MessageOutboxMapper scoped(long id) {
        MessageOutboxMapper mapper = Mockito.mock(MessageOutboxMapper.class);
        when(mapper.selectRelayCandidates(any(LocalDateTime.class), anyInt()))
                .thenAnswer(ignored -> List.of(outboxes.selectById(id)));
        when(mapper.claimForRelay(anyLong(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenAnswer(call -> outboxes.claimForRelay(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
        when(mapper.markSent(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenAnswer(call -> outboxes.markSent(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        when(mapper.markFailed(anyLong(), anyString(), anyString(), any(LocalDateTime.class)))
                .thenAnswer(call -> outboxes.markFailed(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
        return mapper;
    }

    private MessagingProperties messaging() {
        String stream = marker + ":" + UUID.randomUUID();
        streams.add(stream);
        return new MessagingProperties(false, "redis-stream", stream, marker + "-group-" + UUID.randomUUID(),
                marker + "-consumer-" + UUID.randomUUID(), 20,
                Duration.ofMillis(10), Duration.ofMillis(100), Duration.ofSeconds(30));
    }

    private void assertAvailable(long benefitId) {
        assertThat(status(benefitId)).isEqualTo("AVAILABLE");
    }

    private String status(long benefitId) {
        return jdbc.queryForObject("SELECT status FROM user_benefit WHERE id=?", String.class, benefitId);
    }

    private long count(String table, String no) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE fulfillment_no=?", Long.class, no);
    }

    private long insert(String sql, Object... args) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private record Fixture(long activityId) {}
    private record Result(long benefitId, String no) {}
}
