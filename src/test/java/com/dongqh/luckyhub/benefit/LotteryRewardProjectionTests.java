package com.dongqh.luckyhub.benefit;

import com.dongqh.luckyhub.benefit.service.LotteryRewardProjectionService;
import com.dongqh.luckyhub.coupon.dto.CreateCouponTemplateCommand;
import com.dongqh.luckyhub.coupon.enums.CouponType;
import com.dongqh.luckyhub.coupon.service.CouponService;
import com.dongqh.luckyhub.membership.dto.CreateMembershipProductCommand;
import com.dongqh.luckyhub.membership.enums.MembershipCardType;
import com.dongqh.luckyhub.membership.service.MembershipService;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import com.dongqh.luckyhub.rbac.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LotteryRewardProjectionTests {
    @Autowired LotteryRewardProjectionService projection;
    @Autowired CouponService coupons;
    @Autowired MembershipService memberships;
    @Autowired SysUserMapper users;
    @Autowired JdbcTemplate jdbc;

    private long userId;

    @BeforeEach
    void setUp() {
        cleanBusinessRows();
        SysUser user = new SysUser();
        user.setUsername("projection-" + UUID.randomUUID());
        user.setPassword("x");
        user.setNickname("奖励投影用户");
        user.setStatus(1);
        users.insert(user);
        userId = user.getId();
    }

    @AfterEach
    void tearDown() {
        cleanBusinessRows();
        users.deleteById(userId);
    }

    @Test
    void projectsTwoCouponsWithDeterministicIdsAndNoDuplicates() {
        long templateId = coupons.createTemplate(new CreateCouponTemplateCommand(
                "LR-TWO", "两张奖励券", CouponType.NO_THRESHOLD, 0L, 500L, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30), 10, true)).id();
        long benefitId = insertBenefit("COUPON", templateId, 2, "LR-COUPON-2");
        insertTask(benefitId, "COUPON", "{\"couponTemplateCode\":\"LR-TWO\",\"quantity\":2}", "SUCCEEDED");

        projection.project(benefitId);
        projection.project(benefitId);

        assertThat(count("coupon_issue_record")).isEqualTo(2);
        assertThat(count("user_coupon")).isEqualTo(2);
        assertThat(strings("SELECT business_no FROM coupon_issue_record ORDER BY business_no"))
                .containsExactly("LR-COUPON-2-C-1", "LR-COUPON-2-C-2");
        assertThat(strings("SELECT coupon_no FROM user_coupon ORDER BY coupon_no"))
                .containsExactly("LR-C-" + benefitId + "-1", "LR-C-" + benefitId + "-2");
        assertBenefit(benefitId, "AVAILABLE", null);
    }

    @Test
    void projectsPointsAndMembershipQuantityIntoUsableAssets() {
        long pointsBenefit = insertBenefit("POINTS", null, 300, "LR-POINTS-300");
        insertTask(pointsBenefit, "POINTS", "{\"points\":300,\"reason\":\"抽奖奖励\"}", "SUCCEEDED");
        projection.project(pointsBenefit);

        assertThat(jdbc.queryForObject("SELECT balance FROM points_account WHERE user_id=?", Long.class, userId))
                .isEqualTo(300L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM points_ledger WHERE business_type='LOTTERY_REWARD' AND business_id=?", Long.class, "LR-POINTS-300"))
                .isOne();
        assertBenefit(pointsBenefit, "AVAILABLE", null);

        long productId = memberships.createProduct(new CreateMembershipProductCommand(
                "LR-MONTH", "奖励月卡", "MEMBER", MembershipCardType.MONTH,
                30, 1000L, 9500, 1, 10000)).id();
        long membershipBenefit = insertBenefit("MEMBERSHIP", productId, 2, "LR-MEMBER-2");
        insertTask(membershipBenefit, "MEMBERSHIP", "{\"membershipCode\":\"LR-MONTH\",\"durationDays\":30}", "SUCCEEDED");
        projection.project(membershipBenefit);
        LocalDateTime firstExpiry = jdbc.queryForObject("SELECT expires_at FROM user_membership WHERE user_id=?", LocalDateTime.class, userId);
        projection.project(membershipBenefit);

        assertThat(count("membership_grant_record")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT expires_at FROM user_membership WHERE user_id=?", LocalDateTime.class, userId))
                .isEqualTo(firstExpiry);
        assertBenefit(membershipBenefit, "AVAILABLE", null);
    }

    @Test
    void mapsProviderTerminalStateToFailureAndRecoversAfterAdminRetry() {
        long benefitId = insertBenefit("POINTS", null, 88, "LR-RETRY");
        insertTask(benefitId, "POINTS", "{\"points\":88,\"reason\":\"抽奖奖励\"}", "QUARANTINED");

        projection.project(benefitId);
        assertBenefit(benefitId, "GRANT_FAILED", "权益发放失败");
        assertThat(count("points_ledger")).isZero();

        jdbc.update("UPDATE fulfillment_task SET status='SUCCEEDED' WHERE fulfillment_no='LR-RETRY'");
        projection.project(benefitId);
        assertBenefit(benefitId, "AVAILABLE", null);
        assertThat(count("points_ledger")).isOne();
    }

    @Test
    void rollsBackLocalEffectsStoresSafeErrorAndRetriesLater() {
        long templateId = coupons.createTemplate(new CreateCouponTemplateCommand(
                "LR-PAUSE", "暂停奖励券", CouponType.NO_THRESHOLD, 0L, 100L, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30), 10, true)).id();
        jdbc.update("UPDATE coupon_template SET status=0 WHERE id=?", templateId);
        long benefitId = insertBenefit("COUPON", templateId, 2, "LR-LOCAL-RETRY");
        insertTask(benefitId, "COUPON", "{\"couponTemplateCode\":\"LR-PAUSE\",\"quantity\":2}", "SUCCEEDED");

        projection.project(benefitId);
        assertBenefit(benefitId, "GRANT_FAILED", "本地资产投影失败");
        assertThat(count("coupon_issue_record")).isZero();
        assertThat(count("user_coupon")).isZero();

        jdbc.update("UPDATE coupon_template SET status=1 WHERE id=?", templateId);
        projection.project(benefitId);
        assertBenefit(benefitId, "AVAILABLE", null);
        assertThat(count("coupon_issue_record")).isEqualTo(2);
    }

    @Test
    void batchIsBoundedAndProcessesInBenefitOrder() {
        long first = insertBenefit("POINTS", null, 10, "LR-BATCH-1");
        long second = insertBenefit("POINTS", null, 20, "LR-BATCH-2");
        insertTask(first, "POINTS", "{\"points\":10,\"reason\":\"抽奖奖励\"}", "SUCCEEDED");
        insertTask(second, "POINTS", "{\"points\":20,\"reason\":\"抽奖奖励\"}", "SUCCEEDED");

        assertThat(projection.projectBatch(1)).isOne();
        assertBenefit(first, "AVAILABLE", null);
        assertBenefit(second, "PENDING", null);
        assertThat(projection.projectBatch(1000)).isOne();
        assertBenefit(second, "AVAILABLE", null);
    }

    private long insertBenefit(String type, Long targetId, long quantity, String no) {
        jdbc.update("""
                INSERT INTO user_benefit
                  (draw_record_id,user_id,prize_id,prize_type,reward_definition_id,reward_type,
                   reward_target_id,reward_quantity,reward_payload,reward_fingerprint,fulfillment_no,
                   quantity,status,obtained_at)
                VALUES (?,?,?,?,?,?,?,?,CAST(? AS JSON),?,?,1,'PENDING',CURRENT_TIMESTAMP(3))
                """, positiveId(), userId, positiveId(), type, positiveId(), type, targetId, quantity,
                "{}", "f".repeat(64), no);
        return jdbc.queryForObject("SELECT id FROM user_benefit WHERE fulfillment_no=?", Long.class, no);
    }

    private void insertTask(long benefitId, String type, String payload, String status) {
        String no = jdbc.queryForObject("SELECT fulfillment_no FROM user_benefit WHERE id=?", String.class, benefitId);
        jdbc.update("""
                INSERT INTO fulfillment_task
                  (fulfillment_no,source_type,source_id,fulfillment_type,target_user_id,request_payload,
                   request_fingerprint,status,attempt_count,max_attempts,version)
                VALUES (?,'LOTTERY_BENEFIT',?,?,?,CAST(? AS JSON),?,?,0,5,0)
                """, no, Long.toString(benefitId), type, userId, payload, "a".repeat(64), status);
    }

    private void assertBenefit(long id, String status, String error) {
        assertThat(jdbc.queryForMap("SELECT status,grant_error FROM user_benefit WHERE id=?", id))
                .containsEntry("status", status).containsEntry("grant_error", error);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private java.util.List<String> strings(String sql) {
        return jdbc.queryForList(sql, String.class);
    }

    private long positiveId() {
        long value = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        return value == 0 ? 1 : value;
    }

    private void cleanBusinessRows() {
        jdbc.update("DELETE FROM coupon_issue_record");
        jdbc.update("DELETE FROM user_coupon");
        jdbc.update("DELETE FROM coupon_template");
        jdbc.update("DELETE FROM membership_grant_record");
        jdbc.update("DELETE FROM user_membership");
        jdbc.update("DELETE FROM membership_product");
        jdbc.update("DELETE FROM points_ledger");
        jdbc.update("DELETE FROM points_account");
        jdbc.update("DELETE FROM fulfillment_attempt");
        jdbc.update("DELETE FROM fulfillment_quarantine");
        jdbc.update("DELETE FROM fulfillment_task");
        jdbc.update("DELETE FROM user_benefit");
    }
}
