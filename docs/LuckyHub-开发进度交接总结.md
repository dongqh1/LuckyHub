# LuckyHub 开发进度交接总结

## 当前状态

阶段 1–5 已完成，项目可直接运行。数据库迁移为 V1–V16，共 43 张业务表；全量回归 407 项全部通过。下一阶段是实物领取、地址快照与物流履约。

## 阶段 5 完成内容

- 奖品可绑定 `reward_definition`，旧奖品仍可不绑定；
- 抽奖事务把奖励定义、类型、目标、数量、JSON 载荷和 SHA-256 指纹冻结到中奖记录与权益；
- 优惠券、积分、会员通过统一 `fulfillment_task` 和本地模拟供应方；
- 供应方成功后投影为真实用户券、积分流水/余额和会员发放记录；
- 实物只进入 `CLAIM_PENDING`，阶段 5 不创建物流任务；
- 奖励抽奖次数拥有 MySQL 账户、不可变流水和预留/确认/释放生命周期；
- 每日免费次数和奖励次数共同支持单抽/十连抽，建单失败会完整补偿；
- 消息消费交叉核对订单、中奖记录、权益和冻结快照；伪造事件进入隔离且零副作用；
- 并发重复消息、供应方重复调用和本地重复投影均保持幂等；
- 权益查询追加奖励定义、类型、数量、履约编号和履约状态，旧数据返回 `null`。

## 五类奖励终态

```text
COUPON      -> 模拟供应方 -> 用户券               -> AVAILABLE
POINTS      -> 模拟供应方 -> 积分流水与余额         -> AVAILABLE
MEMBERSHIP  -> 模拟供应方 -> 会员发放与有效期累计   -> AVAILABLE
PRODUCT     -> 无物流任务                          -> CLAIM_PENDING
DRAW_CHANCE -> 次数账户与流水                      -> AVAILABLE
```

## 关键实现文件

```text
src/main/resources/db/migration/V16__integrate_lottery_rewards.sql
src/main/java/com/dongqh/luckyhub/lottery/service/impl/LotteryRewardDispatchServiceImpl.java
src/main/java/com/dongqh/luckyhub/lottery/service/impl/LotteryRewardIdentityServiceImpl.java
src/main/java/com/dongqh/luckyhub/drawchance/service/impl/DrawChanceServiceImpl.java
src/main/java/com/dongqh/luckyhub/benefit/service/impl/LotteryRewardProjectionServiceImpl.java
src/test/java/com/dongqh/luckyhub/lottery/LotteryFiveRewardEndToEndTests.java
scripts/Verify-Phase5FreshMigration.ps1
```

## 验证证据

```text
阶段 5 集中验收：49 项，0 失败，0 错误，0 跳过
空库迁移：V1–V16 成功，43 张业务表
关键并发验收：4 项，0 失败，0 错误，0 跳过
全量回归：407 项，0 失败，0 错误，0 跳过
打包：BUILD SUCCESS
JAR：69,748,317 字节
OpenAPI：抽奖、权益、奖励定义端点存在
临时数据库残留：0
Critical：0
Important：0
```

第一次全量回归曾发现一个由故意 RED 测试遗留的无角色测试用户；已精确清理，并把五类端到端夹具修正为创建用户时同步绑定 USER 角色。随后重新执行全量回归 407/407 通过。

## 已知边界

- 四类 Gateway 当前连接本地模拟供应方，未来可替换为公司内部或第三方 API；
- 实物没有收货地址、领取期限、包裹、运单和物流轨迹；
- 优惠券和会员本地资产仍依赖当前模板/产品领域服务；
- 阶段 5 不做购物车、退款、混合支付或真实支付机构对接。

## 下一步

阶段 6 先写设计规格，再实现：地址快照、实物领取状态机、领取期限、物流任务创建、运单与轨迹、隐私脱敏、失败补偿及管理员操作。不得修改 V1–V16。
