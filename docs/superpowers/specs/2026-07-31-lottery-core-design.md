# LuckyHub 抽奖核心流程设计

> 日期：2026-07-31  
> 状态：业务规则已逐段确认，等待最终复核  
> 范围：第一版同步单抽、十连抽、未中奖、Redis额度控制、MySQL库存、权益、Outbox和Redis Stream闭环。

## 1. 目标与边界

现有认证授权、奖品管理、OSS和活动管理已经完成，抽奖、库存和权益业务包尚未实现。本阶段让登录用户参加 `RUNNING` 活动，并得到可靠、可查询、可审计的结果。

第一版必须包含：

- 单抽和十连抽；
- 独立未中奖权重；
- Redis每日额度预占和Redisson锁；
- `requestId`幂等；
- MySQL条件扣库存；
- 订单、结果、奖品快照和用户权益；
- Outbox、Redis Stream和超时对账；
- 消息接口可替换为Kafka；
- 权限与数据范围；
- 完整测试、API文档和执行流程教学文档。

第一版不实现真实Kafka适配器、Redis库存、积分账户、优惠券核销、会员时长、物流和复杂统计。这些只预留扩展接口。

## 2. 已确认的业务规则

### 2.1 单抽与十连抽

- `drawCount`只能是1或10；
- 单抽生成1条记录，十连抽生成10条；
- 十连抽一次预占10次额度，剩余不足10次整笔拒绝；
- 十连抽在一个MySQL业务事务中全部成功或全部回滚；
- 用户只会看到完整10条结果或失败订单。

### 2.2 次数消耗

- `WIN`和`NO_WIN`都消耗次数；
- 参数错误、活动不可参与和额度不足不消耗；
- 系统异常且没有有效结果时归还全部预占额度；
- 相同 `requestId`重试不重复消耗。

### 2.3 未中奖与权重

活动增加 `noWinWeight >= 0`，默认0：

```text
总权重 = 所有活动奖品权重 + noWinWeight
```

- 发布时总权重必须大于0；
- 抽中独立未中奖区间时记录 `NO_WIN`；
- 售罄或禁用奖品的原权重解释为未中奖，不分配给其他奖品；
- 权重选中奖品只是候选中奖；
- MySQL扣库存成功才是最终 `WIN`；
- 并发扣库存失败直接转 `NO_WIN`，不重新抽取；
- 所有奖品售罄时必然未中奖，即使 `noWinWeight = 0`。

### 2.4 配置快照

- 每个订单从MySQL读取一次活动奖品配置；
- 十连抽10次都使用同一份内存快照；
- 管理员修改只影响之后的新订单；
- 第一版不缓存活动权重；
- 用户接口不返回权重、未中奖权重和精确库存。

### 2.5 日期、身份与幂等

- 每日额度按 `Asia/Shanghai`自然日计算；
- 预占时固定 `drawDate`，跨零点确认和补偿仍使用原日期；
- 用户必须登录，`userId`只从JWT获取，请求体不得传入；
- `requestId`由客户端生成，最长64字符，网络重试必须复用；
- 同ID、同用户、同参数返回原结果；
- 同ID但用户、活动或次数不同返回幂等冲突；
- `FAILED`请求不能用相同ID重新执行，新尝试使用新ID。

## 3. 整体架构

```text
LotteryController
    ↓
LotteryApplicationService
    ├─ DrawEligibilityService
    ├─ DrawQuotaService
    ├─ WeightedDrawEngine
    ├─ ActivityPrizeInventoryService
    ├─ DrawOrderService
    └─ BenefitGrantService
```

### 3.1 MySQL

MySQL是最终事实来源，保存活动配置、库存、订单、结果快照、权益、Outbox、消费记录和失败原因。最终是否成功以MySQL订单状态为准。

### 3.2 Redis

Redis保存每日额度、预占状态、用户活动锁、请求短期状态、结果缓存和当前Redis Stream消息。Redis不是最终抽奖记录来源，异常状态由MySQL和对账任务修复。

### 3.3 消息抽象

业务只依赖：

```text
DrawEventPublisher
├─ RedisStreamDrawEventPublisher（当前）
└─ KafkaDrawEventPublisher（未来）
```

业务Service不能直接使用Stream Key或Kafka Topic。通用事件包含：

