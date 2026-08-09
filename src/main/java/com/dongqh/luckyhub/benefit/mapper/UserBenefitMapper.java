package com.dongqh.luckyhub.benefit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.benefit.entity.UserBenefit;
import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import com.dongqh.luckyhub.lottery.model.LotteryRewardIdentityRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface UserBenefitMapper extends BaseMapper<UserBenefit> {

    @Select("""
            SELECT b.id benefit_id,b.draw_record_id,r.order_id,o.request_id,
              o.user_id order_user_id,r.user_id record_user_id,b.user_id benefit_user_id,
              o.activity_id order_activity_id,r.activity_id record_activity_id,
              r.prize_id order_prize_id,r.prize_id record_prize_id,b.prize_id benefit_prize_id,
              r.prize_type record_prize_type,b.prize_type benefit_prize_type,
              o.status order_status,b.status benefit_status,
              r.reward_definition_id record_reward_definition_id,b.reward_definition_id benefit_reward_definition_id,
              r.reward_type record_reward_type,b.reward_type benefit_reward_type,
              r.reward_target_id record_reward_target_id,b.reward_target_id benefit_reward_target_id,
              r.reward_quantity record_reward_quantity,b.reward_quantity benefit_reward_quantity,
              CAST(r.reward_payload AS CHAR) record_reward_payload,
              CAST(b.reward_payload AS CHAR) benefit_reward_payload,
              r.reward_fingerprint record_reward_fingerprint,b.reward_fingerprint benefit_reward_fingerprint
            FROM user_benefit b
            JOIN lottery_draw_record r ON r.id=b.draw_record_id
            JOIN lottery_draw_order o ON o.id=r.order_id
            WHERE b.id=#{benefitId} FOR UPDATE
            """)
    LotteryRewardIdentityRow lockLotteryRewardIdentity(@Param("benefitId") long benefitId);

    @Select("SELECT * FROM user_benefit WHERE id = #{id} FOR UPDATE")
    UserBenefit selectByIdForUpdate(@Param("id") long id);

    @Update("""
            UPDATE user_benefit
            SET status = #{target}, grant_error = NULL, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND status = #{expected}
            """)
    int transitionStatus(@Param("id") long id,
                         @Param("expected") BenefitStatus expected,
                         @Param("target") BenefitStatus target);

    @Update("""
            UPDATE user_benefit
            SET status = 'GRANT_FAILED', grant_error = #{safeError},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND status IN ('PENDING', 'GRANT_FAILED')
            """)
    int markGrantFailed(@Param("id") long id, @Param("safeError") String safeError);

    @Update("UPDATE user_benefit SET fulfillment_no=#{fulfillmentNo},updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND fulfillment_no IS NULL")
    int bindFulfillmentNo(@Param("id") long id, @Param("fulfillmentNo") String fulfillmentNo);
}
