package com.dongqh.luckyhub.benefit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.benefit.entity.UserBenefit;
import com.dongqh.luckyhub.benefit.enums.BenefitStatus;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface UserBenefitMapper extends BaseMapper<UserBenefit> {

    @Select("SELECT * FROM user_benefit WHERE id = #{id} FOR UPDATE")
    UserBenefit selectByIdForUpdate(@Param("id") long id);

    @Update("""
            UPDATE user_benefit
            SET status = #{target}, grant_error = NULL, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND status = #{expected}
            """)
    int transitionStatus(@Param("id") long id,
                         @Param("expected") BenefitStatus expected,
                         @Param("target") BenefitStatus target);

    @Update("""
            UPDATE user_benefit
            SET status = 'GRANT_FAILED', grant_error = #{safeError},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND status IN ('PENDING', 'GRANT_FAILED')
            """)
    int markGrantFailed(@Param("id") long id, @Param("safeError") String safeError);
}