```text
eventId, eventType, eventVersion, requestId,
activityId, userId, occurredAt, payload
```

配置：

```yaml
luckyhub:
  messaging:
    provider: redis-stream
```

### 3.4 包结构

```text
lottery
├─ controller, dto, vo, entity, enums, mapper
├─ service/impl
├─ algorithm
├─ messaging/event
├─ messaging/port
├─ messaging/redis
└─ scheduler

inventory
├─ mapper
└─ service

benefit
├─ entity, mapper, service
└─ handler
```

## 4. 数据库设计

使用当前最大Flyway版本之后的新迁移。

### 4.1 `marketing_activity`

新增：

```sql
no_win_weight INT UNSIGNED NOT NULL DEFAULT 0
```

增加非负检查，活动DTO、Entity和VO同步增加 `noWinWeight`。

### 4.2 `lottery_draw_order`

新增 `draw_date DATE NOT NULL`。继续使用 `request_id`、`user_id`、`activity_id`、`draw_count`、`status`、`fail_reason`和时间字段。

状态：

```text
PROCESSING → SUCCESS
PROCESSING → FAILED
```

`SUCCESS`和`FAILED`都是终态。

### 4.3 `lottery_draw_record`

新增或调整：

```text
result_type      WIN / NO_WIN
prize_id         允许NULL
prize_name       允许NULL
prize_type       允许NULL
prize_image_url  允许NULL
```

- `WIN`必须保存奖品ID、名称和类型；
- `NO_WIN`奖品字段全部为NULL；
- `(request_id, sequence_no)`保持唯一；
- 快照避免奖品后续改名、换图或禁用影响历史。

### 4.4 `user_benefit`

新增：

```text
draw_record_id BIGINT UNSIGNED NOT NULL
prize_type     VARCHAR(30) NOT NULL
grant_error    VARCHAR(500) NULL
```

`draw_record_id`建立唯一索引。状态：

```text
PENDING
AVAILABLE
CLAIM_PENDING
GRANT_FAILED
```

中奖事务中先创建 `PENDING`。优惠券、积分、会员第一版模拟为 `AVAILABLE`；实物转 `CLAIM_PENDING`；异常转 `GRANT_FAILED`并等待重试。

### 4.5 `message_outbox`

```text
id, event_id, event_type, event_version,
aggregate_type, aggregate_id, payload,
status, retry_count, next_retry_at,
created_at, sent_at
```

- `event_id`唯一；
- `payload`使用JSON；
- 不保存Redis或Kafka专属字段；
- 状态为 `PENDING / SENT / FAILED`；
- 失败增加次数和下次重试时间，不直接删除。

### 4.6 `message_consume_record`

保存 `event_id`、`consumer_name`和 `consumed_at`，唯一索引为 `(event_id, consumer_name)`，保证当前Stream和未来Kafka消费幂等。

### 4.7 USER角色

- 创建启用的USER角色；
- USER获得普通抽奖权限；
- ADMIN获得普通权限和全量读取权限；
- 现有用户补充USER角色；
- 新用户创建时自动关联USER角色。

## 5. 权重与库存算法

例如：

```text
一等奖10，二等奖30，独立未中奖60，总权重100
1—10一等奖候选，11—40二等奖候选，41—100未中奖
```

随机数生成封装为接口。生产使用安全随机源，测试返回固定数值。

不可用奖品的区间保留但解释为 `NO_WIN`。候选中奖执行：

```sql
UPDATE marketing_activity_prize
SET remaining_stock = remaining_stock - 1
WHERE id = ?
  AND remaining_stock > 0;
```

- 影响1行：最终 `WIN`；
- 影响0行：最终 `NO_WIN`；
- 不重新随机，其他奖品概率不增加。

## 6. Redis设计

### 6.1 Key

```text
draw:quota:{activityId}:{userId}:{yyyyMMdd}
draw:reservation:{requestId}
draw:reservation:timeouts
draw:request:{requestId}
draw:result:{requestId}
lock:draw:{activityId}:{userId}
```

Key由集中工厂生成。额度和预占至少保留到抽奖日期次日零点后48小时。

`draw:reservation:timeouts`使用Sorted Set，member为 `requestId`，score为预占超时时刻。预占Lua同时写入该索引，确认或释放Lua同时移除；对账任务按score批量读取超时请求，不能通过 `KEYS`扫描Redis。

