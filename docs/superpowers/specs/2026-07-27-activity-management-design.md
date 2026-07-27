# LuckyHub 活动管理模块设计

## 1. 目标

在现有奖品管理模块之上实现后台活动管理，包含：

- 创建、查询和分页查询活动；
- 修改活动基础信息；
- 发布、禁用和恢复活动；
- 为活动添加、查询、修改和移除奖品；
- 管理活动奖品的权重、总库存、剩余库存和展示顺序；
- 通过 RBAC 权限保护所有后台接口；
- 使用 MySQL 事务保存管理数据；
- 通过 Spring 定时任务把活动运行状态持久化到 MySQL；
- 编写按 HTTP 请求执行顺序展开的教学文档。

本阶段不实现：

- Redis 活动缓存；
- Redis 库存扣减；
- 用户抽奖；
- 每日抽奖次数统计；
- 抽奖订单和中奖记录；
- 用户权益发放；
- 活动物理删除。

## 2. 核心设计决策

### 2.1 数据库持久化完整活动状态

`marketing_activity.status` 保存活动当前的完整状态：

- `DRAFT`：草稿；
- `SCHEDULED`：已发布，等待开始；
- `RUNNING`：正在进行；
- `ENDED`：已经到达结束时间；
- `DISABLED`：管理员手动禁用。

“发布”是一个管理操作，不单独保存为 `PUBLISHED`。发布时：

```text
当前时间 < startTime
    → status = SCHEDULED

startTime <= 当前时间 < endTime
    → status = RUNNING
```

Spring 定时任务默认每 30 秒执行一次，使用 MySQL `NOW(3)` 批量更新：

```sql
UPDATE marketing_activity
SET status = 'RUNNING'
WHERE status = 'SCHEDULED'
  AND start_time <= NOW(3)
  AND end_time > NOW(3);

UPDATE marketing_activity
SET status = 'ENDED'
WHERE status IN ('SCHEDULED', 'RUNNING')
  AND end_time <= NOW(3);
```

第二条 SQL 同时匹配 `SCHEDULED`，用于修复应用停机期间错过开始和结束时间的活动。应用重启后第一次调度即可把它直接修正为 `ENDED`。

查询接口只读取数据库，不在 `GET` 请求中执行状态更新。

### 2.2 状态流转

```text
创建
  ↓
DRAFT ──发布──┬──→ SCHEDULED ──到达开始时间──→ RUNNING
  │           │                                   │
  │           └──→ RUNNING                        │
  │                                               │
  │                         到达结束时间───────────┘
  │                                   ↓
  │                                 ENDED
  │
  └───────────────管理员禁用────────────────────→ DISABLED
                                                   │
                                                   └─恢复编辑→ DRAFT
```

规则：

- 创建活动后固定为 `DRAFT`；
- `DRAFT` 可以发布；
- `DRAFT`、`SCHEDULED`、`RUNNING` 和 `ENDED` 都可以禁用；
- `DISABLED` 只能查询或恢复；
- 恢复把 `DISABLED` 转换为 `DRAFT`；
- 恢复后可以修改时间和奖品，再重新发布；
- `SCHEDULED` 和 `RUNNING` 允许具有权限的管理员修改；
- `ENDED` 不能直接修改，必须先禁用、恢复为草稿，再修改和发布；
- 重复禁用 `DISABLED` 活动保持成功；
- 重复恢复非 `DISABLED` 活动返回非法状态错误；
- 只有 `DRAFT` 可以发布。

### 2.3 不使用 Redis

活动后台管理是低频、强一致性的管理操作，本阶段只使用 MySQL。

后续抽奖模块可以在活动发布、修改、定时变更、禁用和恢复后维护 Redis 缓存，但本设计不提前引入缓存双写和一致性问题。

### 2.4 定时任务的一致性边界

- 默认间隔为 30 秒，可通过 `luckyhub.activity.status-refresh-interval` 配置；
- 状态变化最多延迟一个调度周期；
- SQL 使用数据库 `NOW(3)`，避免多个应用实例系统时间不一致；
- 条件更新具有幂等性，多实例重复执行不会重复改变已经更新的记录；
- 后续抽奖接口不能只检查 `status = RUNNING`，还必须同时检查 `start_time <= NOW(3)` 和 `end_time > NOW(3)`，防止调度延迟造成越界抽奖。

## 3. 数据模型

复用 V1 已创建的两张表。

### 3.1 `marketing_activity`

