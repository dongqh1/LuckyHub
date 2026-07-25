# LuckyHub 初始数据库结构设计

## 目标

使用 Flyway 管理 LuckyHub 第一阶段的 MySQL 结构，首次启动自动创建 11 张业务表，并为后续版本保留可追踪、不可重复执行的迁移历史。

## 技术与迁移规则

- Spring Boot 4.1.0 的 `spring-boot-starter-flyway`。
- MySQL 支持模块 `org.flywaydb:flyway-mysql`。
- 首个迁移为 `src/main/resources/db/migration/V1__create_luckyhub_schema.sql`。
- Flyway 使用现有 Spring `DataSource`，启动时自动迁移并启用校验。
- 已执行的迁移文件不得修改；结构变化通过 V2、V3 等新文件完成。

## 表结构范围

V1 创建以下 11 张业务表：

1. `sys_user`
2. `sys_role`
3. `sys_permission`
4. `sys_user_role`
5. `sys_role_permission`
6. `marketing_prize`
7. `marketing_activity`
8. `marketing_activity_prize`
9. `lottery_draw_order`
10. `lottery_draw_record`
11. `user_benefit`

Flyway 自己创建 `flyway_schema_history`，该表不计入 11 张业务表。

## 数据库规范

- 存储引擎为 InnoDB，字符集为 `utf8mb4`，排序规则为 `utf8mb4_0900_ai_ci`。
- 实体表主键为 `BIGINT UNSIGNED AUTO_INCREMENT`。
- 关联 ID 为 `BIGINT UNSIGNED`，不创建物理外键。
- 时间字段使用 `DATETIME(3)`；创建时间和修改时间由数据库提供默认值，修改时间自动更新。
- 状态数值和布尔值使用 `TINYINT`，布尔值通过 CHECK 约束限制为 0 或 1。
- 权重、库存、数量、次数和结果序号使用无符号整数，并通过 CHECK 约束保证业务边界。
- 所有表和字段带中文注释。

## 唯一约束

- `sys_user(username)`
- `sys_role(role_code)`
- `sys_permission(permission_code)`
- `sys_user_role(user_id, role_id)`
- `sys_role_permission(role_id, permission_id)`
- `marketing_activity_prize(activity_id, prize_id)`
- `lottery_draw_order(request_id)`
- `lottery_draw_record(request_id, sequence_no)`

`user_benefit(user_id, prize_id)` 不建立唯一索引，因为奖品是否可叠加是动态业务属性，不能由固定索引准确表达。

## 查询索引

- 所有关联字段建立普通索引。
- 活动表为 `status`、`start_time`、`end_time` 和 `activity_name` 建立索引。
- 抽奖订单为用户、活动、状态和创建时间的常用查询建立索引。
- 中奖记录为订单、用户、活动、奖品和抽奖时间建立索引。
- 用户权益为用户、奖品、状态和获得时间建立索引。

## 验证

- 自动化测试断言 11 张业务表全部存在。
- 自动化测试断言 Flyway V1 状态为成功。
- 自动化测试断言 8 组核心唯一索引存在。
- 重复运行完整测试时不会重复执行 V1。
- 原有 MySQL 与 Redis 连接测试继续通过。

## 本次不包含

- 不插入管理员、角色、权限或业务测试数据。
- 不创建实体类、Mapper、Service 或 Controller。
- 不创建物理外键、触发器、存储过程或分区表。
