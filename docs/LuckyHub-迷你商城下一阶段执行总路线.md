# LuckyHub 迷你商城下一阶段执行总路线

> 更新时间：2026-08-09
> 用途：下一次开发会话的唯一入口。

## 1. 当前结论

阶段 1–5 已完成。当前唯一主线是：

> **阶段 6：实物领取、收货地址快照和物流履约。**

阶段 5 已把抽奖奖励统一为五类真实流程：优惠券、积分、会员、实物和奖励抽奖次数。实物目前故意停在 `CLAIM_PENDING`，没有提前创建物流任务。

## 2. 下次直接这样开始

```text
请先读取 docs/LuckyHub-迷你商城下一阶段执行总路线.md 和
docs/LuckyHub-开发进度交接总结.md，执行恢复检查，然后为阶段 6
“实物领取、地址快照与物流履约”先写设计规格；规格确认前不要改代码。
```

恢复检查：

```powershell
git status --short --branch
git log -8 --oneline
docker compose ps
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 test
```

`.codex-progress/` 和 `.superpowers/` 是用户的未跟踪辅助目录，不要提交或删除。

## 3. 已完成阶段

```text
阶段 1：商品、统一奖励定义、渠道库存
阶段 2：积分账户、不可变流水、积分兑换
阶段 3：优惠券、会员、现金订单、模拟支付
阶段 4：统一异步履约、四类本地模拟供应方
阶段 5：抽奖奖励快照、身份隔离、奖励次数、资产投影、五类端到端
```

阶段 5 入口文档：

```text
docs/superpowers/plans/2026-08-09-lottery-unified-reward-fulfillment.md
docs/lottery-reward-fulfillment-api.md
docs/progress/阶段5-任务9-阶段交付完成介绍.md
```

## 4. 当前可运行基线

- Java 17、Spring Boot、MyBatis-Plus、MySQL、Redis；
- Flyway 已到 V16；
- 43 张业务表；
- 全量测试 407 项，失败 0、错误 0、跳过 0；
- 可执行 JAR：`target/luckyhub-0.0.1-SNAPSHOT.jar`；
- JAR 大小：69,748,317 字节；
- OpenAPI 冒烟已验证抽奖、权益和奖励定义端点；
- 空库脚本：`scripts/Verify-Phase5FreshMigration.ps1`。

## 5. 阶段 6 必须设计的内容

1. 用户中奖后在领取期限内提交收货地址；
2. 地址必须形成不可变快照，后续修改地址簿不能改变已领取订单；
3. `CLAIM_PENDING -> CLAIMED -> FULFILLING -> SHIPPED/DELIVERED` 状态机；
4. 只有领取成功后才创建 `LOGISTICS` 履约任务；
5. 物流履约号、地址提交和运单回调都要幂等；
6. 超时未领取、履约失败、人工重试/终止和隐私脱敏；
7. 物流查询只返回必要信息，手机号和详细地址不能进入日志或错误摘要；
8. 继续使用本地模拟物流，真实供应商通过可替换 Gateway 接入；
9. 空库迁移、并发领取、重复回调和可执行 JAR 验收。

## 6. 阶段 6 明确不能破坏的边界

- 不修改已发布的 V1–V16，只新增 V17 及以后迁移；
- 不改变五类奖励冻结快照和身份交叉校验；
- 不让实物中奖时直接创建物流任务；
- 不把明文地址、手机号、密钥写入事件、日志或履约安全错误；
- 不删除旧权益字段，新字段继续保持向后兼容；
- 不把本地模拟供应方写死到业务服务，保留 Gateway 替换能力。

## 7. 推荐读取顺序

1. 本文档；
2. `docs/LuckyHub-开发进度交接总结.md`；
3. `docs/lottery-reward-fulfillment-api.md`；
4. `docs/fulfillment-gateway-api.md`；
5. `docs/superpowers/specs/2026-08-08-lottery-mini-mall-design.md`；
6. 阶段 6 新设计与计划（创建后）。
