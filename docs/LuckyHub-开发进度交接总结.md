# LuckyHub 开发进度交接总结

> 更新时间：2026-08-08
>
> 用途：压缩聊天上下文，供下次开发直接读取。
>
> 项目目录：`E:\ANotes\software\luckyhub`

🎉 LuckyHub 抽奖核心 + 迷你商城阶段 1 基础（截至 2026-08-08）

Progress：抽奖核心保持可用；迷你商城阶段 1 已完成商品/SKU、统一奖励定义、渠道库存、11 个 API、V8/V9、权限和文档。阶段 1 聚焦测试 39/39、空库迁移测试 14/14、全量回归 274/274 通过。

Plans：阶段 1 必做任务为零。下一步只编写阶段 2“积分账户与积分商城”实施计划，依据已交付的 SKU、积分价格和 `POINTS` 渠道库存接口拆分任务；尚未开始阶段 2 代码。

Problems：无 Critical/Important 阻断；工作区保留未跟踪的 `.superpowers`/`.codex-progress` 过程文件，未提交、不影响运行。

---

## 1. 明天如何恢复上下文

新会话开始时直接说：

```text
请先读取 docs/LuckyHub-开发进度交接总结.md，
再执行 git status --short 和 git log -5 --oneline，
然后读取 docs/LuckyHub-迷你商城下一阶段执行总路线.md，
为阶段 2“积分账户与积分商城”编写独立实施计划，不直接写阶段 2 代码。
```

最小检查命令：

```powershell
git status --short
git log -5 --oneline
git rev-list --left-right --count origin/master...master
docker compose ps
```

项目约定：

- 直接在当前 `master` 开发，用户已确认不建 worktree。
- Windows + PowerShell 7；中文 UTF-8。
- 编辑文件优先使用 `apply_patch`。
- 不要直接运行系统 `mvn`；统一使用：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 test
```

---

## 2. 当前最终状态

- 分支：`master`
- 抽奖核心基线：`03c242b docs: finalize lottery specification`
- 阶段 1 功能基线：`90257bb feat: expose channel inventory API`
- 当前远程同步状态：本阶段未执行推送，使用恢复检查命令确认
- tracked 工作区：阶段 1 交接提交后应为干净
- Docker MySQL/Redis：最终验收时已启动并健康
- Java：17
- Spring Boot：4.1.0
- MySQL：8.4
- Redis：配额、预占、Redisson 锁、Stream 消息
- Flyway：V1–V9 全部通过校验

阶段 1 聚焦验收证据：

```text
Tests run: 39
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

空数据库迁移验收证据：

```text
临时数据库：luckyhub_phase1_verify
Flyway：从 Empty Schema 依次成功应用 V1-V9
Tests run: 14
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
临时数据库与临时授权已清理
```

全量回归证据：

