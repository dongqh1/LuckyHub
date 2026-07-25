# LuckyHub RBAC 持久层设计

## 目标

使用 MyBatis-Plus 映射用户、角色和权限三张基础表，提供类型安全的基础 CRUD，并通过真实 MySQL 事务测试验证映射、自增主键、时间填充和唯一约束。

## 实体

- `SysUser` 映射 `sys_user`。
- `SysRole` 映射 `sys_role`。
- `SysPermission` 映射 `sys_permission`。
- 主键均使用 `IdType.AUTO`，对应 MySQL `AUTO_INCREMENT`。
- 数据库下划线字段映射为 Java 驼峰属性。
- 用户密码不参与 `toString()`，避免日志泄露。
- 状态暂用 `Integer` 保存 0/1，不提前引入业务枚举转换。

## Mapper

三个 Mapper 均继承 `BaseMapper<T>`。配置类通过 `@MapperScan` 扫描 `com.dongqh.luckyhub.rbac.mapper`，不在接口上重复添加 `@Mapper`。

## 时间填充

`MetaObjectHandler` 在插入时填写 `createdAt` 和 `updatedAt`，在更新时填写 `updatedAt`。数据库默认值继续作为非应用写入场景的兜底。

## 验证

- 用户插入、按 ID 查询、更新和删除。
- 角色与权限插入和条件查询。
- 自增 ID 与时间字段不为空。
- 用户名、角色编码和权限编码的数据库唯一约束生效。
- 测试使用真实 Docker MySQL，并由 Spring 测试事务回滚。

## 本次不包含

- 不映射用户角色、角色权限关联表。
- 不创建 Service、DTO、VO、Controller。
- 不实现密码加密、登录或接口权限保护。
