# LuckyHub 阶段 5：抽奖奖励迁移到统一履约设计

> 日期：2026-08-09
> 状态：已批准
> 前置阶段：阶段 4 统一异步履约与本地模拟供应方
> 数据库基线：V1-V15 已发布，只能新增 V16 及之后迁移

## 1. 目标

把抽奖中奖结果从旧 `PrizeType` 状态切换迁移到 `reward_definition` 驱动的统一奖励流程，并保证：

- 新奖品必须绑定统一奖励定义；
- 优惠券、积分、会员经过阶段 4 异步履约后成为商城内真实可用资产；
- 商品奖形成待领取权益，地址和物流留到阶段 6；
- 额外抽奖次数进入数据库账户和不可变流水，后续抽奖优先使用；
- 事件身份不一致时不产生任何供应方或本地资产效果，而是进入隔离；
- 已存在且未绑定奖励定义的奖品继续按旧路径运行；
- 既有中奖记录、权益查询和抽奖幂等语义保持兼容。

## 2. 明确不做

- 不修改 V1-V15；
- 不接入真实第三方厂商账号，仍使用阶段 4 的四套本地模拟供应方；
- 不采集收货地址，不创建包裹、运单或物流轨迹；
- 不自动猜测旧奖品对应的券模板、会员产品或 SKU；
- 不在抽奖主事务中调用 Gateway；
- 不删除旧 `PrizeType`、旧 `BenefitFulfillmentHandler` 或历史数据。

## 3. 方案选择

### 3.1 采用：事件驱动统一履约与本地资产投影

抽奖事务只保存不可变奖励快照和 Outbox 事件。消息消费者完成身份校验后，为券、积分和会员创建阶段 4 履约任务。Worker 在事务外调用模拟供应方；任务成功后，本地投影器使用已有幂等领域服务创建 `user_coupon`、积分账户流水或 `user_membership`，最后把权益标为可用。

优点是抽奖不受供应方延迟影响，并完整复用阶段 4 的幂等、租约、重试、对账和隔离能力。

### 3.2 不采用：消费者直接调用本地资产服务

此方案代码较少，但会绕过阶段 4，无法验证模拟供应方、未知结果对账和统一履约任务，不满足本阶段目标。

### 3.3 不采用：抽奖事务同步发放

供应方故障会拖长或回滚抽奖事务，且难以安全处理“供应方成功但响应丢失”，不采用。

## 4. 兼容策略

### 4.1 新旧双轨

`marketing_prize.reward_definition_id` 的含义固定为：

- 非空：统一奖励新路径；
- 为空：旧兼容路径。

新建奖品必须提供 `rewardDefinitionId`。更新旧奖品时可以绑定奖励定义，但绑定后不能再清空，也不能换成与历史类型冲突的奖励。现有空值记录不会被 V16 自动填充。

### 4.2 `RewardType` 与 `PrizeType`

新路径以 `RewardType` 为唯一业务依据，`PrizeType` 只保留为现有接口、查询和历史快照的展示兼容字段：

| RewardType | 兼容 PrizeType | 处理结果 |
|---|---|---|
| `PRODUCT` | `PHYSICAL` | 待领取 |
| `COUPON` | `COUPON` | 异步履约后生成用户券 |
| `POINTS` | `POINTS` | 异步履约后增加积分 |
| `MEMBERSHIP` | `MEMBERSHIP` | 异步履约后更新会员 |
| `DRAW_CHANCE` | `DRAW_CHANCE` | 增加奖励抽奖次数 |

`PrizeType` 新增 `DRAW_CHANCE`，但新流程不得根据它决定发放方式。

## 5. 奖励配置与不可变快照

### 5.1 奖励定义校验

创建奖励定义时必须验证：

- `PRODUCT.target_id` 指向启用的 `product_sku`；
- `COUPON.target_id` 指向启用且当前配置有效的 `coupon_template`；
- `MEMBERSHIP.target_id` 指向启用的 `membership_product`；
- `POINTS` 和 `DRAW_CHANCE` 的 `target_id` 必须为空；
- `quantity` 必须能安全转换为下游所需的 `int` 或 `long`，乘法不得溢出；
- `config_snapshot` 继续只保存合法 JSON 扩展配置，不能替代服务端目标校验。

### 5.2 抽奖资格快照

`DrawEligibilityService` 对已绑定奖品加载奖励定义和目标实体，生成规范化 `RewardSnapshot`：

- `rewardDefinitionId`、`rewardCode`、`rewardType`；
- `targetId`、`quantity`；
- 服务端解析出的规范化 `deliveryPayload`；
- 上述字段规范序列化后的 SHA-256 `rewardFingerprint`。

规范化 payload：

- 券：模板 ID、模板编码、数量；
- 积分：积分数量和固定原因“抽奖奖励”；
- 会员：会员产品 ID、产品编码、等级、总有效天数；
- 商品：SKU ID、SKU 编码、商品名称、SKU 名称、数量；
- 抽奖次数：次数。

