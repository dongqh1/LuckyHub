package com.dongqh.luckyhub.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.activity.entity.MarketingActivity;
import org.apache.ibatis.annotations.Update;

public interface MarketingActivityMapper extends BaseMapper<MarketingActivity> {

    @Update("""
            UPDATE marketing_activity
            SET status = 'RUNNING'
            WHERE status = 'SCHEDULED'
              AND start_time <= NOW(3)
              AND end_time > NOW(3)
            """)
    int promoteScheduledToRunning();

    @Update("""
            UPDATE marketing_activity
            SET status = 'ENDED'
            WHERE status IN ('SCHEDULED', 'RUNNING')
              AND end_time <= NOW(3)
            """)
    int finishExpiredActivities();
}