| 字段 | 含义 |
|---|---|
| `id` | 活动 ID |
| `activity_name` | 活动名称 |
| `description` | 活动说明 |
| `status` | `DRAFT`、`SCHEDULED`、`RUNNING`、`ENDED` 或 `DISABLED` |
| `start_time` | 开始时间 |
| `end_time` | 结束时间 |
| `daily_limit` | 用户每日参与次数上限 |
| `created_by` | 创建活动的登录用户 ID |
| `created_at` | 创建时间 |
| `updated_at` | 修改时间 |

`created_by` 从 `LoginContext.require().userId()` 获取，不允许客户端传入。

### 3.2 `marketing_activity_prize`

| 字段 | 含义 |
|---|---|
| `id` | 关联记录 ID |
| `activity_id` | 活动 ID |
| `prize_id` | 奖品 ID |
| `weight` | 中奖权重 |
| `total_stock` | 总库存 |
| `remaining_stock` | 数据库剩余库存 |
| `sort_order` | 展示顺序 |
| `created_at` | 创建时间 |
| `updated_at` | 修改时间 |

同一活动不能重复关联同一奖品，由 `uk_activity_prize(activity_id, prize_id)` 和业务校验共同保证。

## 4. 枚举

### 4.1 `ActivityStatus`

```java
public enum ActivityStatus {
    DRAFT,
    SCHEDULED,
    RUNNING,
    ENDED,
    DISABLED
}
```

## 5. 活动接口

基础路径：

```text
/api/admin/activities
```

### 5.1 创建活动

```http
POST /api/admin/activities
```

权限：

```text
activity:create
```

请求：

```json
{
  "activityName": "八月幸运抽奖",
  "description": "八月会员抽奖活动",
  "startTime": "2026-08-01T10:00:00",
  "endTime": "2026-08-10T22:00:00",
  "dailyLimit": 3
}
```

规则：

- `activityName` 必填，去除首尾空格后长度不超过 100；
- `description` 可空，去除首尾空格后长度不超过 1000；
- `startTime` 和 `endTime` 必填；
- `endTime` 必须晚于 `startTime`；
- `dailyLimit` 必须大于零；
- 创建状态固定为 `DRAFT`；
- `createdBy` 来自当前登录用户。

成功返回 HTTP 201。

### 5.2 查询活动详情

```http
GET /api/admin/activities/{id}
```

权限：

```text
activity:read
```

返回活动基础信息和数据库中持久化的 `status`。

活动不存在返回 HTTP 404。

### 5.3 分页查询活动

```http
GET /api/admin/activities
```

权限：

```text
activity:read
```

查询参数：

- `name`：按活动名称模糊查询；
- `status`：按活动状态精确筛选；
- `page`：从 1 开始；
- `size`：1 至 100。

排序：

```text
created_at DESC, id DESC
```

### 5.4 修改活动

```http
PUT /api/admin/activities/{id}
```

权限：

```text
activity:update
```

请求字段与创建一致。

允许：

- `DRAFT`；
- `SCHEDULED`；
- `RUNNING`。

拒绝：

- `DISABLED`；
- `ENDED`。

修改 `DRAFT` 不改变状态。修改 `SCHEDULED` 或 `RUNNING` 时，新的 `endTime` 必须晚于数据库当前时间，然后根据新的 `startTime` 立即重新计算为 `SCHEDULED` 或 `RUNNING`。

### 5.5 发布活动

```http
PATCH /api/admin/activities/{id}/publish
```

权限：

```text
activity:publish
```

只有 `DRAFT` 可以发布。

发布前校验：

- `endTime` 晚于当前时间；
- 至少配置一个活动奖品；
- 所有关联奖品当前为启用状态；
- 所有权重大于零；
- 所有库存满足 `0 <= remainingStock <= totalStock`；
- 至少一个奖品的 `remainingStock > 0`。

发布成功后，根据数据库当前时间设置：

```text
now < startTime
    → status = SCHEDULED

startTime <= now < endTime
    → status = RUNNING
```

### 5.6 禁用活动

```http
PATCH /api/admin/activities/{id}/disable
```

权限：

```text
activity:disable
```

允许从 `DRAFT`、`SCHEDULED`、`RUNNING` 或 `ENDED` 禁用。重复禁用保持成功。

禁用后：

- 允许查询；
- 允许恢复；
- 禁止普通修改；
- 禁止添加、修改或移除活动奖品；
- 禁止直接发布。

### 5.7 恢复活动

