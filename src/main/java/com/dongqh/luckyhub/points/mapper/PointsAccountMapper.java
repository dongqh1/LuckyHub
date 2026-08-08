package com.dongqh.luckyhub.points.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.points.entity.PointsAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface PointsAccountMapper extends BaseMapper<PointsAccount> {

    @Insert("""
            INSERT IGNORE INTO points_account (
                user_id, balance, version, created_at, updated_at
            ) VALUES (
                #{userId}, 0, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
            )
            """)
    int ensureAccount(@Param("userId") long userId);

    @Update("""
            UPDATE points_account
            SET balance = balance - #{amount}, version = version + 1
            WHERE user_id = #{userId} AND balance >= #{amount}
            """)
    int debitIfSufficient(@Param("userId") long userId, @Param("amount") long amount);

    @Update("""
            UPDATE points_account
            SET balance = balance + #{amount}, version = version + 1
            WHERE user_id = #{userId}
              AND balance <= 9223372036854775807 - #{amount}
            """)
    int creditIfNoOverflow(@Param("userId") long userId, @Param("amount") long amount);
}
