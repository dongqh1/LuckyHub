package com.dongqh.luckyhub.lottery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface LotteryDrawOrderMapper extends BaseMapper<LotteryDrawOrder> {

    @Insert("""
            INSERT INTO lottery_draw_order
                (request_id, user_id, activity_id, draw_count, draw_date, status)
            VALUES
                (#{order.requestId}, #{order.userId}, #{order.activityId},
                 #{order.drawCount}, #{order.drawDate}, 'PROCESSING')
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertProcessingIfAbsent(@Param("order") LotteryDrawOrder order);

    @Select("SELECT * FROM lottery_draw_order WHERE request_id = #{requestId}")
    LotteryDrawOrder selectByRequestId(@Param("requestId") String requestId);

    @Select("SELECT * FROM lottery_draw_order WHERE request_id = #{requestId} FOR UPDATE")
    LotteryDrawOrder selectByRequestIdForUpdate(@Param("requestId") String requestId);

    @Update("""
            UPDATE lottery_draw_order
            SET status = 'SUCCESS', fail_reason = NULL, completed_at = #{completedAt}
            WHERE id = #{orderId} AND status = 'PROCESSING'
            """)
    int markSuccessIfProcessing(
            @Param("orderId") long orderId,
            @Param("completedAt") java.time.LocalDateTime completedAt);

    @Update("""
            UPDATE lottery_draw_order
            SET status = 'FAILED', fail_reason = #{safeReason},
                completed_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{orderId} AND status = 'PROCESSING'
            """)
    int markFailedIfProcessing(
            @Param("orderId") long orderId,
            @Param("safeReason") String safeReason);
}