```http
PATCH /api/admin/activities/{id}/restore
```

权限：

```text
activity:restore
```

只有 `DISABLED` 可以恢复。

恢复后：

```text
status = DRAFT
```

恢复不会自动修改原开始时间、结束时间或奖品配置。管理员需要检查和修改后重新发布。

## 6. 活动奖品接口

基础路径：

```text
/api/admin/activities/{activityId}/prizes
```

管理权限统一为：

```text
activity:prize:manage
```

查询活动奖品列表使用：

```text
activity:read
```

### 6.1 添加活动奖品

```http
POST /api/admin/activities/{activityId}/prizes
```

请求：

```json
{
  "prizeId": 1,
  "weight": 20,
  "totalStock": 100,
  "sortOrder": 1
}
```

规则：

- 活动必须存在且允许配置；
- `DISABLED` 和 `ENDED` 不允许配置；
- 奖品必须存在；
- 奖品必须处于启用状态；
- 同一活动不能重复添加同一奖品；
- `weight > 0`；
- `totalStock > 0`；
- `sortOrder >= 0`；
- 新增时 `remainingStock = totalStock`。

### 6.2 查询活动奖品

```http
GET /api/admin/activities/{activityId}/prizes
```

返回：

- 活动奖品关联 ID；
- 奖品 ID；
- 奖品名称、类型、等级、图片 URL 和当前状态；
- 权重；
- 总库存；
- 剩余库存；
- 展示顺序。

排序：

```text
sort_order ASC, id ASC
```

即使奖品后来被禁用，查询仍返回该关联，并显示奖品当前状态。

### 6.3 修改活动奖品

```http
PUT /api/admin/activities/{activityId}/prizes/{prizeId}
```

请求：

```json
{
  "weight": 30,
  "totalStock": 150,
  "sortOrder": 1
}
```

规则：

- 活动必须允许配置；
- 关联记录必须存在；
- 当前奖品必须为启用状态；
- `weight > 0`；
- `totalStock > 0`；
- `sortOrder >= 0`。

库存计算：

```text
consumedStock = oldTotalStock - oldRemainingStock
newRemainingStock = newTotalStock - consumedStock
```

如果：

```text
newTotalStock < consumedStock
```

则拒绝修改，防止剩余库存变成负数。

### 6.4 移除活动奖品

```http
DELETE /api/admin/activities/{activityId}/prizes/{prizeId}
```

这里只删除活动与奖品之间的关联，不删除 `marketing_prize`。

`DISABLED` 和 `ENDED` 活动不允许移除关联奖品。

## 7. 分层结构

```text
activity/
├─ controller/
│  ├─ ActivityController
│  └─ ActivityPrizeController
├─ dto/
│  ├─ CreateActivityCommand
│  ├─ UpdateActivityCommand
│  ├─ ActivityQuery
│  ├─ AddActivityPrizeCommand
│  └─ UpdateActivityPrizeCommand
├─ entity/
│  ├─ MarketingActivity
│  └─ MarketingActivityPrize
├─ enums/
│  ├─ ActivityStatus
│  └─ ActivityErrorCode
├─ mapper/
│  ├─ MarketingActivityMapper
│  └─ MarketingActivityPrizeMapper
├─ service/
│  ├─ ActivityService
│  ├─ ActivityPrizeService
│  ├─ ActivityStatusService
│  └─ impl/
│     ├─ ActivityServiceImpl
│     ├─ ActivityPrizeServiceImpl
│     └─ ActivityStatusServiceImpl
├─ scheduler/
│  └─ ActivityStatusScheduler
└─ vo/
   ├─ ActivityView
   └─ ActivityPrizeView
```

职责：

- Controller：HTTP 路由、请求绑定、权限和响应状态；
- DTO：客户端输入和 Jakarta Validation；
- Service：事务、状态流转和业务规则；
- StatusService：使用两条条件更新 SQL 批量推进时间状态；
- StatusScheduler：每 30 秒触发一次状态修正；
- Mapper：MyBatis-Plus 数据访问；
- Entity：数据库行映射；
- VO：稳定的接口输出。

## 8. 事务

使用 `@Transactional` 的操作：

- 创建活动；
- 修改活动；
- 发布；
- 禁用；
- 恢复；
- 添加活动奖品；
- 修改活动奖品；
- 移除活动奖品。

查询接口不启动写事务。

发布操作必须在同一个事务中读取活动和关联奖品、完成所有校验并更新状态。

