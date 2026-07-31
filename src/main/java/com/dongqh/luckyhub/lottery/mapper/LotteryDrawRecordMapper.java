package com.dongqh.luckyhub.lottery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.lottery.entity.LotteryDrawRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface LotteryDrawRecordMapper extends BaseMapper<LotteryDrawRecord> {
    @Select("""
            SELECT r.*, b.id AS benefit_id
            FROM lottery_draw_record r
            LEFT JOIN user_benefit b ON b.draw_record_id = r.id
            WHERE r.order_id = #{orderId}
            ORDER BY r.sequence_no
            """)
    List<LotteryDrawRecord> selectByOrderId(@Param("orderId") long orderId);

    @Select("""
            <script>
            SELECT r.*, b.id AS benefit_id
            FROM lottery_draw_record r
            LEFT JOIN user_benefit b ON b.draw_record_id = r.id
            WHERE r.order_id IN
            <foreach collection='orderIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            ORDER BY r.order_id, r.sequence_no
            </script>
            """)
    List<LotteryDrawRecord> selectByOrderIds(@Param("orderIds") List<Long> orderIds);
}
