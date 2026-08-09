package com.dongqh.luckyhub.reward;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.prize.entity.MarketingPrize;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.reward.enums.RewardType;
import com.dongqh.luckyhub.reward.enums.RewardErrorCode;
import com.dongqh.luckyhub.reward.model.RewardSnapshot;
import com.dongqh.luckyhub.reward.service.RewardSnapshotService;
import com.dongqh.luckyhub.reward.support.RewardPrizeTypeMapping;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RewardSnapshotServiceTests {
    @Autowired RewardSnapshotService snapshots;
    @Autowired JdbcTemplate jdbc;

    @Test
    void mapsAllRewardTypesToCompatibilityPrizeTypes() {
        assertThat(RewardPrizeTypeMapping.toPrizeType(RewardType.PRODUCT)).isEqualTo(PrizeType.PHYSICAL);
        assertThat(RewardPrizeTypeMapping.toPrizeType(RewardType.COUPON)).isEqualTo(PrizeType.COUPON);
        assertThat(RewardPrizeTypeMapping.toPrizeType(RewardType.POINTS)).isEqualTo(PrizeType.POINTS);
        assertThat(RewardPrizeTypeMapping.toPrizeType(RewardType.MEMBERSHIP)).isEqualTo(PrizeType.MEMBERSHIP);
        assertThat(RewardPrizeTypeMapping.toPrizeType(RewardType.DRAW_CHANCE)).isEqualTo(PrizeType.DRAW_CHANCE);
    }

    @Test
    void createsStableCanonicalPointsSnapshotAndSkipsLegacyPrize() {
        String code = "PHASE5-POINTS-SNAPSHOT";
        jdbc.update("DELETE FROM reward_definition WHERE reward_code=?", code);
        jdbc.update("""
                INSERT INTO reward_definition(reward_code,reward_name,reward_type,target_id,quantity,status)
                VALUES(?,?,'POINTS',NULL,500,1)
                """, code, "500积分");
        Long rewardId = jdbc.queryForObject(
                "SELECT id FROM reward_definition WHERE reward_code=?", Long.class, code);
        try {
            MarketingPrize bound = prize(81001L, rewardId);
            MarketingPrize legacy = prize(81002L, null);
            RewardSnapshot first = snapshots.resolveForPrizes(List.of(bound, legacy)).get(bound.getId());
            RewardSnapshot second = snapshots.resolveForPrizes(List.of(bound)).get(bound.getId());

            assertThat(first.rewardDefinitionId()).isEqualTo(rewardId);
            assertThat(first.rewardType()).isEqualTo(RewardType.POINTS);
            assertThat(first.payloadJson()).isEqualTo("{\"points\":500,\"reason\":\"抽奖奖励\"}");
            assertThat(first.fingerprint()).matches("[0-9a-f]{64}").isEqualTo(second.fingerprint());
            assertThat(snapshots.resolveForPrizes(List.of(bound, legacy))).doesNotContainKey(legacy.getId());
        } finally {
            jdbc.update("DELETE FROM reward_definition WHERE reward_code=?", code);
        }
    }

    @Test
    void resolvesCouponMembershipProductAndDrawChancePayloads() {
        cleanupTargets();
        try {
            jdbc.update("""
                    INSERT INTO coupon_template(template_code,template_name,coupon_type,threshold_cent,
                      discount_cent,valid_from,valid_until,per_user_limit,stackable_with_membership,status)
                    VALUES('P5-COUPON','阶段5券','NO_THRESHOLD',0,100,NOW(),DATE_ADD(NOW(),INTERVAL 30 DAY),10,1,1)
                    """);
            jdbc.update("""
                    INSERT INTO membership_product(product_code,product_name,membership_level,card_type,
                      duration_days,price_cent,discount_basis_points,daily_draw_bonus,points_multiplier_basis_points,status)
                    VALUES('P5-MEMBER','阶段5会员','GOLD','MONTH',30,9900,9000,1,11000,1)
                    """);
            jdbc.update("""
                    INSERT INTO product(product_code,product_name,product_type,status)
                    VALUES('P5-PRODUCT','阶段5实物','PHYSICAL',1)
                    """);
            Long couponId = id("coupon_template", "template_code", "P5-COUPON");
            Long memberId = id("membership_product", "product_code", "P5-MEMBER");
            Long productId = id("product", "product_code", "P5-PRODUCT");
            jdbc.update("""
                    INSERT INTO product_sku(product_id,sku_code,sku_name,cash_enabled,points_enabled,status)
                    VALUES(?,'P5-SKU','红色',0,0,1)
                    """, productId);
            Long skuId = id("product_sku", "sku_code", "P5-SKU");
            insertReward("P5-R-COUPON", "COUPON", couponId, 2);
            insertReward("P5-R-MEMBER", "MEMBERSHIP", memberId, 2);
            insertReward("P5-R-PRODUCT", "PRODUCT", skuId, 1);
            insertReward("P5-R-CHANCE", "DRAW_CHANCE", null, 3);

            MarketingPrize coupon = prize(82001L, rewardId("P5-R-COUPON"));
            MarketingPrize member = prize(82002L, rewardId("P5-R-MEMBER"));
            MarketingPrize product = prize(82003L, rewardId("P5-R-PRODUCT"));
            MarketingPrize chance = prize(82004L, rewardId("P5-R-CHANCE"));
            var result = snapshots.resolveForPrizes(List.of(coupon, member, product, chance));

            assertThat(result.get(coupon.getId()).payloadJson())
                    .isEqualTo("{\"templateId\":" + couponId + ",\"templateCode\":\"P5-COUPON\",\"quantity\":2}");
            assertThat(result.get(member.getId()).payloadJson()).contains("\"durationDays\":60", "\"quantity\":2");
            assertThat(result.get(product.getId()).payloadJson()).contains("\"skuId\":" + skuId, "\"productName\":\"阶段5实物\"");
            assertThat(result.get(chance.getId()).payloadJson()).isEqualTo("{\"chances\":3}");
        } finally {
            cleanupTargets();
        }
    }

    @Test
    void convertsDisabledTargetAndDurationOverflowToStableBusinessError() {
        cleanupTargets();
        try {
            jdbc.update("""
                    INSERT INTO membership_product(product_code,product_name,membership_level,card_type,
                      duration_days,price_cent,discount_basis_points,daily_draw_bonus,points_multiplier_basis_points,status)
                    VALUES('P5-MEMBER','阶段5会员','GOLD','MONTH',30000000,9900,9000,1,11000,1)
                    """);
            Long memberId = id("membership_product", "product_code", "P5-MEMBER");
            insertReward("P5-R-MEMBER", "MEMBERSHIP", memberId, 100);
            MarketingPrize prize = prize(83001L, rewardId("P5-R-MEMBER"));
            assertThatThrownBy(() -> snapshots.resolveForPrizes(List.of(prize)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(RewardErrorCode.REWARD_TARGET_INVALID));

            jdbc.update("UPDATE membership_product SET duration_days=30,status=0 WHERE id=?", memberId);
            assertThatThrownBy(() -> snapshots.resolveForPrizes(List.of(prize)))
                    .isInstanceOf(BusinessException.class);
        } finally {
            cleanupTargets();
        }
    }

    private void insertReward(String code, String type, Long targetId, long quantity) {
        jdbc.update("INSERT INTO reward_definition(reward_code,reward_name,reward_type,target_id,quantity,status) VALUES(?,?,?, ?,?,1)",
                code, code, type, targetId, quantity);
    }

    private Long rewardId(String code) { return id("reward_definition", "reward_code", code); }

    private Long id(String table, String column, String value) {
        return jdbc.queryForObject("SELECT id FROM " + table + " WHERE " + column + "=?", Long.class, value);
    }

    private void cleanupTargets() {
        jdbc.update("DELETE FROM reward_definition WHERE reward_code LIKE 'P5-R-%'");
        jdbc.update("DELETE FROM product_sku WHERE sku_code='P5-SKU'");
        jdbc.update("DELETE FROM product WHERE product_code='P5-PRODUCT'");
        jdbc.update("DELETE FROM coupon_template WHERE template_code='P5-COUPON'");
        jdbc.update("DELETE FROM membership_product WHERE product_code='P5-MEMBER'");
    }

    private MarketingPrize prize(long id, Long rewardDefinitionId) {
        MarketingPrize prize = new MarketingPrize();
        prize.setId(id);
        prize.setPrizeName("测试奖品");
        prize.setPrizeType(PrizeType.POINTS);
        prize.setRewardDefinitionId(rewardDefinitionId);
        prize.setStatus(1);
        return prize;
    }
}