```text
Tests run: 274
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

打包证据：

```text
target/luckyhub-0.0.1-SNAPSHOT.jar
69,386,112 bytes
BUILD SUCCESS
```

敏感信息审计：

- tracked 高风险密钥形状：0。
- tracked `.env` / AccessKey CSV：0。
- `.env.example` 和文档只有占位符。
- 真实 `.env` 与 AccessKey 文件不得提交。

---

## 3. 已完成的业务模块

### 3.1 基础模块

已有并保持可用：

- JWT 登录与 RBAC；
- 用户注册时默认 `USER` 角色；
- 奖品创建、修改、查询和禁用；
- 阿里云 OSS 图片上传与公开 URL 入库；
- 活动创建、发布、禁用、恢复、活动奖品管理；
- 活动状态定时持久化更新。

### 3.2 抽奖核心

已完成：

- 支持单抽和十连抽；
- 支持独立 `noWinWeight`，默认 0；
- 支持明确未中奖；
- 禁用或售罄奖品保留原权重区间，命中后转 `NO_WIN`，不把概率分给其他奖品；
- 候选奖品只有 MySQL 条件扣库存成功才是 `WIN`；
- 扣库存失败转 `NO_WIN`，不重抽；
- 十连抽必须有完整 10 次配额，业务事务全或无；
- 历史记录保存奖品名称、类型、图片等快照。

### 3.3 幂等、配额与锁

- `requestId` 由客户端生成，必须是规范 UUID；
- 幂等身份是 `requestId + userId + activityId + drawCount`；
- `dailyLimit` 只是新预占时的策略快照，不属于幂等身份；
- 同请求跨零点或策略变化仍返回原 MySQL 订单；
- MySQL 订单是幂等与结果的最终事实来源；
- Redis Lua 原子完成预占、确认和释放；
- Redisson 用户/活动锁保护二次幂等、预占和 `PROCESSING` 建单；
- MySQL 唯一索引、Lua、条件更新与对账才是一致性基础，锁不是唯一基础。

### 3.4 订单与事务

- `PROCESSING` 订单用 `REQUIRES_NEW` 先独立提交；
- 抽奖记录、库存、权益、`SUCCESS` 状态和 Outbox 在一个 `REQUIRED` 事务内；
- 十连抽第 7 项异常等场景会整个回滚；
- 失败订单保留，同一 requestId 不允许重新执行；
- 新尝试必须换新 UUID。

### 3.5 Outbox 和 Redis Stream

- 业务只依赖 `DrawEventPublisher`，没有把 Stream 字段泄漏到核心事件；
- 业务与 Outbox 同 MySQL 事务；
- relay 使用 `PROCESSING + claim_token + lease`短租约；
- Redis 发布在 MySQL 事务外执行；
- 发布成功后才标记 `SENT`；
- 实现 at-least-once，允许重复、不允许丢失；
- Stream 消费只在业务成功后 ACK；
- `eventId + logicalConsumerName` 的 MySQL 消费记录提供幂等；
- 从 `0-0` 创建消费组，不跳过已有消息；
- stale pending 和新消息每轮都有有界处理，毒消息不再阻塞新消息。

### 3.6 权益履约

- `COUPON` / `POINTS` / `MEMBERSHIP` 成功后为 `AVAILABLE`；
- `PHYSICAL` 成功后为 `CLAIM_PENDING`；
- 失败用独立事务记录 `GRANT_FAILED` 和安全错误；
- 失败消息不 ACK，允许同事件重试；
- 当前四个 handler 是第一版状态履约，尚未调用真实第三方券、积分、会员或物流 API。

### 3.7 超时对账

- 只按 `draw:reservation:timeouts` ZSet score 读取到期成员；
- 有界 batch，不使用 Redis `KEYS` / `SCAN`；
- `RESERVED + SUCCESS -> CONFIRMED`；
- `RESERVED + FAILED/无订单 -> RELEASED`；
- fresh `PROCESSING` 重排真实 deadline；
- expired `PROCESSING` 条件改 `FAILED` 并写释放事件；
- 对账与创建订单使用同一把 draw lock，避免“先释放、后成功”的免费抽奖竞争；
- 完整交叉校验 Redis reservation 七字段与 MySQL 订单身份。

### 3.8 迷你商城阶段 1 基础

已完成：

- `catalog` 包：商品、默认 SKU、现金价、积分价、购买方式、用户查询和管理员创建；
- `reward` 包：`PRODUCT/COUPON/POINTS/MEMBERSHIP/DRAW_CHANCE` 统一奖励定义；
- `inventory.channel` 包：SKU 总库存、渠道分配、预占、确认、释放、查询和不可变幂等流水；
- 商品响应不包含任何总库存、可用库存、预占库存或已消费库存字段；
- 渠道库存使用 MySQL 条件更新，100 个请求并发争抢 10 件时只有 10 个成功；
- 相同业务号或预占号重复调用只产生一次效果，不同参数重用同一编号会返回冲突；
- 新库存和旧 `ActivityPrizeInventoryService` 独立，现有抽奖库存、Outbox 和事件行为未改变。

新增权限：

```text
catalog:read
catalog:manage
reward:manage
inventory:manage
```

角色默认分配：USER 和 ADMIN 拥有 `catalog:read`；只有 ADMIN 拥有三个管理权限。

新增 11 个 API：

```text
POST /api/admin/products
GET  /api/products
GET  /api/products/{id}

