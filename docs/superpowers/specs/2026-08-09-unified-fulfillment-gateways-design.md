# LuckyHub 阶段 4：统一异步履约与模拟外部系统设计

> 日期：2026-08-09
>
> 状态：已由用户确认设计方向，等待书面复核
>
> 范围：只建设通用履约底座、四类可替换 Gateway、四套本地模拟外部系统及运维接口；不迁移抽奖奖励，不实现真实地址和物流闭环。

## 1. 为什么需要阶段 4

阶段 2 和阶段 3 已经拥有 LuckyHub 内部的积分、优惠券和会员资产能力，但真实公司系统或第三方平台不会和 LuckyHub 共用一张数据库表。外部调用可能超时、重复、限流、返回业务失败，也可能已经成功但响应丢失。

如果抽奖或订单直接调用外部 API，会出现三个核心风险：

- LuckyHub 事务回滚，但外部平台已经发券或加积分；
- 消息重试导致用户收到两张券、两次积分或重复会员时长；
- 一条永久失败的毒消息反复重试，长期堵塞后续履约。

阶段 4 用稳定履约号、独立调用流水、状态查询、重试和隔离解决这些问题，并为阶段 5 抽奖奖励迁移及阶段 6 实物物流提供稳定接口。

## 2. 目标与完成标准

完成后系统应具备：

- 使用稳定 `fulfillmentNo` 创建幂等履约任务；
- 通过 `CouponGateway`、`PointsGateway`、`MembershipGateway`、`LogisticsGateway` 调用可替换外部能力；
- 四套模拟供应方分别维护自己的外部业务记录，禁止直接修改 LuckyHub 资产表伪装外部成功；
- 支持成功、可重试失败、永久失败、结果未知四种结果；
- 远程调用不持有 LuckyHub 数据库事务或行锁；
- 可重试错误按指数退避再次执行；
- 结果未知时优先按履约号查询供应方，不盲目重复发放；
- 达到最大次数或永久失败后进入隔离记录，任务不再自动占用 Worker；
- 管理员可以查询任务、调用记录、隔离原因，执行人工重试或终止；
- 测试能够证明“供应方成功但 LuckyHub 超时”不会重复发券、加积分、续会员或创建运单。

## 3. 范围边界

### 3.1 本阶段包含

- 通用履约任务、每次调用尝试和隔离记录；
- 领取租约、崩溃恢复、指数退避、最大尝试次数；
- Gateway 请求、响应、查询和错误分类；
- 模拟优惠券、模拟积分、模拟会员、模拟物流供应方；
- 模拟供应方故障模式控制；
- 定时 Worker、未知结果对账和过期租约恢复；
- 管理员查询、人工重试和终止 API；
- 权限、数据库约束、并发、日志脱敏和端到端测试。

### 3.2 本阶段不包含

- 将现有抽奖 `user_benefit` 切换到统一履约，这是阶段 5；
- 用户地址簿、地址加密、发货单、物流轨迹和签收，这是阶段 6；
- 真实顺丰、京东物流、微信支付或企业内部服务；
- RabbitMQ/Kafka 等新中间件；
- 前端页面；
- 多租户、动态供应商路由和复杂熔断平台。

## 4. 总体架构

继续使用单体 Spring Boot、MySQL 和现有调度能力，新增两个清晰边界：

```text
fulfillment
├─ 任务创建、领取、完成、重试、隔离、人工操作
├─ Worker 与对账调度
└─ 管理查询 API

integration
├─ gateway        四类稳定接口及请求/结果模型
└─ simulator      四套独立模拟供应方与故障控制
```

业务模块以后只负责创建履约任务，不依赖模拟实现。Worker 根据任务类型构造对应 Gateway 请求。未来接入真实 HTTP/SDK 时，只增加新的 Gateway 适配器并通过配置替换，不修改任务状态机。

## 5. 数据模型

### 5.1 `fulfillment_task`

保存一项需要外部系统完成的工作：

- `fulfillment_no`：稳定外部幂等键，唯一；
- `source_type`、`source_id`：来源类型和来源业务 ID；
- `fulfillment_type`：`COUPON/POINTS/MEMBERSHIP/LOGISTICS`；
- `target_user_id`：目标用户；
- `request_payload`：创建时冻结的非敏感请求快照 JSON；
- `status`：任务状态；
- `attempt_count`、`max_attempts`：已执行和最大执行次数；
- `next_attempt_at`：下次允许领取时间；
- `lease_token`、`lease_until`：多实例 Worker 的短租约；
- `external_reference`：供应方返回的券号、流水号、会员记录号或运单号；
- `last_error_category`、`last_error_code`、`last_error_message`：安全错误摘要；
- `completed_at`、`created_at`、`updated_at`。

