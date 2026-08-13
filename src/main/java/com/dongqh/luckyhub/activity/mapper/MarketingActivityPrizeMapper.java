package com.dongqh.luckyhub.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.activity.entity.MarketingActivityPrize;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MarketingActivityPrizeMapper extends BaseMapper<MarketingActivityPrize> {
    @Select("""
            SELECT * FROM marketing_activity_prize
            WHERE activity_id=#{activityId} AND prize_id=#{prizeId} FOR UPDATE
            """)
    MarketingActivityPrize lockByActivityAndPrize(@Param("activityId") long activityId,
                                                  @Param("prizeId") long prizeId);
}
