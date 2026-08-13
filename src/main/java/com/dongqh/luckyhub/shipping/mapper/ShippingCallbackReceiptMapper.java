package com.dongqh.luckyhub.shipping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.shipping.entity.ShippingCallbackReceipt;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ShippingCallbackReceiptMapper extends BaseMapper<ShippingCallbackReceipt> {
    @Select("SELECT * FROM shipping_callback_receipt WHERE callback_id=#{callbackId}")
    ShippingCallbackReceipt selectByCallbackId(@Param("callbackId") String callbackId);
}
