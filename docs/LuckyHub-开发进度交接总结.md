# LuckyHub 开发进度交接总结

## 当前状态

阶段 1–6 已完成，当前分支为 `feature/phase6-physical-shipping`。数据库迁移为 V1–V17，共 48 张业务表；全量回归 509 项全部通过。阶段 6 已完成最终交付，但尚未根据本交接擅自合并到 `master`。

当前没有已批准的下一阶段。继续开发前先确定新的业务目标，不要把真实快递或退款售后描述成已经承诺的阶段 7。

## 阶段 6 分支与提交基线

当前分支：`feature/phase6-physical-shipping`；阶段 6 起点：`ca213f9`。以下提交构成已实现、已测试和已交接的基线：

```text
Task 1：d945a01、8521bc3
Task 2：fcdca0c
Task 3：89c651d、98e17e9
Task 4：e8aecf0、3b2e1fa、b78621a
Task 5：20887f9、ee9ba36
Task 6：0499bd1、a7a9938、d9f7987
Task 7：9c4942a、d624219
Task 8：3a9a750、2fb8243
Task 9：d95bdab（最终验证与交接）
```

接手时以 `git log ca213f9..HEAD --oneline` 核对这条提交链；若本文之后又有交接修订，以当前分支 `HEAD` 为准，不要只依赖文档中的短哈希。

## 阶段 6 完成内容

- 地址簿敏感字段使用 AES-256-GCM；密钥和回调 secret 由环境提供，无生产默认值；
- 抽奖、现金订单和积分兑换各自校验来源后，冻结不可变地址快照；
- 每个来源只创建一张统一发货单和一个稳定的 `LOGISTICS-{shippingOrderId}` 履约任务；
- 实物中奖时只进入 `CLAIM_PENDING`，领取成功后才创建物流；领取与超时竞争安全，库存只回补一次；
- Gateway 远程调用位于数据库事务外，完整地址只在装配阶段于内存短暂解密；
- 回调使用 HMAC-SHA256、五分钟时间窗、callbackId、nonce 摘要和供应方事件 ID 防重；
- 轨迹可追加，但 `DELIVERED` 不会被迟到的低等级事件回退；
- 用户只能查自己的脱敏物流；管理员查询、重试和终止有精确权限与状态约束；
- 现金订单继续用支付状态，积分兑换继续用资产状态，物流状态兼容追加，不混淆旧语义；
- 三来源端到端、并发和全业务字符/JSON 列隐私扫描均已覆盖。

## 三类实物终态

```text
LOTTERY_BENEFIT   -> CLAIM_PENDING -> 领取 -> 统一发货 -> DELIVERED
CASH_ORDER        -> PAID          -> 统一发货 -> DELIVERED（支付状态仍为 PAID）
POINTS_REDEMPTION -> COMPLETED     -> 统一发货 -> DELIVERED（兑换状态仍为 COMPLETED）
```

## 关键实现文件

```text
src/main/resources/db/migration/V17__add_shipping_and_physical_claim.sql
src/main/java/com/dongqh/luckyhub/shipping/
src/main/java/com/dongqh/luckyhub/fulfillment/worker/FulfillmentWorker.java
src/test/java/com/dongqh/luckyhub/shipping/PhysicalShippingEndToEndTests.java
src/test/java/com/dongqh/luckyhub/shipping/PhysicalShippingConcurrencyTests.java
src/test/java/com/dongqh/luckyhub/shipping/PhysicalShippingSafetyTests.java
scripts/Verify-Phase6FreshMigration.ps1
docs/physical-shipping-api.md
```

## 最终验证证据

```text
空库迁移选测：19 项，0 失败，0 错误，0 跳过
空库迁移：V1–V17 成功，48 张业务表
关键并发：8 项，0 失败，0 错误，0 跳过
全量回归：509 项，0 失败，0 错误，0 跳过
打包：BUILD SUCCESS
JAR：69,883,985 字节
JAR SHA-256：F32A936EF59E2AD60CB4128C2600ACA5DE8B63E19532714988DA94B5F655E012
OpenAPI：14 个阶段 6 method + path 存在
冒烟端口：49679
启动 PID：12024，仅该 PID 在 finally 中停止并确认退出
临时 schema / grant 残留：0 / 0
Critical：0
Important：0
```

全量回归第一次运行时，`DrawChanceServiceTests` 的过期对账用例在共享开发库的 327 条更老 `RESERVED` 记录前提下，错误假设自己的两行一定进入全局 `LIMIT 10`，因此出现 expected 2 / actual 0。focused RED 稳定复现后，只修正测试隔离：关闭该测试上下文的自动对账，把本测试两行设为确定最早时间并使用 `limit=2`；没有删除或修改其他测试数据，也没有改变生产对账语义。随后 focused 4/4 和全量 509/509 通过。

## 新电脑 / 空数据库例子

在一台新电脑上先启动 MySQL、Redis，并配置 `.env`、`SHIPPING_ADDRESS_KEY` 与 `SHIPPING_CALLBACK_SECRET`。运行 `scripts/Verify-Phase6FreshMigration.ps1`，系统创建一个随机临时 schema，从 V1 顺序迁移到 V17，核对 48 张业务表，再撤权并删除它。然后执行 package，启动唯一生成的 JAR；访问 `/v3/api-docs` 能看到地址、领取、用户物流、tracking、callback、管理员物流和模拟物流接口。这个流程证明交付不依赖旧数据库残留或 IDE 启动方式。

## 已知边界

- 当前 Gateway 连接本地模拟物流，未来可替换，但本阶段没有真实快递账号或电子面单；
- 没有退款、退货、换货、售后、购物车、多仓和运费计算；
- V1–V17 已发布，后续结构变化只能新增迁移；
- 下一阶段名称与范围未决定，需先做业务决策和设计评审。

## 下一次会话

先读取总路线、本文和 `docs/progress/阶段6-最终交付通俗总结.md`，执行恢复检查，再确认新的目标。若需要集成当前分支，先由负责人选择 merge / PR / 保留分支；不要自行合并到 `master`。
