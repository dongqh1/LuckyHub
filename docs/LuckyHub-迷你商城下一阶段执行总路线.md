# LuckyHub 迷你商城下一阶段执行总路线

> 更新时间：2026-08-08  
> 用途：下一次开发会话的唯一入口。先恢复环境和仓库状态，再进入当前阶段计划。  
> 已批准设计：`docs/superpowers/specs/2026-08-08-lottery-mini-mall-design.md`

## 1. 明天从这里开始

新会话直接输入：

```text
请先读取 docs/LuckyHub-迷你商城下一阶段执行总路线.md，
再执行其中“恢复检查”，然后根据“当前阶段”的规划输入编写阶段 2 实施计划。
计划确认前不要写阶段 2 代码，不要跳到后续阶段，不要修改已经发布的 V1-V9。
```

恢复检查：

```powershell
git status --short --branch
git log -5 --oneline
git rev-list --left-right --count origin/master...master
docker compose ps
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 test
```

如果 Docker Desktop 没有运行，先启动 Docker Desktop，再执行：

```powershell
docker compose up -d
docker compose ps
```

只有 MySQL、Redis 健康且全量测试通过后，才能开始修改代码。如果测试因环境失败，先修复环境，不把失败基线带入新功能。

## 2. 当前仓库基线

- 分支：`master`；
- 已批准设计提交：`18b6ad8 docs: design lottery mini mall`；
- 现有抽奖核心、Outbox、Redis Stream、权益占位处理器保持可用；
- V1-V9 是已发布迁移，只能新增 V10 及后续迁移；
- 阶段 1 功能基线：`90257bb feat: expose channel inventory API`；
- `.codex-progress/` 和 `.superpowers/` 是未跟踪辅助目录，不要提交；
- Windows + PowerShell 7 + Java 17；
- Maven 统一通过 `scripts/Invoke-Maven.ps1` 执行。

## 3. 当前阶段

阶段 1 已完成。阶段 2 实施计划已经生成，当前等待用户确认：

> **积分账户与积分商城**

规划输入：

```text
docs/superpowers/specs/2026-08-08-lottery-mini-mall-design.md
docs/superpowers/plans/2026-08-08-catalog-reward-channel-inventory.md
docs/catalog-reward-inventory-api.md
```

阶段 2 详细计划：

```text
docs/superpowers/plans/2026-08-08-points-account-redemption.md
```

规划完成介绍：

```text
docs/progress/阶段2-规划-积分账户与积分商城执行计划完成介绍.md
```

下一步由用户审阅并确认计划。确认后从任务 1 的第一个未勾选步骤开始执行。确认前不编写积分账户或兑换订单代码，也不提前进入优惠券、会员、支付、地址或物流。

阶段 1 验收证据：

- 功能基线：`90257bb feat: expose channel inventory API`；
- V8/V9 从空数据库随 V1-V7 依次迁移成功；
- 空库迁移契约：14 个测试，0 失败，0 错误；
- 阶段 1 聚焦测试：39 个测试，0 失败，0 错误；
- 全量回归：274 个测试，0 失败，0 错误；
- 打包：`BUILD SUCCESS`，生成 `target/luckyhub-0.0.1-SNAPSHOT.jar`；
- 审查：Critical 0，Important 0；
- 阶段 1 API：`docs/catalog-reward-inventory-api.md`；
- 阶段 1 完成介绍：`docs/progress/阶段1-任务8-阶段交付完成介绍.md`。

## 4. 六阶段执行顺序

### 阶段 1：商品、统一奖励与渠道库存基础（已完成）

交付：

- 商品与 SKU；
- 现金价、积分兑换价和可用购买方式；
- 统一奖励定义；
- 总库存、渠道库存、预占记录和库存流水；
- 管理 API 和权限；
- 与现有抽奖兼容的可空关联字段。

阶段验收后，依据真实接口签名编写阶段 2 计划。

### 阶段 2：积分账户与积分商城

依赖：阶段 1 的 SKU、积分兑换价和渠道库存。

交付：

- 用户积分账户；
- 不可变积分流水；
- 幂等入账、条件扣减和冲正；
- 积分兑换单；
- 积分兑换库存预占、确认和释放；
- 用户余额/流水 API；
- 管理员积分调整 API。

完成定义：并发扣减不出现负余额；重复业务号不重复记账；兑换失败通过反向流水恢复积分和库存。

