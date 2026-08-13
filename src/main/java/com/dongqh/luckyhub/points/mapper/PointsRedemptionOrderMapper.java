package com.dongqh.luckyhub.points.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.points.entity.PointsRedemptionOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface PointsRedemptionOrderMapper extends BaseMapper<PointsRedemptionOrder> {

    @Insert("""
            INSERT IGNORE INTO points_redemption_order (
                redemption_no, user_id, sku_id, quantity, unit_points, total_points,
                product_code, product_name, sku_code, sku_name, product_type, image_url,
                status, created_at, updated_at
            ) VALUES (
                #{order.redemptionNo}, #{order.userId}, #{order.skuId}, #{order.quantity},
                #{order.unitPoints}, #{order.totalPoints}, #{order.productCode},
                #{order.productName}, #{order.skuCode}, #{order.skuName},
                #{order.productType}, #{order.imageUrl}, #{order.status},
                CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "order.id")
    int claim(@Param("order") PointsRedemptionOrder order);

    @Update("""
            UPDATE points_redemption_order
            SET status = 'COMPLETED', updated_at = CURRENT_TIMESTAMP(3)
            WHERE redemption_no = #{redemptionNo} AND status = 'PROCESSING'
            """)
    int completeProcessing(@Param("redemptionNo") String redemptionNo);

    @Update("UPDATE points_redemption_order SET address_snapshot_id=#{snapshotId} WHERE id=#{id} AND address_snapshot_id IS NULL")
    int attachAddressSnapshot(@Param("id") long id, @Param("snapshotId") long snapshotId);

    @Update("UPDATE points_redemption_order SET shipping_order_id=#{shippingOrderId} WHERE id=#{id} AND shipping_order_id IS NULL")
    int attachShippingOrder(@Param("id") long id, @Param("shippingOrderId") long shippingOrderId);

    @Select("""
            SELECT * FROM points_redemption_order
            WHERE redemption_no = #{redemptionNo}
            FOR UPDATE
            """)
    PointsRedemptionOrder lockByRedemptionNo(@Param("redemptionNo") String redemptionNo);

    @Update("""
            UPDATE points_redemption_order
            SET status = 'REVERSED', reversal_no = #{reversalNo},
                failure_reason = #{reason}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE redemption_no = #{redemptionNo} AND status = 'COMPLETED'
            """)
    int reverseCompleted(@Param("redemptionNo") String redemptionNo,
                         @Param("reversalNo") String reversalNo,
                         @Param("reason") String reason);
}
