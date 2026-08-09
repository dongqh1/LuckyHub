package com.dongqh.luckyhub.benefit;

import com.dongqh.luckyhub.benefit.entity.UserBenefit;
import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.benefit.handler.BenefitFulfillmentHandler;
import com.dongqh.luckyhub.benefit.handler.BenefitFulfillmentRouter;
import com.dongqh.luckyhub.benefit.handler.CouponFulfillmentHandler;
import com.dongqh.luckyhub.benefit.handler.DrawChanceFulfillmentHandler;
import com.dongqh.luckyhub.benefit.handler.MembershipFulfillmentHandler;
import com.dongqh.luckyhub.benefit.handler.PhysicalFulfillmentHandler;
import com.dongqh.luckyhub.benefit.handler.PointsFulfillmentHandler;
import com.dongqh.luckyhub.benefit.mapper.UserBenefitMapper;
import com.dongqh.luckyhub.benefit.service.BenefitFulfillmentService;
import com.dongqh.luckyhub.benefit.service.BenefitFulfillmentServiceImpl;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.lottery.config.MessagingProperties;
import com.dongqh.luckyhub.lottery.mapper.MessageConsumeRecordMapper;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class BenefitFulfillmentServiceTests {

    @Autowired BenefitFulfillmentService fulfillmentService;
    @Autowired UserBenefitMapper benefitMapper;
    @Autowired MessageConsumeRecordMapper consumeRecordMapper;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbcTemplate;

    private final List<Long> benefitIds = new ArrayList<>();
    private final List<String> eventIds = new ArrayList<>();

    @AfterEach
    void cleanExactRows() {
        eventIds.forEach(id -> jdbcTemplate.update("DELETE FROM message_consume_record WHERE event_id = ?", id));
        benefitIds.forEach(id -> jdbcTemplate.update("DELETE FROM user_benefit WHERE id = ?", id));
    }

    @Test
    void eachPrizeTypeUsesExactlyOneHandlerAndReachesItsDesignedStatus() {
        assertFulfilled(PrizeType.COUPON, BenefitStatus.AVAILABLE);
        assertFulfilled(PrizeType.POINTS, BenefitStatus.AVAILABLE);
        assertFulfilled(PrizeType.MEMBERSHIP, BenefitStatus.AVAILABLE);
        assertFulfilled(PrizeType.PHYSICAL, BenefitStatus.CLAIM_PENDING);
    }

    @Test
    void duplicateEventIsSuccessfulWithoutASecondHandlerSideEffect() {
        AtomicInteger effects = new AtomicInteger();
        BenefitFulfillmentService service = serviceWith(couponHandler(effects, false));
        long benefitId = insertBenefit(PrizeType.COUPON, BenefitStatus.PENDING);
        String eventId = eventId();

        service.fulfill(benefitId, eventId);
        service.fulfill(benefitId, eventId);

        assertThat(effects).hasValue(1);
        assertThat(consumeCount(eventId)).isOne();
    }

    @Test
    void handlerFailurePersistsOnlyASafeFailureAndCanBeRetried() {
        AtomicInteger effects = new AtomicInteger();
        long benefitId = insertBenefit(PrizeType.COUPON, BenefitStatus.PENDING);
        String firstEvent = eventId();

        assertThatThrownBy(() -> serviceWith(couponHandler(effects, true)).fulfill(benefitId, firstEvent))
                .isInstanceOf(BusinessException.class);

        UserBenefit failed = benefitMapper.selectById(benefitId);
        assertThat(failed.getStatus()).isEqualTo(BenefitStatus.GRANT_FAILED);
        assertThat(failed.getGrantError()).isEqualTo("权益发放失败");
        assertThat(failed.getGrantError()).doesNotContain("database-password");
        assertThat(consumeCount(firstEvent)).isZero();

        serviceWith(couponHandler(effects, false)).fulfill(benefitId, firstEvent);
        assertThat(benefitMapper.selectById(benefitId).getStatus()).isEqualTo(BenefitStatus.AVAILABLE);
        assertThat(benefitMapper.selectById(benefitId).getGrantError()).isNull();
        assertThat(consumeCount(firstEvent)).isOne();
    }

    @Test
    void databaseFailureRollsBackSuccessTransitionBeforePersistingGrantFailure() {
        long benefitId = insertBenefit(PrizeType.COUPON, BenefitStatus.PENDING);
        String tooLongEventId = "e".repeat(65);
        eventIds.add(tooLongEventId);

        assertThatThrownBy(() -> fulfillmentService.fulfill(benefitId, tooLongEventId))
                .isInstanceOf(BusinessException.class);

        assertThat(benefitMapper.selectById(benefitId).getStatus()).isEqualTo(BenefitStatus.GRANT_FAILED);
        assertThat(consumeCount(tooLongEventId)).isZero();
    }

    @Test
    void invalidTerminalStateIsRejectedWithoutConsumption() {
        long benefitId = insertBenefit(PrizeType.POINTS, BenefitStatus.AVAILABLE);
        String eventId = eventId();

        assertThatThrownBy(() -> fulfillmentService.fulfill(benefitId, eventId))
                .isInstanceOf(BusinessException.class);

        assertThat(benefitMapper.selectById(benefitId).getStatus()).isEqualTo(BenefitStatus.AVAILABLE);
        assertThat(consumeCount(eventId)).isZero();
    }

    @Test
    void concurrentDuplicateDeliveryRunsTheHandlerOnlyOnce() throws Exception {
        AtomicInteger effects = new AtomicInteger();
        BenefitFulfillmentService service = serviceWith(couponHandler(effects, false));
        long benefitId = insertBenefit(PrizeType.COUPON, BenefitStatus.PENDING);
        String eventId = eventId();
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    service.fulfill(benefitId, eventId);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(effects).hasValue(1);
        assertThat(consumeCount(eventId)).isOne();
        assertThat(benefitMapper.selectById(benefitId).getStatus()).isEqualTo(BenefitStatus.AVAILABLE);
    }

    @Test
    void fulfillmentNeverChangesDrawOrdersOrRecords() {
        long orderId = positiveId();
        long recordId = positiveId();
        String requestId = "task12-order-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO lottery_draw_order
                    (id, request_id, user_id, activity_id, draw_count, draw_date, status)
                VALUES (?, ?, 1, 1, 1, CURRENT_DATE, 'SUCCESS')
                """, orderId, requestId);
        jdbcTemplate.update("""
                INSERT INTO lottery_draw_record
                    (id, order_id, request_id, sequence_no, user_id, activity_id, result_type,
                     prize_id, prize_name, prize_type, draw_time)
                VALUES (?, ?, ?, 1, 1, 1, 'WIN', 1, 'snapshot', 'PHYSICAL', CURRENT_TIMESTAMP(3))
                """, recordId, orderId, requestId);
        try {
            long benefitId = insertBenefit(PrizeType.PHYSICAL, BenefitStatus.PENDING, recordId);
            fulfillmentService.fulfill(benefitId, eventId());

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM lottery_draw_order WHERE id = ?", String.class, orderId))
                    .isEqualTo("SUCCESS");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT prize_name FROM lottery_draw_record WHERE id = ?", String.class, recordId))
                    .isEqualTo("snapshot");
        } finally {
            jdbcTemplate.update("DELETE FROM lottery_draw_record WHERE id = ?", recordId);
            jdbcTemplate.update("DELETE FROM lottery_draw_order WHERE id = ?", orderId);
        }
    }

    @Test
    void routerRejectsMissingOrDuplicateHandlersAtConstruction() {
        assertThatThrownBy(() -> new BenefitFulfillmentRouter(List.of(new CouponFulfillmentHandler())))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new BenefitFulfillmentRouter(List.of(
                new CouponFulfillmentHandler(), new CouponFulfillmentHandler(),
                new PointsFulfillmentHandler(), new MembershipFulfillmentHandler(),
                new PhysicalFulfillmentHandler())))
                .isInstanceOf(IllegalStateException.class);
    }

    private void assertFulfilled(PrizeType type, BenefitStatus expected) {
        long benefitId = insertBenefit(type, BenefitStatus.PENDING);
        String eventId = eventId();
        fulfillmentService.fulfill(benefitId, eventId);
        UserBenefit benefit = benefitMapper.selectById(benefitId);
        assertThat(benefit.getStatus()).isEqualTo(expected);
        assertThat(benefit.getGrantError()).isNull();
        assertThat(consumeCount(eventId)).isOne();
    }

    private BenefitFulfillmentService serviceWith(BenefitFulfillmentHandler coupon) {
        BenefitFulfillmentRouter router = new BenefitFulfillmentRouter(List.of(
                coupon, new PointsFulfillmentHandler(), new MembershipFulfillmentHandler(),
                new PhysicalFulfillmentHandler(), new DrawChanceFulfillmentHandler()));
        return new BenefitFulfillmentServiceImpl(benefitMapper, consumeRecordMapper, router,
                properties(), transactionManager);
    }

    private BenefitFulfillmentHandler couponHandler(AtomicInteger effects, boolean fail) {
        return new BenefitFulfillmentHandler() {
            @Override public PrizeType prizeType() { return PrizeType.COUPON; }
            @Override public BenefitStatus fulfill(UserBenefit benefit, String eventId) {
                effects.incrementAndGet();
                if (fail) {
                    throw new IllegalStateException("database-password=secret");
                }
                return BenefitStatus.AVAILABLE;
            }
        };
    }

    private MessagingProperties properties() {
        return new MessagingProperties(false, "redis-stream", "task12", "task12-group",
                "task12-core", 20, Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(30));
    }

    private long insertBenefit(PrizeType type, BenefitStatus status) {
        return insertBenefit(type, status, positiveId());
    }

    private long insertBenefit(PrizeType type, BenefitStatus status, long drawRecordId) {
        jdbcTemplate.update("""
                INSERT INTO user_benefit
                    (draw_record_id, user_id, prize_id, prize_type, quantity, status, obtained_at)
                VALUES (?, ?, ?, ?, 1, ?, ?)
                """, drawRecordId, positiveId(), positiveId(), type.name(), status.name(), LocalDateTime.now());
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM user_benefit WHERE draw_record_id = ?", Long.class, drawRecordId);
        benefitIds.add(id);
        return id;
    }

    private String eventId() {
        String value = UUID.randomUUID().toString();
        eventIds.add(value);
        return value;
    }

    private long consumeCount(String eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message_consume_record WHERE event_id = ?", Long.class, eventId);
    }

    private long positiveId() {
        long value = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        return value == 0 ? 1 : value;
    }
}
