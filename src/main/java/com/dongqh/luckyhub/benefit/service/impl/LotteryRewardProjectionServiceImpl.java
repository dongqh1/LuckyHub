package com.dongqh.luckyhub.benefit.service.impl;

import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.benefit.service.LotteryRewardProjectionService;
import com.dongqh.luckyhub.coupon.dto.IssueCouponCommand;
import com.dongqh.luckyhub.coupon.service.CouponService;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentStatus;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType;
import com.dongqh.luckyhub.fulfillment.model.CouponFulfillmentPayload;
import com.dongqh.luckyhub.fulfillment.model.MembershipFulfillmentPayload;
import com.dongqh.luckyhub.fulfillment.model.PointsFulfillmentPayload;
import com.dongqh.luckyhub.membership.dto.PurchaseMembershipCommand;
import com.dongqh.luckyhub.membership.service.MembershipService;
import com.dongqh.luckyhub.points.dto.PointsMutationCommand;
import com.dongqh.luckyhub.points.enums.PointsBusinessType;
import com.dongqh.luckyhub.points.service.PointsAccountService;
import com.dongqh.luckyhub.reward.enums.RewardType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Service
public class LotteryRewardProjectionServiceImpl implements LotteryRewardProjectionService {
    private static final int MAX_BATCH = 100;
    private static final int MAX_QUANTITY = 100;
    private static final String PROVIDER_FAILURE = "权益发放失败";
    private static final String LOCAL_FAILURE = "本地资产投影失败";

    private final JdbcTemplate jdbc;
    private final CouponService coupons;
    private final PointsAccountService points;
    private final MembershipService memberships;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;

