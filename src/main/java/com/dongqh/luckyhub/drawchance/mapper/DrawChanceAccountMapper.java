package com.dongqh.luckyhub.drawchance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.drawchance.entity.DrawChanceAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface DrawChanceAccountMapper extends BaseMapper<DrawChanceAccount> {
    @Insert("INSERT INTO draw_chance_account(user_id,available_balance,reserved_balance,version) VALUES(#{userId},0,0,0) ON DUPLICATE KEY UPDATE id=id")
    int insertIfAbsent(@Param("userId") long userId);

    @Select("SELECT * FROM draw_chance_account WHERE user_id=#{userId} FOR UPDATE")
    DrawChanceAccount lockByUserId(@Param("userId") long userId);

    @Select("SELECT * FROM draw_chance_account WHERE user_id=#{userId}")
    DrawChanceAccount selectByUserId(@Param("userId") long userId);

    @Update("UPDATE draw_chance_account SET available_balance=#{available},reserved_balance=#{reserved},version=version+1 WHERE id=#{id}")
    int updateBalances(@Param("id") long id, @Param("available") long available,
                       @Param("reserved") long reserved);
}
