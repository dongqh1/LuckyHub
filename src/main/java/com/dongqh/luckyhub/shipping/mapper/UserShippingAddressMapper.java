package com.dongqh.luckyhub.shipping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.shipping.entity.UserShippingAddress;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserShippingAddressMapper extends BaseMapper<UserShippingAddress> {
    @Select("SELECT * FROM user_shipping_address WHERE id=#{id} FOR UPDATE")
    UserShippingAddress lockById(@Param("id") long id);

    @Select("SELECT * FROM user_shipping_address WHERE user_id=#{userId} AND status='ACTIVE' ORDER BY id FOR UPDATE")
    List<UserShippingAddress> lockActiveByUser(@Param("userId") long userId);
}
