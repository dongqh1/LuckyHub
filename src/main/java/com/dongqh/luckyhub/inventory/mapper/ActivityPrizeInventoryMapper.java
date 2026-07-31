package com.dongqh.luckyhub.inventory.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ActivityPrizeInventoryMapper {

    @Update("""
            UPDATE marketing_activity_prize
            SET remaining_stock = remaining_stock - 1
            WHERE id = #{activityPrizeId}
              AND remaining_stock > 0
            """)
    int decrementIfAvailable(@Param("activityPrizeId") long activityPrizeId);
}