### 6.2 额度预占Lua

一个脚本原子完成：

1. 检查请求是否已经预占；
2. 读取当日已用次数；
3. 判断 `used + drawCount <= dailyLimit`；
4. `INCRBY`增加1或10；
5. 创建 `RESERVED`记录；
6. 保存日期、用户、活动和次数；
7. 设置过期时间。

### 6.3 确认和释放Lua

```text
RESERVED → CONFIRMED
RESERVED → RELEASED，同时quota -= drawCount
```

`CONFIRMED`不能释放，`RELEASED`不能重复释放，quota不能成为负数。状态和计数必须在一个脚本中原子修改。

### 6.4 分布式锁

Redisson用户活动锁保护二次幂等检查、资格确认、Lua预占和PROCESSING订单创建，不覆盖整个抽奖事务。锁不是一致性基础，Lua、MySQL唯一索引、Outbox和对账仍然必须存在。

## 7. 完整请求流程

入口：

```http
POST /api/lottery/draws
Authorization: Bearer <JWT>
```

```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "activityId": 10,
  "drawCount": 1
}
```

执行顺序：

1. Controller校验DTO并从JWT取得用户ID；
2. 查询已有MySQL订单，存在则校验归属和参数并返回；
3. 加载 `RUNNING`活动、每日上限和奖品快照；
4. 获取用户活动分布式锁；
5. 锁内再次检查幂等；
6. Lua原子预占1或10次额度；
7. 单独事务创建 `PROCESSING`订单；
8. 释放分布式锁；
9. 开启抽奖业务事务；
10. 生成1个或10个候选结果；
11. 候选中奖执行MySQL条件扣库存；
12. 保存 `WIN`或 `NO_WIN`记录和奖品快照；
13. 为 `WIN`创建 `PENDING`权益；
14. 订单改为 `SUCCESS`；
15. 写入 `DRAW_CONFIRMED`事件；
16. 为中奖权益写入 `PRIZE_FULFILLMENT_REQUESTED`事件；
17. 事务提交后同步返回完整结果；
18. Outbox后台投递，消费者确认Redis预占并处理权益。

失败流程：

1. 业务事务回滚记录、库存、权益和成功事件；
2. 独立事务把订单改为 `FAILED`并保存安全失败原因；
3. 写入 `DRAW_RELEASE_REQUESTED`事件；
4. 消费者归还1或10次额度；
5. 消息延迟时由对账任务兜底。

## 8. 事件与权益

第一版事件：

```text
DRAW_CONFIRMED
DRAW_RELEASE_REQUESTED
PRIZE_FULFILLMENT_REQUESTED
```

不使用Pub/Sub。消费者处理前检查 `(eventId, consumerName)`，成功后再确认消息。权益状态修改和消费记录写入同一个MySQL事务；Redis额度确认与释放本身由Lua保证幂等。只有业务处理成功后才能对Stream执行消息确认。

权益处理接口：

```text
BenefitFulfillmentHandler
├─ CouponFulfillmentHandler
├─ PointsFulfillmentHandler
├─ MembershipFulfillmentHandler
└─ PhysicalFulfillmentHandler
```

权益失败只改变权益状态，不改变已成功订单和中奖记录。

## 9. 接口、权限与数据范围

统一接口，不单独创建管理员Controller：

```text
GET  /api/lottery/activities/{activityId}
POST /api/lottery/draws
GET  /api/lottery/draws/{requestId}
GET  /api/lottery/orders
GET  /api/lottery/records
GET  /api/benefits
GET  /api/benefits/{id}
```

普通权限：

```text
lottery:activity:read
lottery:draw
lottery:draw:read
lottery:record:read
benefit:read
```

全量权限：

```text
lottery:order:read:all
lottery:draw:read:all
lottery:record:read:all
benefit:read:all
```

Controller用现有 `@RequirePermission`检查基础权限，Service再判断 `read:all`：

- 有全量权限可按用户、活动、状态和日期查询；
- 无全量权限强制使用JWT用户范围；
- 普通用户显式传入其他 `userId`时返回禁止访问，不静默忽略；
- 查询 `requestId`和权益详情必须检查归属。

## 10. 后台任务与故障恢复

### 10.1 Outbox投递

