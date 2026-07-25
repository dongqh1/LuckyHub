package com.dongqh.luckyhub.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_role_permission")
public class SysRolePermission {

    private Long roleId;

    private Long permissionId;
}