    public LotteryRewardProjectionServiceImpl(JdbcTemplate jdbc, CouponService coupons,
            PointsAccountService points, MembershipService memberships, ObjectMapper json,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.coupons = coupons;
        this.points = points;
        this.memberships = memberships;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public int projectBatch(int limit) {
        if (limit <= 0) return 0;
        List<Long> benefitIds = jdbc.queryForList("""
                SELECT b.id
                FROM user_benefit b
                JOIN fulfillment_task t ON t.fulfillment_no=b.fulfillment_no
                WHERE t.source_type='LOTTERY_BENEFIT'
                  AND b.status IN ('PENDING','GRANT_FAILED')
                  AND t.status IN ('SUCCEEDED','QUARANTINED','TERMINATED')
                ORDER BY b.id
                LIMIT ?
                """, Long.class, Math.min(limit, MAX_BATCH));
        benefitIds.forEach(this::project);
        return benefitIds.size();
    }

    @Override
    public void project(long benefitId) {
        if (benefitId <= 0) throw new IllegalArgumentException("benefitId必须为正数");
        try {
            transactions.executeWithoutResult(ignored -> projectLocked(benefitId));
        } catch (RuntimeException failure) {
            transactions.executeWithoutResult(ignored -> markLocalFailure(benefitId));
        }
    }

    private void projectLocked(long benefitId) {
        ProjectionRow row = lock(benefitId);
        if (row == null || row.benefitStatus == BenefitStatus.AVAILABLE) return;
        if (row.benefitStatus != BenefitStatus.PENDING
                && row.benefitStatus != BenefitStatus.GRANT_FAILED) return;
        requireIdentity(row);
        if (row.fulfillmentStatus == FulfillmentStatus.QUARANTINED
                || row.fulfillmentStatus == FulfillmentStatus.TERMINATED) {
            markFailure(row.benefitId, PROVIDER_FAILURE);
            return;
        }
        if (row.fulfillmentStatus != FulfillmentStatus.SUCCEEDED) return;

        switch (row.rewardType) {
            case COUPON -> projectCoupons(row);
            case POINTS -> projectPoints(row);
            case MEMBERSHIP -> projectMembership(row);
            default -> throw new IllegalStateException("该奖励类型不需要履约投影");
        }
        int changed = jdbc.update("""
                UPDATE user_benefit SET status='AVAILABLE',grant_error=NULL,
                  updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=? AND status IN ('PENDING','GRANT_FAILED')
                """, row.benefitId);
        if (changed != 1) throw new IllegalStateException("权益状态并发冲突");
    }

    private void projectCoupons(ProjectionRow row) {
        CouponFulfillmentPayload payload = read(row.requestPayload, CouponFulfillmentPayload.class);
        int quantity = bounded(row.rewardQuantity);
        if (payload.quantity() != quantity || row.rewardTargetId == null) {
            throw new IllegalStateException("优惠券冻结快照不一致");
        }
        for (int index = 1; index <= quantity; index++) {
            coupons.issue(new IssueCouponCommand(
                    row.fulfillmentNo + "-C-" + index,
                    "LR-C-" + row.benefitId + "-" + index,
                    row.rewardTargetId, row.userId));
        }
    }

    private void projectPoints(ProjectionRow row) {
        PointsFulfillmentPayload payload = read(row.requestPayload, PointsFulfillmentPayload.class);
        if (payload.points() != row.rewardQuantity) {
            throw new IllegalStateException("积分冻结快照不一致");
        }
        points.credit(new PointsMutationCommand(row.userId, PointsBusinessType.LOTTERY_REWARD,
                row.fulfillmentNo, payload.points(), "抽奖奖励"));
    }

    private void projectMembership(ProjectionRow row) {
        read(row.requestPayload, MembershipFulfillmentPayload.class);
        int quantity = bounded(row.rewardQuantity);
        if (row.rewardTargetId == null) throw new IllegalStateException("会员冻结快照缺少产品");
        for (int index = 1; index <= quantity; index++) {
            memberships.purchase(new PurchaseMembershipCommand(
                    row.fulfillmentNo + "-M-" + index, row.rewardTargetId, row.userId));
        }
    }

    private void requireIdentity(ProjectionRow row) {
        if (!"LOTTERY_BENEFIT".equals(row.sourceType)
                || !Long.toString(row.benefitId).equals(row.sourceId)
                || !row.userId.equals(row.targetUserId)
                || expectedType(row.rewardType) != row.fulfillmentType) {
            throw new IllegalStateException("履约任务与权益身份不一致");
        }
    }

    private FulfillmentType expectedType(RewardType rewardType) {
        return switch (rewardType) {
            case COUPON -> FulfillmentType.COUPON;
            case POINTS -> FulfillmentType.POINTS;
            case MEMBERSHIP -> FulfillmentType.MEMBERSHIP;
            default -> throw new IllegalStateException("奖励类型不支持投影");
        };
    }

    private int bounded(long quantity) {
        if (quantity <= 0 || quantity > MAX_QUANTITY) {
            throw new IllegalStateException("单个奖励投影数量必须在1到100之间");
        }
        return Math.toIntExact(quantity);
    }

    private <T> T read(String payload, Class<T> type) {
        try {
            return json.readValue(payload, type);
        } catch (JacksonException error) {
            throw new IllegalStateException("履约快照无法解析", error);
        }
    }

    private ProjectionRow lock(long benefitId) {
        return jdbc.query("""
                SELECT b.id,b.user_id,b.reward_type,b.reward_target_id,b.reward_quantity,
                  b.fulfillment_no,b.status benefit_status,
                  t.source_type,t.source_id,t.fulfillment_type,t.target_user_id,
                  CAST(t.request_payload AS CHAR) request_payload,t.status fulfillment_status
                FROM user_benefit b
                LEFT JOIN fulfillment_task t ON t.fulfillment_no=b.fulfillment_no
                WHERE b.id=? FOR UPDATE
                """, rs -> rs.next() ? row(rs) : null, benefitId);
    }

    private ProjectionRow row(ResultSet rs) throws SQLException {
        String rewardType = rs.getString("reward_type");
        String fulfillmentType = rs.getString("fulfillment_type");
        String fulfillmentStatus = rs.getString("fulfillment_status");
        Object target = rs.getObject("reward_target_id");
        Object quantity = rs.getObject("reward_quantity");
        Object targetUser = rs.getObject("target_user_id");
        return new ProjectionRow(
                rs.getLong("id"), rs.getLong("user_id"),
                rewardType == null ? null : RewardType.valueOf(rewardType),
                target == null ? null : ((Number) target).longValue(),
                quantity == null ? 0 : ((Number) quantity).longValue(),
                rs.getString("fulfillment_no"), BenefitStatus.valueOf(rs.getString("benefit_status")),
                rs.getString("source_type"), rs.getString("source_id"),
                fulfillmentType == null ? null : FulfillmentType.valueOf(fulfillmentType),
                targetUser == null ? null : ((Number) targetUser).longValue(),
                rs.getString("request_payload"),
                fulfillmentStatus == null ? null : FulfillmentStatus.valueOf(fulfillmentStatus));
    }

    private void markLocalFailure(long benefitId) {
        jdbc.update("""
                UPDATE user_benefit SET status='GRANT_FAILED',grant_error=?,
                  updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=? AND status IN ('PENDING','GRANT_FAILED')
                """, LOCAL_FAILURE, benefitId);
    }

    private void markFailure(long benefitId, String safeError) {
        jdbc.update("""
                UPDATE user_benefit SET status='GRANT_FAILED',grant_error=?,
                  updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=? AND status IN ('PENDING','GRANT_FAILED')
                """, safeError, benefitId);
    }

    private record ProjectionRow(long benefitId, Long userId, RewardType rewardType,
            Long rewardTargetId, long rewardQuantity, String fulfillmentNo,
            BenefitStatus benefitStatus, String sourceType, String sourceId,
            FulfillmentType fulfillmentType, Long targetUserId, String requestPayload,
            FulfillmentStatus fulfillmentStatus) {
    }
}
