package com.dongqh.luckyhub.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_user_role")
public class SysUserRole {

    private Long userId;

    private Long roleId;
}