任务状态：

```text
PENDING -> PROCESSING -> SUCCEEDED
              |
              +-> RETRY_WAITING -> PROCESSING
              +-> RECONCILING  -> PROCESSING / SUCCEEDED / QUARANTINED
              +-> QUARANTINED

PENDING / RETRY_WAITING / RECONCILING / QUARANTINED -> TERMINATED（管理员）
QUARANTINED -> RETRY_WAITING（管理员重试）
```

### 5.2 `fulfillment_attempt`

每次 Gateway 调用或查询都新增不可变流水：

- 任务 ID、履约号、尝试序号；
- 操作：`EXECUTE` 或 `QUERY`；
- 开始和结束时间、耗时；
- 结果：`SUCCESS/RETRYABLE/PERMANENT/UNKNOWN/NOT_FOUND`；
- 外部引用和安全错误摘要；
- 请求摘要哈希，不保存密钥、完整地址或原始异常堆栈。

### 5.3 `fulfillment_quarantine`

记录进入人工处理的最终原因：任务 ID 唯一、履约号、进入原因、错误分类、错误摘要、进入时间、解除时间、解除操作人和备注。重复进入不会产生多条未解除记录。

### 5.4 模拟供应方表

四套供应方分别使用独立表：

- `sim_coupon_record`
- `sim_points_record`
- `sim_membership_record`
- `sim_logistics_record`

每张表都以 `fulfillment_no` 唯一，保存供应方自己的外部引用、业务参数、状态和时间。积分与会员模拟表保存供应方侧的发放量或时长快照；物流表保存脱敏收件摘要、模拟运单号和供应方状态。

故障控制使用 `sim_failure_rule`，按 Gateway 类型保存下一批调用的故障模式和剩余次数。仅管理员开发接口可设置。

## 6. Gateway 接口

四个接口都提供两类能力：

```text
execute(request)  按 fulfillmentNo 幂等执行
query(fulfillmentNo)  查询供应方是否已完成
```

统一结果包含：

- `status`：`SUCCEEDED/RETRYABLE_FAILURE/PERMANENT_FAILURE/UNKNOWN/NOT_FOUND`；
- `externalReference`；
- `errorCode` 和安全错误信息。

请求类型保持独立：

- 券：目标用户、模板 ID、数量；
- 积分：目标用户、积分数量、业务原因；
- 会员：目标用户、会员产品 ID、顺延天数；
- 物流：来源订单号、脱敏收件人、脱敏手机号、区域摘要和包裹描述。

独立请求避免使用一个任意 Map 把类型错误拖到运行时，也便于以后替换真实 SDK。

## 7. 模拟物流边界

用户已选择轻量方案 A。阶段 4 的模拟物流只验证 Gateway 和一致性，不建立真实地址或发货领域。

测试请求示例：

```text
fulfillmentNo = FUL-LOG-1001
orderNo       = ORDER-1001
receiver      = 张**
phone         = 138****1234
region        = 浙江省杭州市
package       = 测试礼盒 x1
```

模拟供应方返回 `MOCK-SF-100001`。如果创建成功后模拟响应超时，LuckyHub 将任务转为 `RECONCILING`，随后用 `FUL-LOG-1001` 查询并取回同一运单号，不会创建第二票。

阶段 6 再由真实地址快照和发货单构造相同 `LogisticsGateway` 请求。

## 8. 任务创建与幂等

任务创建命令包含调用方提供的稳定 `fulfillmentNo`。相同编号、相同来源、类型、用户和请求快照重复创建，返回原任务；同一编号复用不同参数，返回幂等冲突。

数据库唯一键是最终防线。任务创建只写本地数据库，不调用 Gateway，因此可以安全地参与未来的抽奖或订单本地事务。

本阶段提供管理测试入口创建独立任务，用于完整验证底座；阶段 5 再由抽奖奖励事务创建正式任务。

## 9. Worker 与事务边界

一次执行分为三个阶段：

1. 短事务领取：条件更新一条到期任务为 `PROCESSING`，写入随机 `leaseToken` 和 `leaseUntil`，提交事务。
2. 无事务远程调用：根据请求快照调用 Gateway。此时不持有任务行锁和数据库连接事务。
3. 短事务落账：验证 `leaseToken` 仍属于当前 Worker，新增 attempt，并把任务更新为成功、等待重试、待对账或隔离。

Worker 每轮只处理有界批量，默认 50 条。多实例通过条件领取和租约避免同一时刻重复执行；即使租约失效导致重复调用，供应方的 `fulfillmentNo` 唯一约束仍保证业务只发生一次。

## 10. 错误分类与重试