定时任务中的 `SCHEDULED → RUNNING` 和 `SCHEDULED/RUNNING → ENDED` 分别使用单条批量 `UPDATE`。每条 SQL 自己构成原子操作，不逐行加载 Entity。

## 9. 权限

新增权限：

| 权限码 | 含义 |
|---|---|
| `activity:create` | 创建活动 |
| `activity:read` | 查询活动与活动奖品 |
| `activity:update` | 修改活动 |
| `activity:publish` | 发布活动 |
| `activity:disable` | 禁用活动 |
| `activity:restore` | 恢复活动为草稿 |
| `activity:prize:manage` | 添加、修改和移除活动奖品 |

通过 Flyway V4 创建权限并授予 `ADMIN`。

## 10. 错误处理

活动模块使用独立业务错误码，至少包括：

| 场景 | HTTP |
|---|---:|
| 活动不存在 | 404 |
| 活动奖品关联不存在 | 404 |
| 活动时间范围非法 | 400 |
| 当前状态不允许操作 | 409 |
| 活动没有配置奖品 | 409 |
| 活动包含已禁用奖品 | 409 |
| 活动奖品重复 | 409 |
| 新总库存小于已消耗库存 | 409 |
| 活动没有可用库存 | 409 |

DTO 格式错误继续由全局参数校验处理。

## 11. 测试

### 11.1 `ActivityStatusServiceTests`

验证：

- 到达开始时间的 `SCHEDULED` 批量变为 `RUNNING`；
- 到达结束时间的 `SCHEDULED` 和 `RUNNING` 批量变为 `ENDED`；
- `DRAFT`、`ENDED` 和 `DISABLED` 不被错误修改；
- 重复执行状态刷新结果不变；
- `ActivityStatusScheduler` 会调用状态服务。

### 11.2 `ActivityServiceTests`

覆盖：

- 创建固定为草稿并记录当前用户；
- 时间非法拒绝；
- 查询和分页；
- 草稿、待开始和进行中允许修改；
- 已结束和禁用活动拒绝修改；
- 发布前全部校验，并根据当前时间持久化为 `SCHEDULED` 或 `RUNNING`；
- 修改已发布活动时间后重新计算并持久化状态；
- 禁用幂等；
- 只有禁用活动可以恢复；
- 恢复后回到草稿。

### 11.3 `ActivityPrizeServiceTests`

覆盖：

- 添加启用奖品；
- 拒绝禁用奖品；
- 拒绝重复奖品；
- 初始化剩余库存；
- 查询包含奖品展示信息；
- 修改权重和展示顺序；
- 按已消耗数量重算剩余库存；
- 拒绝把总库存改到已消耗数量以下；
- 移除关联但不删除奖品；
- 禁用和结束活动拒绝配置。

### 11.4 Controller 测试

覆盖：

- 路由；
- 请求绑定；
- HTTP 状态；
- JSON 响应；
- 每个方法的权限注解。

### 11.5 数据库迁移测试

验证：

- 七项权限存在；
- ADMIN 拥有七项活动管理权限。

### 11.6 全量验证

运行：

```powershell
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests
```

## 12. 教学文档

创建：

```text
docs/LuckyHub-活动管理实现详解.md
```

文档面向希望下次能够独立实现该模块的学习者，不按组件罗列，而按请求执行顺序展开。

主线从：

```http
POST /api/admin/activities
```

开始，依次解释：

```text
HTTP JSON
→ AuthenticationFilter
→ PermissionInterceptor
→ ActivityController
→ CreateActivityCommand
→ Jakarta Validation
→ ActivityService
→ LoginContext
→ MarketingActivity
→ MarketingActivityMapper
→ MyBatis-Plus
→ MySQL
→ ActivityView
→ ApiResponse
→ JSON
```

随后继续按实际请求讲解：

- 添加活动奖品；
- 修改活动奖品库存；
- 发布活动；
- Spring 定时任务如何使用批量 SQL 把 `SCHEDULED` 更新为 `RUNNING`、把到期活动更新为 `ENDED`；
- 为什么查询接口不执行 `UPDATE`；
- 应用停机后重新启动时怎样修复过期状态；
- 禁用活动；
- 恢复为草稿；
- 修改配置并重新发布。

每一步都回答：

```text
现在缺少什么？
为什么需要这个类？
它接收什么输入？
关键代码每一行做什么？
它产生什么输出？
输出交给下一步的谁？
```

文档包含可直接执行的 Postman 示例、正常流程、失败流程和从零复现顺序。
