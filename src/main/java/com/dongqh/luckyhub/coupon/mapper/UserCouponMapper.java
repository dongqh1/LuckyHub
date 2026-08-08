package com.dongqh.luckyhub.coupon.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.dongqh.luckyhub.coupon.entity.UserCoupon; import org.apache.ibatis.annotations.*;
public interface UserCouponMapper extends BaseMapper<UserCoupon> {
 @Update("UPDATE user_coupon SET status='LOCKED',locked_order_no=#{orderNo},version=version+1 WHERE id=#{id} AND user_id=#{userId} AND status='AVAILABLE' AND valid_from<=CURRENT_TIMESTAMP(3) AND valid_until>CURRENT_TIMESTAMP(3)") int lock(@Param("id") long id,@Param("userId") long userId,@Param("orderNo") String orderNo);
 @Update("UPDATE user_coupon SET status='USED',used_order_no=#{orderNo},version=version+1 WHERE id=#{id} AND status='LOCKED' AND locked_order_no=#{orderNo}") int use(@Param("id") long id,@Param("orderNo") String orderNo);
 @Update("UPDATE user_coupon SET status='AVAILABLE',locked_order_no=NULL,version=version+1 WHERE id=#{id} AND status='LOCKED' AND locked_order_no=#{orderNo}") int release(@Param("id") long id,@Param("orderNo") String orderNo);
 @Update("UPDATE user_coupon SET status='EXPIRED',version=version+1 WHERE status='AVAILABLE' AND valid_until<=CURRENT_TIMESTAMP(3)") int expireAvailable();
}