### 阶段 3：优惠券、会员与现金订单

依赖：阶段 1 的商品、SKU、渠道库存；阶段 2 的积分账户只作为未来消费返积分基础，不参与混合支付。

交付：

- 优惠券模板和用户券包；
- 锁券、核销、释放和过期；
- 月卡、季卡、年卡及有效期顺延；
- 固定价格顺序：商品原价 → 会员折扣 → 优惠券 → 应付金额；
- 单 SKU 立即购买订单；
- 模拟支付单、回调和支付超时；
- 订单价格快照。

完成定义：重复支付回调不重复确认订单；取消和超时释放券及库存；历史订单不受商品、券或会员规则修改影响。

### 阶段 4：统一异步履约底座与模拟外部系统

依赖：阶段 2、3 已有真实的积分、券和会员领域入口。

交付：

- `fulfillment_task`、`fulfillment_attempt` 和隔离记录；
- 稳定 `fulfillment_no`；
- `CouponGateway`、`PointsGateway`、`MembershipGateway`、`LogisticsGateway`；
- 四套幂等模拟系统；
- 事务外远程调用；
- 可重试、永久失败、结果未知分类；
- 指数退避、最大尝试次数、DLQ/quarantine、人工重试和主动对账。

完成定义：模拟第三方成功但本地超时不会重复发放；毒消息不会永久堵塞 Redis Stream；每次调用都有安全流水。

### 阶段 5：抽奖奖励迁移到统一履约

依赖：阶段 4 的统一履约任务和 Gateway。

交付：

- 活动奖项引用 `reward_definition`；
- 抽奖可发商品、积分、券、会员和抽奖资格；
- 完整事件身份交叉校验；
- 现有历史中奖记录和权益查询兼容；
- 旧 `PrizeType` 路径有明确迁移或退役策略。

完成定义：五种奖励均有端到端测试；事件身份不一致时不调用模拟系统并进入隔离处理。

### 阶段 6：实物领奖与模拟物流

依赖：阶段 5 可产生商品型奖励；阶段 4 有 `LogisticsGateway`。

交付：

- 用户地址簿；
- 加密地址和脱敏响应；
- 抽奖实物零元订单；
- 领奖截止时间和超时回补规则；
- 发货单、运单号、物流轨迹；
- 回调验签模拟和主动查询；
- `SHIPPED`、`IN_TRANSIT`、`DELIVERED` 状态闭环。

完成定义：抽中实物后可以确认地址、模拟发货和签收；越权用户无法读取他人地址；日志不出现地址明文。

## 5. 每阶段固定工作方式

每个阶段都遵守：

1. 先阅读已批准设计和本阶段计划；
2. 按计划使用 TDD：失败测试 → 最小实现 → 测试通过；
3. 一个可审查任务一个提交；
4. 每个迁移先写 schema contract；
5. 每个资产变化必须有业务幂等号和流水；
6. 外部调用不得持有数据库事务或行锁；
7. 单项测试通过后再跑相关包测试；
8. 阶段结束运行全量测试和打包；
9. 完成代码审查后再更新本路线的当前阶段；
10. 不在同一提交混入下一阶段代码。

验证命令模板：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=完整测试类名' test

pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 test

pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 package '-DskipTests'
```

## 6. 关键约束

- 抽奖是核心，商城保持最小范围；
- 不做购物车、真实支付、退款售后、积分现金混合支付；
- 金额统一使用整数分，积分统一使用整数；
- 积分不能兑换现金或提现；
- 积分兑换不使用现金优惠券或会员价格折扣，也不返积分；
- 会员折扣先于优惠券计算；
- 商城、积分兑换和抽奖库存不自动互借；
- 数据库是资产与订单最终事实来源；
- Redis 不保存最终积分、券、会员或订单账本；
- 历史记录使用快照，不能被当前配置覆盖；
- 所有模拟外部能力必须经 Gateway，禁止直接修改业务表伪装远程成功。

## 7. 阶段切换规则

只有同时满足以下条件才进入下一阶段：

- 本阶段计划中的复选框全部完成；
- 相关测试和全量测试通过；
- `git diff --check` 无错误；
- tracked 工作区干净；
- 文档与真实接口一致；
- 完成本阶段代码审查；
- 本路线已更新当前阶段和最新验收证据。

如果某阶段发现设计需要改变，先更新设计文档并取得确认，再调整实施计划；不要在代码中临时发明新的业务规则。