POST /api/admin/reward-definitions
GET  /api/admin/reward-definitions/{id}

POST /api/admin/inventory/skus/initialize
POST /api/admin/inventory/channels/allocate
POST /api/admin/inventory/reservations
POST /api/admin/inventory/reservations/{reservationNo}/confirm
POST /api/admin/inventory/reservations/{reservationNo}/release
GET  /api/admin/inventory/skus/{skuId}/channels/{channelCode}
```

完整请求、响应、错误码和 PowerShell 示例：

```text
docs/catalog-reward-inventory-api.md
```

阶段 1 边界：

- 只使用 `MALL` 和 `POINTS` 渠道；`LOTTERY:{activityId}` 保留到阶段 5；
- 新 `reward_definition` 尚未接入当前抽奖；
- 没有实现积分账户、兑换订单、优惠券、会员、支付、地址或物流；
- 当前券、积分、会员和实物权益 handler 仍是抽奖核心时期的状态占位实现，不是真实外部系统。

---

## 4. 数据库迁移

已有 V1–V4 保留原基础、奖品和活动模块。

抽奖新迁移：

- `V5__add_lottery_core.sql`
  - 抽奖订单、记录、权益、Outbox、消费记录；
  - 活动 `no_win_weight`；
  - `USER` 角色和 9 项抽奖/权益权限；
  - 历史 `user_benefit` 安全迁移守卫。
- `V6__add_outbox_delivery_error.sql`
  - Outbox `last_error`。
- `V7__lease_outbox_delivery.sql`
  - Outbox `PROCESSING` 状态和 `claim_token`租约。

迷你商城阶段 1 新迁移：

- `V8__add_catalog_and_reward_foundation.sql`
  - `product`、`product_sku`、`reward_definition`；
  - `marketing_prize.reward_definition_id` 可空兼容字段；
  - 4 个新权限及角色分配。
- `V9__add_channel_inventory.sql`
  - `sku_inventory`、`inventory_channel_stock`；
  - `inventory_reservation`、`inventory_ledger`；
  - 库存平衡、状态、唯一业务号和预占号约束。

不要修改已发布的 V1–V9；后续数据库变更新建 V10。

---

## 5. Redis 结构

当前已实现：

```text
draw:quota:{activityId}:{userId}:{yyyyMMdd}
draw:reservation:{requestId}
draw:reservation:timeouts
lock:draw:{activityId}:{userId}
luckyhub:stream:lottery
```

reservation hash 核心字段：

```text
requestId
activityId
userId
drawCount
drawDate
status
createdAt
```

reservation 状态：

```text
RESERVED
CONFIRMED
RELEASED
```

Lua：

```text
src/main/resources/redis/lottery/reserve_draw_quota.lua
src/main/resources/redis/lottery/confirm_draw_quota.lua
src/main/resources/redis/lottery/release_draw_quota.lua
```

`draw:request:*` 与 `draw:result:*` 是未来可选缓存，第一版未实现；当前直接回源 MySQL。

---

## 6. 统一用户/API

已实现七个统一接口：

```text
GET  /api/lottery/activities/{activityId}
POST /api/lottery/draws
GET  /api/lottery/draws/{requestId}
GET  /api/lottery/orders
GET  /api/lottery/records
GET  /api/benefits
GET  /api/benefits/{id}
```

数据范围：

- 普通 USER 只能查询自己的抽奖和权益；
- 无 `read:all` 时显式传别人 `userId` 直接拒绝，不会静默替换；
- 管理员通过权限获得 all scope，不需要单独用户端/管理端业务实现。

权限码：

```text
lottery:activity:read
lottery:draw
lottery:draw:read
lottery:record:read
benefit:read
lottery:order:read:all
lottery:draw:read:all
lottery:record:read:all
benefit:read:all
```

公开活动 JSON 不返回中奖权重、独立未中奖权重或精确库存。

---

## 7. 核心执行顺序

```text
POST /api/lottery/draws
  -> JWT AuthenticationFilter
  -> PermissionInterceptor
  -> LotteryController
  -> DrawCommand 校验
  -> LoginContext 取 userId
  -> MySQL 第一次幂等查询
  -> 资格校验与单份活动奖品快照
  -> Redisson draw lock
  -> MySQL 第二次幂等查询
  -> Lua 预占 1/10 次配额
  -> REQUIRES_NEW 创建 PROCESSING 订单
  -> 释放 draw lock
  -> REQUIRED 抽奖业务事务
       -> 权重候选
       -> MySQL 条件扣库存
       -> 抽奖记录/权益
       -> PROCESSING -> SUCCESS
       -> Outbox
  -> 同步返回 DrawOrderView
  -> Outbox relay
  -> Redis Stream
  -> confirm/release quota
  -> 异步权益履约
  -> ACK
  -> 对账任务修复异常终态
