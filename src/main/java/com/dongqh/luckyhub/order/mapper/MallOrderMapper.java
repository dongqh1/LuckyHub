package com.dongqh.luckyhub.order.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.dongqh.luckyhub.order.entity.MallOrder; import org.apache.ibatis.annotations.*; import java.time.LocalDateTime; import java.util.List;
public interface MallOrderMapper extends BaseMapper<MallOrder> {
 @Select("SELECT * FROM mall_order WHERE order_no=#{orderNo} FOR UPDATE") MallOrder lockByOrderNo(@Param("orderNo") String orderNo);
 @Update("UPDATE mall_order SET status='PAID',paid_at=CURRENT_TIMESTAMP(3) WHERE order_no=#{orderNo} AND status='PENDING_PAYMENT'") int markPaid(@Param("orderNo") String orderNo);
 @Update("UPDATE mall_order SET status='CANCELLED',cancelled_at=CURRENT_TIMESTAMP(3),cancel_reason=#{reason} WHERE order_no=#{orderNo} AND status='PENDING_PAYMENT'") int cancel(@Param("orderNo") String orderNo,@Param("reason") String reason);
 @Select("SELECT * FROM mall_order WHERE status='PENDING_PAYMENT' AND payment_deadline<=#{cutoff} ORDER BY id LIMIT #{limit}") List<MallOrder> findExpired(@Param("cutoff") LocalDateTime cutoff,@Param("limit") int limit);
}