周期读取到期的 `PENDING/FAILED`事件，通过 `DrawEventPublisher`发送。成功标记 `SENT`，失败更新重试次数和时间。多实例通过条件抢占或数据库锁避免重复投递，消费者仍必须幂等。

### 10.2 预占和订单对账

默认每30秒执行，超时阈值默认2分钟，均可配置：

```text
RESERVED + SUCCESS    → CONFIRMED
RESERVED + FAILED     → RELEASED
RESERVED + 无订单      → RELEASED
RESERVED + PROCESSING → 超时订单处理
```

超时 `PROCESSING`且没有成功业务结果时，订单转 `FAILED`，原因 `PROCESSING_TIMEOUT`，并写入额度释放事件。

所有订单状态更新都必须带当前状态条件，例如 `WHERE status = 'PROCESSING'`。正常抽奖提交和超时对账如果发生竞争，只能有一个状态更新成功；更新失败的一方必须停止后续处理，不能把 `FAILED`重新覆盖为 `SUCCESS`，也不能把 `SUCCESS`覆盖为 `FAILED`。

### 10.3 故障策略

- Redis不可用：拒绝新抽奖，不能绕过额度；
- 锁获取失败：返回请求处理中，客户端使用原ID查询或重试；
- MySQL不可用：抽奖失败，已预占额度释放并由对账兜底；
- Redis Stream不可用：已提交结果正常返回，Outbox保留待发送；
- 权益发放失败：转 `GRANT_FAILED`并重试，不回滚抽奖。

## 11. 错误码

至少包含：

```text
活动不存在、活动不可参与、参数不合法、每日额度不足、
十连抽额度不足、幂等参数冲突、订单处理中、订单失败、
数据访问越权、Redis额度服务不可用、锁获取失败、
抽奖配置无有效权重、抽奖事务失败、权益不存在
```

对外不返回SQL、堆栈和Redis内部Key，详细异常写日志，订单只保存清理后的失败原因。

## 12. 测试要求

### 12.1 算法

- 独立未中奖和 `noWinWeight = 0`；
- 售罄、禁用和扣减失败转未中奖；
- 所有奖品售罄；
- 固定随机源保证测试确定。

### 12.2 Redis与并发

- 单抽和十连抽原子预占；
- 额度不足整笔拒绝；
- 相同请求不重复预占；
- 确认、释放和跨零点幂等；
- 多线程不突破每日上限。

### 12.3 MySQL与事务

- 库存10、并发100次时只能成功10次且库存不为负；
- 单抽1条、十连抽10条；
- 中奖创建权益，未中奖不创建；
- 十连抽中途系统异常全部回滚；
- 失败订单保留且Redis额度释放。

### 12.4 幂等、消息和权限

- 同请求不重复扣库存、额度或权益；
- 参数变化冲突，其他用户不能读取；
- Outbox和业务事务一致；
- 重复消息不重复消费；
- 中间件故障保留待投递事件；
- 超时状态得到对账修复；
- USER只能看自己，ADMIN全量读取；
- 新用户自动获得USER角色；
- 用户接口不泄露权重和精确库存。

## 13. 验证与文档

验证命令：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 test
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 package -DskipTests
git diff --check
```

不得提交 `.env`、AccessKey或真实密钥。

实现后必须生成：

```text
docs/lottery-api.md
docs/LuckyHub-抽奖核心流程实现详解.md
```

教学文档从 `POST /api/lottery/draws`开始，严格按真实执行顺序解释每个文件、类、注解、字段、方法、参数、返回值、DTO/VO/Entity/Mapper/Service区别、权重算法、Lua脚本、库存SQL、事务拆分、锁的边界、Outbox、Redis Stream、Kafka扩展、单抽、十连抽、未中奖、幂等、补偿、Postman、MySQL、Redis、断点和排错，并提供可复用模板。

## 14. 验收标准

1. USER可在运行活动单抽和十连抽；
2. 未中奖和售罄权重符合设计；
3. 并发库存不超卖、额度不超限；
4. 重复请求不重复执行；
5. 十连抽系统失败全部回滚；
6. 订单、记录、快照和权益可查询；
7. Redis与MySQL异常状态可补偿和对账；
8. 消息接口不绑定Redis Stream；
9. 基础权限和 `read:all`数据范围正确；
10. 完整测试、打包、API文档和详细教学文档全部完成。
