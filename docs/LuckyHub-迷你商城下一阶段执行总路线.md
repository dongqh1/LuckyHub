# LuckyHub 迷你商城下一阶段执行总路线

> 更新时间：2026-08-13
> 用途：下一次开发会话的唯一入口。

## 1. 当前结论

阶段 1–6 已完成。阶段 6 已把抽奖实物、现金实物订单和积分实物兑换接入同一套隐私安全的地址、领取、发货、运单和轨迹链路。

当前没有已批准的“阶段 7”范围。下一次工作应先由负责人选择并批准目标；真实快递、退款、退货、换货和售后仍然只是边界，不代表已经排期。

## 2. 下次直接这样开始

```text
请先读取 docs/LuckyHub-迷你商城下一阶段执行总路线.md、
docs/LuckyHub-开发进度交接总结.md 和
docs/progress/阶段6-最终交付通俗总结.md，执行恢复检查。
阶段 6 已交付；先确认新的业务目标和边界，再写设计与计划，不要自行虚构阶段 7。
```

恢复检查：

```powershell
git status --short --branch
git log -12 --oneline
docker compose ps
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Verify-Phase6FreshMigration.ps1
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
阶段 6：加密地址、不可变快照、实物领取、统一发货、签名轨迹、管理恢复
```

阶段 6 入口：

```text
docs/superpowers/specs/2026-08-09-physical-claim-and-shipping-design.md
docs/superpowers/plans/2026-08-09-physical-claim-and-shipping.md
docs/physical-shipping-api.md
docs/progress/阶段6-最终交付通俗总结.md
docs/progress/阶段6-任务9-阶段交付完成介绍.md
```

## 4. 当前可运行基线

- Java 17、Spring Boot、MyBatis-Plus、MySQL、Redis；
- Flyway V1–V17，48 张业务表；
- 全量测试 509 项，失败 0、错误 0、跳过 0；
- 可执行 JAR：`target/luckyhub-0.0.1-SNAPSHOT.jar`，69,883,985 字节；
- OpenAPI 冒烟验证 14 个阶段 6 method + path；
- 正式空库脚本：`scripts/Verify-Phase6FreshMigration.ps1`；
- 临时 schema 和授权残留均为 0；
- 阶段 6 最终审查 Critical 0、Important 0。

一个具体恢复例子：在新电脑上启动 MySQL 和 Redis，准备空数据库环境，运行正式脚本让 Flyway 从 V1 顺序执行到 V17；再打包并启动 JAR，打开 `/v3/api-docs`，能看到地址、实物领取、用户物流、轨迹回调、管理物流和模拟物流接口。这证明交付物不是只在旧开发库中“碰巧能跑”。

## 5. 阶段 6 已交付能力

1. 地址敏感字段以 AES-256-GCM 加密，API 只返回脱敏信息；
2. 现金和积分实物下单时冻结不可变地址快照；抽奖实物在领取时冻结；
3. 三类来源各自校验后汇入唯一 `shipping_order` 和稳定 `LOGISTICS-{id}`；
4. 并发支付、兑换、领取、任务执行和重复回调保持幂等；
5. 回调使用 HMAC、时间窗、callbackId/nonce/供应方事件三层防重，并保持状态单调；
6. 领取超时与领取竞争使用同一权益行锁，库存最多回补一次；
7. 用户只能查自己的脱敏物流，管理员重试/终止遵守安全状态机；
8. 模拟物流与真实 Gateway 边界分离，完整地址只在受控内存装配边界短暂出现。

## 6. 仍然明确不在已交付范围

- 当前使用本地模拟物流，不代表已接入真实快递账号、电子面单或供应商 SLA；
- 没有退款、退货、换货、售后工单；
- 没有购物车、运费计算、多仓分配或真实支付机构扩展；
- 新需求若涉及数据库结构，只能新增迁移，不修改已发布的 V1–V17；
- 是否继续下一阶段以及下一阶段名称、目标和优先级，均等待明确决策。

## 7. 推荐读取顺序

1. 本文档；
2. `docs/LuckyHub-开发进度交接总结.md`；
3. `docs/progress/阶段6-最终交付通俗总结.md`；
4. `docs/physical-shipping-api.md`；
5. 阶段 6 设计与已完成计划；
6. 新目标经批准后再创建的新规格与计划。
