# ADMIN 角色与基础权限初始化设计

## 目标

通过独立的 Flyway V2 初始化 `ADMIN` 角色、当前后台 RBAC 接口需要的基础权限，以及 ADMIN 与这些权限的关联关系。V1 保持不变，不创建物理外键，不依赖固定主键。

## 数据关系

- 通过 `sys_role.role_code = 'ADMIN'` 唯一定位管理员角色。
- 通过 `sys_permission.permission_code` 唯一定位权限。
- 通过 `INSERT ... SELECT` 查询实际 `role_id` 和 `permission_id`，写入 `sys_role_permission`。
- 管理员用户通过唯一用户名定位，另行写入 `sys_user_role`，不把用户生命周期耦合进 V2。

## 基础权限

- `user:create`、`user:read`
- `role:create`、`role:read`
- `permission:create`、`permission:read`
- `user-role:assign`、`user-role:read`
- `role-permission:assign`、`role-permission:read`
- `user-permission:read`

认证与会话接口 `/api/auth/login`、`/api/auth/logout`、`/api/auth/me` 不纳入后台 RBAC 权限。

## 兼容和安全策略

- 角色或权限编码已存在时保留现有记录，不覆盖名称和状态。
- 仅补充 ADMIN 缺失的基础权限关联，不删除已有自定义权限。
- V2 不引用 `sys_user`，因此全新环境中管理员尚未创建时迁移仍能成功。
- 当前环境已有用户名 `admin`；V2 成功后执行按用户名查询 ID 的幂等 SQL，将其绑定到 ADMIN。
- 新环境应在管理员创建后执行同一条绑定 SQL；若用户名不存在，语句插入零行且不报错。

## 验证

- Maven 编译成功。
- 应用启动后 Flyway 历史包含成功的 V2。
- ADMIN 关联 11 项基础权限。
- 当前 `admin` 用户关联 ADMIN，并可通过现有联表查询获得 11 项最终权限。
- 不新增测试类。