中奖时把快照写入 `lottery_draw_record` 与 `user_benefit`。后续修改奖励定义、模板、会员产品或 SKU 不改变已中奖内容。

## 6. V16 数据模型

### 6.1 中奖与权益快照字段

为 `lottery_draw_record` 和 `user_benefit` 增加：

- `reward_definition_id BIGINT UNSIGNED NULL`；
- `reward_type VARCHAR(30) NULL`；
- `reward_target_id BIGINT UNSIGNED NULL`；
- `reward_quantity BIGINT UNSIGNED NULL`；
- `reward_payload JSON NULL`；
- `reward_fingerprint CHAR(64) NULL`。

`user_benefit` 另增加可空唯一字段 `fulfillment_no VARCHAR(64)`。旧记录以上字段全部允许为空，新路径记录必须由应用服务成组写入。

### 6.2 抽奖次数账户

新增 `draw_chance_account`：

- 每个用户一行；
- `available_balance` 表示可使用次数；
- `reserved_balance` 表示已为处理中抽奖预留的次数；
- 乐观锁版本和审计时间；
- 两个余额均不得为负。

新增不可变 `draw_chance_ledger`：

- `business_type` 为 `LOTTERY_REWARD`、`DRAW_CONSUME`、`DRAW_RELEASE` 或 `MANUAL_ADJUSTMENT`；
- `business_id` 与类型组成唯一键；
- 记录方向、数量、操作后的可用/预留余额；
- 已写流水只允许追加，不允许更新或删除。

新增 `draw_chance_reservation`：

- `request_id` 唯一；
- 保存用户、活动、抽奖日期、请求抽奖数、奖励次数预留数；
- 状态为 `RESERVED`、`CONFIRMED`、`RELEASED`；
- 相同 requestId 不同身份返回幂等冲突。

### 6.3 事件隔离

新增 `lottery_reward_quarantine`：

- `event_id` 唯一；
- 保存 requestId、订单/中奖/权益标识和安全原因码；
- 不保存完整消息、地址、令牌或异常堆栈；
- 状态为 `OPEN`、`RESOLVED`、`IGNORED`；
- 记录隔离时间和可选的人工处置审计字段。

## 7. 抽奖次数预留协议

奖励次数是全活动通用账户，抽奖时优先使用。

在现有用户/活动分布式锁内执行：

1. 按 requestId 幂等创建数据库奖励次数预留；
2. 锁定用户账户，把 `min(availableBalance, drawCount)` 从可用余额移动到预留余额；
3. 计算该用户当天已确认或正在预留的累计奖励次数；
4. 调用现有 Redis 配额预留，传入 `dailyLimit + 当天累计奖励次数`，Redis 仍记录完整 `drawCount`；
5. Redis 拒绝时立即释放本次数据库预留；
6. 抽奖成功事件幂等确认数据库预留：减少预留余额并追加 `DRAW_CONSUME` 流水；
7. 抽奖失败事件幂等释放：预留余额回到可用余额并追加 `DRAW_RELEASE` 流水；
8. 定时对账释放超过处理时限且没有成功订单的预留。

例：每日免费 1 次，用户已有 2 次奖励。第一次抽奖优先预留 1 次奖励，Redis 有效上限为 2；第二次再预留 1 次奖励，有效上限为 3；第三次没有奖励可预留，但当天累计奖励为 2，有效上限仍为 3，因此使用免费次数；第四次被拒绝。

## 8. 统一奖励事件

`PrizeFulfillmentRequestedEvent` 保持对旧消息可反序列化，并为新路径增加可空字段：

- `rewardDefinitionId`；
- `rewardType`；
- `rewardFingerprint`。

旧消息缺少这些字段时，根据数据库权益是否为旧记录决定是否走兼容路径。新路径任一字段缺失都进入隔离。

消费者不得信任消息中的类型或目标值。处理新路径事件前一次性加载并交叉验证：

- envelope 的 requestId、userId、activityId、orderId；
- payload 的 benefitId、drawRecordId、prizeId、rewardDefinitionId；
- 抽奖订单必须成功且身份与 envelope 一致；
- 中奖记录必须属于该订单、用户、活动、请求和奖品；
- 权益必须属于该中奖记录、用户、奖品和奖励定义；
- 中奖记录、权益与 payload 的奖励类型、ID、指纹必须完全一致。

身份不一致时，在短事务中插入隔离记录和消息消费记录，然后正常返回，使 Redis Stream 可以 ACK。此分支不创建 `fulfillment_task`，不调用 Gateway，不变更抽奖次数或商城资产。

## 9. 五种奖励的数据流

### 9.1 券、积分、会员

身份验证成功后，在同一短事务中：

1. 使用 `LOTTERY-BENEFIT-{benefitId}` 作为稳定 `fulfillmentNo`；
2. 用奖励快照构造阶段 4 typed payload；
3. 创建 `source_type=LOTTERY_BENEFIT`、`source_id=benefitId` 的履约任务；
4. 把 `fulfillment_no` 写入权益；
5. 写消息消费幂等记录。

