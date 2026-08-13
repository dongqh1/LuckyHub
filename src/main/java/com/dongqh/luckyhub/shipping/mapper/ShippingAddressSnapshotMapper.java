package com.dongqh.luckyhub.shipping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.shipping.entity.ShippingAddressSnapshot;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ShippingAddressSnapshotMapper extends BaseMapper<ShippingAddressSnapshot> {
    @Select("SELECT * FROM shipping_address_snapshot WHERE source_type=#{type} AND source_id=#{sourceId}")
    ShippingAddressSnapshot selectBySource(@Param("type") ShippingSourceType type,
                                           @Param("sourceId") String sourceId);
}
