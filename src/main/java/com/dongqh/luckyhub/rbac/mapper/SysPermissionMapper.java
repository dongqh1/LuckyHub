package com.dongqh.luckyhub.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.rbac.entity.SysPermission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    @Select("""
            SELECT DISTINCT
                p.id,
                p.permission_code,
                p.permission_name,
                p.created_at
            FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role r ON r.id = ur.role_id AND r.status = 1
            JOIN sys_role_permission rp ON rp.role_id = r.id
            JOIN sys_permission p ON p.id = rp.permission_id
            WHERE u.id = #{userId}
              AND u.status = 1
            ORDER BY p.permission_code
            """)
    List<SysPermission> selectEffectivePermissionsByUserId(@Param("userId") Long userId);
}