权益保持 `PENDING`，直到履约成功。

本地资产投影器扫描带履约号的非终态权益：

- 任务 `SUCCEEDED`：用履约号作为业务幂等号调用券、积分或会员领域服务，并在同一数据库事务中把权益改为 `AVAILABLE`；
- 任务 `QUARANTINED` 或 `TERMINATED`：把权益改为 `GRANT_FAILED`，仅保存安全错误；
- 管理员重试后任务最终成功：允许 `GRANT_FAILED -> AVAILABLE`；
- 其他任务状态：保持原状态等待下一轮。

### 9.2 商品

身份验证成功后直接把权益从 `PENDING` 改为 `CLAIM_PENDING` 并记录消息消费。阶段 5 不创建 LOGISTICS 任务，不制造虚假地址。阶段 6 用户提交地址后才创建物流履约。

### 9.3 抽奖次数

身份验证成功后调用抽奖次数账户服务，以履约事件 ID/权益 ID 组成稳定业务号增加次数，并在同一事务中把权益改为 `AVAILABLE`、写消息消费记录。重复事件不重复增加。

### 9.4 旧兼容奖品

权益没有奖励快照时，继续调用现有 `BenefitFulfillmentRouter`：实物进入 `CLAIM_PENDING`，其他旧类型进入 `AVAILABLE`。旧路径不得创建统一履约任务。

## 10. 事务、并发和幂等

- 抽奖事务只写订单、中奖记录、权益快照和 Outbox，不调用外部系统；
- 阶段 4 Worker 继续在数据库事务外调用 Gateway；
- `fulfillment_no`、抽奖次数业务流水、预留 requestId、隔离 eventId 和消息消费记录均有唯一约束；
- 消息重复、Worker 重复、投影器重复和并发调度必须得到同一个最终结果；
- 投影器调用的本地券、积分和会员服务必须使用其现有幂等业务号；
- 所有状态变更使用行锁或条件更新，禁止无条件覆盖并发结果；
- 错误消息只保存枚举化安全原因，不落 payload、令牌、完整异常或个人信息。

## 11. 查询与 API 兼容

- 原抽奖响应和权益查询字段不删除；
- `PrizeView` 增加 `rewardDefinitionId` 和 `rewardType`，旧记录返回 null；
- `BenefitView` 增加奖励定义、奖励类型、数量、履约号和履约状态等可空字段；
- 旧调用方忽略新增 JSON 字段即可继续使用；
- 新建奖品请求增加必填 `rewardDefinitionId`，服务端校验调用方传入的兼容 `prizeType` 与奖励类型一致；
- 更新旧奖品允许首次绑定，绑定后不得解绑或改绑为其他奖励定义。

## 12. 错误与恢复

- 奖励配置无效：活动资格加载失败，抽奖不开始；
- 事件身份错误：写事件隔离并 ACK，不自动重试；
- 履约临时失败或未知：沿用阶段 4 重试/QUERY 对账；
- 履约永久失败：阶段 4 隔离，权益投影为 `GRANT_FAILED`；
- 本地资产投影失败：不改变履约成功事实，投影器下轮重试；
- 抽奖次数 Redis 预留失败：立即释放数据库奖励次数预留；
- 进程崩溃遗留预留：对账任务依据订单终态确认或释放；
- 管理员重试成功后：本地投影可从失败状态恢复为可用。

## 13. 测试与验收

阶段 5 必须按 TDD 实现，并覆盖：

1. V16 表、字段、唯一索引、检查约束和 V1→V16 空库迁移；
2. 奖励定义目标校验、类型映射、规范 payload 和指纹稳定性；
3. 新奖品必须绑定、旧奖品仍可查询和运行、绑定不可逆；
4. 五种奖励从真实抽奖到最终权益状态的端到端测试；
5. 券、积分、会员最终进入真实本地资产表且各只有一份；
6. 商品不创建物流任务，状态为 `CLAIM_PENDING`；
7. 抽奖次数信用、优先预留、确认、释放、超时恢复和并发不超扣；
8. 重复 Outbox/Stream 消息、重复投影、重复 Worker 均无重复效果；
9. envelope 或 payload 任一身份错配进入隔离，四套模拟表和本地资产表零新增；
10. 旧抽奖、权益、阶段 1-4、RBAC 和安全测试全部回归；
11. 可执行 JAR 启动，OpenAPI 可读取；
12. UTF-8、本地链接、敏感信息形状、Git 空白和临时数据库残留检查通过。

每个实施任务必须生成 `docs/progress/阶段5-任务N-*.md`，解释为什么需要、具体做了什么，并至少包含一个可理解的业务例子。

## 14. 完成边界

阶段 5 完成时，统一奖励新路径、旧路径兼容、五类奖励、事件隔离和奖励抽奖次数必须可运行且通过完整验收。真实收货地址、物流创建、运单与轨迹仍属于阶段 6，不得提前实现。