```

---

## 8. 需要配置的内容

本地 `.env` 中需要确认：

```properties
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=luckyhub
MYSQL_USER=luckyhub
MYSQL_PASSWORD=<本地密码>
MYSQL_ROOT_PASSWORD=<Docker MySQL root 密码>

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=<本地密码>

JWT_SECRET=<Base64 编码的32字节以上密钥>
JWT_EXPIRATION=720000

OSS_ENABLED=true
OSS_REGION=cn-hangzhou
OSS_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com
OSS_BUCKET=<bucket名>
OSS_ACCESS_KEY_ID=<RAM AccessKey ID>
OSS_ACCESS_KEY_SECRET=<RAM AccessKey Secret>
OSS_PUBLIC_BASE_URL=https://<bucket>.<endpoint-host>
```

抽奖默认配置在 `src/main/resources/application.yaml`：

- `luckyhub.lottery.*`：时区、锁等待、处理超时、对账、Outbox batch。
- `luckyhub.messaging.*`：Stream key/group、consumer batch/poll/claim idle、Outbox lease。

第一版可直接使用默认值，无需增加新密钥。

---

## 9. 最重要的文档

抽奖主教学文档：

```text
docs/LuckyHub-抽奖核心流程实现详解.md
```

内容：1151 行，从 `POST /api/lottery/draws` 开始，按真实执行顺序解释所有核心文件和代码。

API 手册：

```text
docs/lottery-api.md
```

内容：467 行，包含七个接口、权限、请求/响应、错误码、Postman/curl 和 requestId 重试规则。

其他文档：

```text
docs/LuckyHub-OSS图片上传实现详解.md
docs/LuckyHub-活动管理实现详解.md
docs/LuckyHub-活动状态定时任务实现详解.md
docs/prize-management-api.md
docs/activity-management-api.md
```

---

## 10. 验证和代码审查结论

最终验证覆盖：

- WIN；
- 独立 NO_WIN；
- 售罄 NO_WIN；
- 候选后库存竞争 NO_WIN 且不重抽；
- 十连抽完整性与全回滚；
- 相同 requestId 并发幂等；
- 40 请求争抢 dailyLimit=17；
- 20 用户争抢库存 5；
- Stream 不可用时 Outbox 保留；
- 失败和超时通过真实 Stream -> Consumer -> Lua -> ACK 释放配额；
- USER self scope 和 ADMIN all scope；
- 真实 JWT Filter/PermissionInterceptor 的深层路径 401/403。

最终全局审查：

- Critical：0；
- Important：0；
- Ready to merge：Yes。

阶段 1 审查逐项结论：

- 库存条件 SQL 在扣减前检查余额，并由 100 请求争抢 10 件的测试证明不会产生负库存；
- 唯一流水记录并校验业务号对应的 SKU、渠道、操作和数量，相同编号不能变更另一份资产；
- 总库存分配 SQL 要求 `total_stock - allocated_stock >= quantity`，所有渠道之和不能超过总库存；
- 阶段 1 没有修改 `lottery`、`activity`、`benefit` 的主业务代码，旧抽奖库存和事件链保持不变；
- `ProductView/SkuView` 没有库存字段，用户商品接口不泄漏库存；
- 业务重复键被翻译为稳定错误码，其他数据库异常由全局异常处理器转换为安全系统错误；
- 与阶段 1 起点 `18b6ad8` 比较，V1–V7 内容没有变化；只新增 V8/V9。

阶段 1 审查结果：Critical 0，Important 0。

---

## 11. 下一阶段与抽奖可选任务

### 当前唯一主线：阶段 2 积分账户与积分商城

下一次开发先从真实阶段 1 接口编写独立实施计划，不直接开始写代码。计划至少覆盖：

1. 用户积分账户和不可变积分流水；
2. 幂等入账、条件扣减和冲正；
3. 单 SKU 积分兑换单；
4. `POINTS` 渠道库存的预占、确认和释放；
5. 用户余额/流水 API 和管理员积分调整 API；
6. 并发扣减、重复业务号、兑换失败补偿和权限测试。

阶段 2 不做优惠券、会员、现金订单、支付或物流，这些继续按总路线进入后续阶段。

以下内容是抽奖核心的非阻断优化，不替代阶段 2 主线。

这些不是第一版阻断项，不需要同时完成。

### 优先级 A：消息可运维性

1. 为永久毒消息增加失败次数、失败原因和指标。
2. 超过阈值后写入 quarantine/DLQ，再 ACK 原消息。
3. 瞬时 Redis/MySQL/履约失败仍保持 pending 重试。

### 优先级 B：事件身份强校验

1. 权益履约入口接收完整事件身份。
2. 锁定 benefit 后交叉校验 `drawRecordId/prizeId/prizeType/userId/orderId/activityId`。
3. 身份不一致进入不可自动重试的毒消息分支。

### 优先级 C：数据库防御约束

新建 V8，不修改 V5，可添加：

- `result_type` 枚举 CHECK；
- benefit status 枚举 CHECK；
- `WIN` 必须有奖品快照；
- `NO_WIN` 奖品和权益字段必须为 NULL。

### 优先级 D：真实第三方权益

- 对接优惠券平台；
- 对接积分账户；
- 对接会员服务；
- 对接实物收货地址和物流履约；
- 每个外部系统需要独立幂等号、超时、重试与人工补偿。

### 其他低优先级项

- 需要时才增加 `draw:request:*` / `draw:result:*` 缓存；
- 如管理查询量增大，评估 `draw_date` 查询索引；
- 两条端到端测试的 quota 断言仍可进一步固定 reservation `drawDate`，消除极低概率跨零点抖动。

---

## 12. 安全与工作区提醒

- 不要提交 `.env`。
- 不要提交 AccessKey CSV。
- 不要在命令输出、README、教学文档或测试日志中打印真实密钥。
- 未跟踪的 `.superpowers` 和 `.codex-progress` 是本次开发过程产物，不属于生产代码。
- 不要为了让 `git status` 视觉干净而盲目删除用户未跟踪文件。

---

## 13. 下次会话的建议读取顺序

1. `docs/LuckyHub-迷你商城下一阶段执行总路线.md`
2. `docs/LuckyHub-开发进度交接总结.md`
3. `git status --short` 与 `git log -5 --oneline`
4. 阶段 2 规划时定向读取：
   - 总体设计：`docs/superpowers/specs/2026-08-08-lottery-mini-mall-design.md`
   - 阶段 1 计划：`docs/superpowers/plans/2026-08-08-catalog-reward-channel-inventory.md`
   - 阶段 1 API：`docs/catalog-reward-inventory-api.md`
5. 如需维护旧抽奖，再读取：
   - 主流程：`docs/LuckyHub-抽奖核心流程实现详解.md`
   - API：`docs/lottery-api.md`
   - 最终设计：`docs/superpowers/specs/2026-07-31-lottery-core-design.md`
   - 实现计划：`docs/superpowers/plans/2026-07-31-lottery-core.md`

不需要重新读取全部历史聊天或所有 `.superpowers/sdd` 过程文件。