```text
RETRYABLE  网络失败、限流、供应方 5xx
PERMANENT  参数错误、模板不存在、账户不存在
UNKNOWN    请求可能已被供应方执行，但 LuckyHub 没收到确定结果
```

- `RETRYABLE`：进入 `RETRY_WAITING`，指数退避 `baseDelay × 2^(attemptCount-1)`，并限制最大退避时间；
- `PERMANENT`：立即进入 `QUARANTINED`；
- `UNKNOWN`：进入 `RECONCILING`，下一次只执行 `query`；
- 查询成功：任务转 `SUCCEEDED`；
- 查询未找到：返回 `RETRY_WAITING`，再安全执行；
- 查询仍未知或可重试：继续有界对账；
- 达到 `maxAttempts`：进入 `QUARANTINED`。

错误信息先映射成长度受限的安全摘要，禁止保存访问令牌、数据库密码、完整手机号、完整地址和原始堆栈。

## 11. 租约恢复与隔离

定时恢复任务扫描 `PROCESSING` 且 `lease_until <= now` 的记录。由于无法确定外部调用是否已经执行，恢复后统一进入 `RECONCILING`，而不是直接重发。

隔离记录使永久错误和超限任务退出自动 Worker。管理员阅读原因并修复外部数据后，可以：

- 人工重试：解除隔离，任务进入 `RETRY_WAITING`；
- 终止：任务进入 `TERMINATED`，保留全部历史；
- 不允许删除任务或 attempt 来伪造成功。

## 12. 管理 API 与权限

新增权限：

- `fulfillment:create`：创建测试履约任务；
- `fulfillment:read`：查询任务、尝试和隔离详情；
- `fulfillment:operate`：人工重试和终止；
- `simulator:control`：设置模拟供应方故障模式。

最小接口：

- `POST /api/admin/fulfillment/tasks`
- `GET /api/admin/fulfillment/tasks`
- `GET /api/admin/fulfillment/tasks/{fulfillmentNo}`
- `POST /api/admin/fulfillment/tasks/{fulfillmentNo}/retry`
- `POST /api/admin/fulfillment/tasks/{fulfillmentNo}/terminate`
- `POST /api/admin/simulators/failure-rules`

这些接口全部经过现有 JWT Filter 和权限拦截器。模拟控制接口仍可随本地环境运行，但只有 ADMIN 默认拥有权限；生产接入真实 Gateway 时应通过配置关闭模拟控制器。

## 13. 配置

配置项使用 `luckyhub.fulfillment.*`：

- Worker 是否启用、轮询间隔、初始延迟、批量大小；
- 租约时长；
- 默认最大尝试次数；
- 基础退避和最大退避；
- 安全错误摘要最大长度；
- 模拟供应方是否启用。

默认值适合本地运行，不增加新的密钥或外部依赖。

## 14. 测试策略

按 TDD 覆盖：

- V14/V15 空库迁移、表、约束、索引和权限；
- 四个 Gateway 成功及相同履约号幂等；
- 同一履约号不同参数冲突；
- 可重试失败的退避时间；
- 永久失败立即隔离；
- 供应方成功后响应超时，再查询恢复成功且外部记录只有一条；
- Worker 调用 Gateway 时事务未激活；
- 多线程领取同一任务只有一个租约赢家；
- 过期租约进入对账而非盲目执行；
- 人工重试、终止和权限 401/403；
- 请求/错误/日志不包含密钥、完整手机号或完整地址；
- 阶段 1～3 和抽奖全量回归。

## 15. 数据库迁移顺序

阶段 4 不修改 V1～V13：

- V14：`fulfillment_task`、`fulfillment_attempt`、`fulfillment_quarantine` 和四项权限；
- V15：四套模拟供应方表、故障规则及必要唯一索引。

每个迁移先通过 schema contract，再进入服务实现。

## 16. 实施任务顺序

1. 履约数据库、枚举和领域骨架；
2. 四类 Gateway 契约与统一结果模型；
3. 四套幂等模拟供应方和故障控制；
4. 任务创建、查询和幂等；
5. 领取租约、事务外执行和结果落账；
6. 指数退避、未知结果对账、租约恢复与隔离；
7. 管理 API、权限和安全边界；
8. 并发、端到端、空库迁移、全量测试、打包和交接。

每个任务结束都生成一份中文完成介绍，固定包含：为什么需要、负责什么、具体实现、业务例子和测试证据。

## 17. 后续阶段如何使用

阶段 5 在抽奖中奖本地事务中创建对应类型的履约任务，再由本阶段 Worker 发券、加积分或续会员。阶段 6 创建真实发货单后，通过同一个 `LogisticsGateway` 创建和查询运单。

因此阶段 4 完成的是“可靠调用外部系统的发动机”，不是提前完成抽奖奖励迁移或真实物流业务。
