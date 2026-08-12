package com.dongqh.luckyhub.shipping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.shipping.entity.ShippingOrder;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ShippingOrderMapper extends BaseMapper<ShippingOrder> {
    @Select("SELECT * FROM shipping_order WHERE source_type=#{type} AND source_id=#{sourceId} FOR UPDATE")
    ShippingOrder lockBySource(@Param("type") ShippingSourceType type,
                               @Param("sourceId") String sourceId);

    @Select("SELECT * FROM shipping_order WHERE shipping_no=#{shippingNo} FOR UPDATE")
    ShippingOrder lockByShippingNo(@Param("shippingNo") String shippingNo);

    @Select("SELECT * FROM shipping_order WHERE waybill_no=#{waybillNo} FOR UPDATE")
    ShippingOrder lockByWaybillNo(@Param("waybillNo") String waybillNo);

    @Select("SELECT * FROM shipping_order WHERE fulfillment_no=#{fulfillmentNo}")
    ShippingOrder selectByFulfillmentNo(@Param("fulfillmentNo") String fulfillmentNo);

    @Select("SELECT * FROM shipping_order WHERE claim_request_id=#{claimRequestId}")
    ShippingOrder selectByClaimRequestId(@Param("claimRequestId") String claimRequestId);

    @Select("SELECT * FROM shipping_order WHERE shipping_no=#{shippingNo}")
    ShippingOrder selectByShippingNo(@Param("shippingNo") String shippingNo);

    @Select("SELECT * FROM shipping_order WHERE id=#{id} FOR UPDATE")
    ShippingOrder lockById(@Param("id") long id);

    @Select("""
            SELECT so.id FROM fulfillment_task ft
            JOIN shipping_order so ON so.fulfillment_no=ft.fulfillment_no
            WHERE ft.fulfillment_type='LOGISTICS'
              AND ft.status IN ('PENDING','RETRY_WAITING','RECONCILING','SUCCEEDED','QUARANTINED','TERMINATED')
              AND so.status NOT IN ('DELIVERED','TERMINATED')
            ORDER BY ft.id ASC LIMIT #{limit}
            """)
    List<Long> selectProjectionCandidateIds(@Param("limit") int limit);
}
