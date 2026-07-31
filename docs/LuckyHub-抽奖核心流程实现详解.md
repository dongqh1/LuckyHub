# LuckyHub 抽奖核心流程实现详解

> 本文不是按文件夹背代码，而是跟着一次真实请求向下走。每遇到一个问题，再引出解决这个问题的类、表、Redis 数据或消息。读完后，你应该能够解释为什么要这样拆分，并能独立写出同类系统的第一版。

## 0. 先建立一张完整地图

用户提交：

```http
POST /api/lottery/draws
Authorization: Bearer <JWT>
Content-Type: application/json

{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "activityId": 10,
  "drawCount": 1
}
```

这一次请求实际上经过两条链路。

同步链路决定“这次抽奖是什么结果”，然后立即响应用户：

```text
HTTP → JWT/权限 → DTO校验 → MySQL幂等检查 → 活动与奖品快照
     → Redisson用户活动锁 → Redis Lua预占额度 → REQUIRES_NEW创建PROCESSING订单
     → 释放锁 → REQUIRED抽奖事务 → 权重候选 → MySQL条件扣库存
     → 记录/权益/Outbox/订单SUCCESS一起提交 → 返回DrawOrderView
```

异步链路完成“确认次数”和“发放权益”：

```text
Outbox定时扫描 → 领取投递租约 → DrawEventPublisher
→ Redis Stream → Consumer Group → 消费幂等
→ 确认/释放Redis额度 或 履约权益 → 成功后ACK
```

还有第三条兜底链路：

```text
超时ZSet → 对账定时任务 → 按requestId核对Redis和MySQL
→ SUCCESS确认 / FAILED释放 / 无订单释放 / PROCESSING超时转FAILED
```

为什么需要三条？同步请求必须快；消息可能暂时不可用；应用可能在任意指令后崩溃。MySQL 保存最终事实，Redis 控制高并发额度，Outbox 把数据库事实可靠地交给消息系统，对账负责修复跨系统留下的缝隙。

## 1. 第一步：HTTP 请求怎样进入 Controller

入口位于：

```text
src/main/java/com/dongqh/luckyhub/lottery/controller/LotteryController.java
```

核心方法是：

```java
@PostMapping("/draws")
@RequirePermission(PermissionCodes.LOTTERY_DRAW)
public ApiResponse<DrawOrderView> draw(@Valid @RequestBody DrawCommand command) {
    return ApiResponse.success(lotteryService.draw(command));
}
```

逐项理解：

- `@RestController`：方法返回值直接序列化成 JSON，不去找 HTML 模板。
- `@RequestMapping("/api/lottery")` 和 `@PostMapping("/draws")` 拼出完整路径。
- `@RequestBody`：把请求 JSON 反序列化成 `DrawCommand`。
- `@Valid`：在进入业务 Service 前执行 Jakarta Validation。
- `@RequirePermission`：声明此方法要求 `lottery:draw`。
- `ApiResponse<DrawOrderView>`：统一成功外壳是 `code=0,message=success,data=...`；真正业务数据是 VO。

如果没有 Controller，HTTP 世界就没有入口。如果 Controller 自己写库存 SQL、Lua 和事务，所有层会纠缠在一起，测试也很难写。因此 Controller 只负责协议转换、注解校验、权限声明和调用 Service。

### 1.1 DTO、VO、Entity、Mapper、Service 到底有什么区别

这是整个项目最值得先掌握的分层：

| 类型 | 代表什么 | 抽奖例子 | 不应该负责什么 |
|---|---|---|---|
| DTO/Command/Query | 客户端输入 | `DrawCommand`、`DrawRecordQuery` | 不直接操作数据库 |
| VO/View | 对客户端输出 | `DrawOrderView`、`BenefitView` | 不暴露内部权重、精确库存 |
| Entity | 数据库一行的 Java 映射 | `LotteryDrawOrder`、`MessageOutbox` | 不等于公开 API |
| Mapper | Java 与 SQL 的边界 | `LotteryDrawOrderMapper` | 不编排完整业务流程 |
| Service | 业务规则与用例编排 | `LotteryServiceImpl` | 不处理 HTTP 细节 |
| Model | Service 之间的内部不可变数据 | `DrawExecutionContext` | 不作为数据库实体随意修改 |

`DrawCommand` 是一个 record：

```java
public record DrawCommand(
    @NotBlank @Size(max = 64) String requestId,
    @NotNull @Positive Long activityId,
    @NotNull Integer drawCount) {

    @AssertTrue(message = "抽奖次数只能是1或10")
    public boolean isSupportedDrawCount() {
        return drawCount != null && (drawCount == 1 || drawCount == 10);
    }
}
```

为什么既有注解校验，`LotteryServiceImpl.validate` 又校验一次？HTTP 调用会走 `@Valid`，但单元测试、其他 Java Service 或以后消息入口也可能直接调用 `LotteryService`。业务层的防御性校验保证核心规则不依赖某个入口。

`requestId` 在 DTO 注解里只检查非空和长度，Service 还用 `UUID.fromString` 检查标准 UUID。这里的 requestId 是客户端业务幂等键，不是响应中的服务端请求追踪 ID。

## 2. 第二步：JWT 如何变成可信 userId

请求在到达 Controller 前先经过：

```text
config/AuthenticationFilterConfig.java
auth/filter/AuthenticationFilter.java
auth/security/JwtService.java
auth/security/SessionService.java
auth/context/LoginContext.java
```

`AuthenticationFilterConfig` 把过滤器注册到 `/api/lottery/*` 和 `/api/benefits/*`。过滤器从 `Authorization: Bearer ...` 取出 Token，`JwtService.parse` 验证 JWT，再用 `SessionService.isValid(sessionId,userId)` 确认 Redis 会话仍有效。之后创建 `LoginPrincipal`：

```java
LoginContext.set(new LoginPrincipal(
    payload.userId(), payload.username(), payload.sessionId()));
```

`LoginContext` 内部是 `ThreadLocal<LoginPrincipal>`。同一个 HTTP 请求通常在同一个工作线程执行，因此后续 Service 可以调用：

```java
long userId = LoginContext.require().userId();
```

过滤器的 `finally` 一定执行 `LoginContext.clear()`。如果忘记 clear，线程池复用线程时，下一个请求可能看到上一个用户，这是严重越权漏洞。

请求体为什么不能接收 `userId`？因为攻击者可以把它改成任何数字。JWT 经过签名和会话验证，才是服务端信任的身份来源。

## 3. 第三步：基础权限与数据范围不是一回事

路径随后经过：

```text
config/PermissionInterceptorConfig.java
rbac/interceptor/PermissionInterceptor.java
rbac/annotation/RequirePermission.java
rbac/constant/PermissionCodes.java
```

`PermissionInterceptor` 读取 Controller 方法上的 `@RequirePermission`，通过 `UserPermissionService.findPermissionCodes(userId)` 判断用户是否拥有基础权限。受保护接口漏写注解时采用默认拒绝。

“能调用接口”不代表“能读所有人的数据”。查询 Service 还会使用：

```text
rbac/service/DataScopeService.java
rbac/service/Impl/DataScopeServiceImpl.java
rbac/service/UserDataScope.java
```

例如用户有 `lottery:record:read`，可以进入记录接口；只有再有 `lottery:record:read:all`，才可以查询所有人。普通用户显式传入别人的 `userId` 会返回 403，而不是偷偷替换成本人。这样前端错误和攻击行为不会被掩盖。

V5 迁移创建 USER 角色和 9 个权限。基础 5 个权限授予 USER 与 ADMIN；4 个 `read:all` 只授予 ADMIN。新用户创建时，现有 `UserServiceImpl` 会在同一事务中自动绑定 USER 角色。

## 4. 第四步：第一次 MySQL 幂等检查

真正业务入口是：

```text
lottery/service/LotteryService.java
lottery/service/impl/LotteryServiceImpl.java
```

`draw` 首先执行：

```java
LotteryDrawOrder existing = orderMapper.selectByRequestId(command.requestId());
if (existing != null) {
    return resolveExisting(existing, command, userId, false);
}
```

为什么 Redis 已有预占 Hash，还要以 MySQL 为幂等事实？Redis 数据会过期、被清理或短暂不可用；数据库订单是需要长期审计的事实。表 `lottery_draw_order` 有唯一索引：

```sql
UNIQUE KEY uk_lottery_draw_order_request_id (request_id)
```

幂等身份由四部分构成：

```text
requestId + JWT userId + activityId + drawCount
```

`dailyLimit` 是管理员策略快照，不属于身份。否则管理员把每日上限从 3 改成 5 后，用户的同一个网络重试会莫名冲突。

已有订单时：

- 身份不同：`IDEMPOTENCY_CONFLICT(43006)`；
- `PROCESSING`：`DRAW_ORDER_PROCESSING(43007)`；
- `FAILED`：`DRAW_ORDER_FAILED(43008)`，原 ID 永久不能重新执行；
- `SUCCESS`：查询 `lottery_draw_record`，返回数据库中的原结果。

为什么失败订单不删除？删除后无法审计，更可能让同一个请求再次扣额度或库存。新的业务尝试必须用新 UUID。

## 5. 第五步：一次性加载活动与奖品快照

没有已有订单时，调用：

```text
lottery/service/DrawEligibilityService.java
lottery/service/impl/DrawEligibilityServiceImpl.java
lottery/model/DrawPrizeSnapshot.java
```

`load(activityId)` 做四件事：

1. 查询 `marketing_activity`；不存在返回 `43001`。
2. 以配置的 `Asia/Shanghai` 时区取得当前时间，要求状态为 `RUNNING`，并满足 `startTime <= now < endTime`。
3. 按 `sort_order,id` 查询活动奖品关系，再批量读取奖品资料。
4. 组装不可变的 `EligibilitySnapshot`，含 `dailyLimit`、`noWinWeight`、奖品快照和快照时间。

`DrawPrizeSnapshot` 同时保留：活动奖品关系 ID、奖品 ID、名称、类型、图片、权重、快照库存、启用状态。十连抽的 10 次选择都使用同一份内存快照。

为什么不每抽一次再查询？那样一次十连抽中途管理员修改权重，前五次和后五次会属于不同规则；还会增加数据库压力。快照使一个订单内部一致，管理员修改只影响之后的新订单。

快照时间截断到毫秒，因为 MySQL `DATETIME(3)` 只能保存毫秒。否则第一次响应带纳秒，同请求重试从数据库读回毫秒，两个“幂等响应”会不完全相同。

## 6. 第六步：为什么需要 Redisson 锁

接下来调用：

```text
lottery/lock/DrawLockService.java
lottery/lock/RedissonDrawLockService.java
lottery/quota/DrawQuotaKeys.java
config/RedissonConfig.java
```

锁 Key 是：

```text
lock:draw:{activityId}:{userId}
```

代码通过 `tryLock(lockWait)` 最多等待配置时间。取得后还检查 `isHeldByCurrentThread()`，最后只有当前线程持有时才 `unlock()`。被中断时恢复线程的 interrupt 标志，并返回 `53002`。

锁内执行三件事：

1. 第二次查询 MySQL 幂等订单；
2. Lua 预占额度；
3. 独立事务创建 `PROCESSING` 订单。

为什么锁前查一次、锁内再查一次？锁前查询让正常重试快速返回；两个请求也可能同时在锁前看到“无订单”，因此取得锁后必须再查一次。这叫 double check。

为什么锁只覆盖到 PROCESSING 订单创建，不覆盖权重抽奖和整个事务？锁的任务是封住“检查幂等 → 预占 → 建立数据库事实”的短临界区。长时间持锁会降低吞吐量。系统正确性不能只靠锁，还要靠 Lua 原子性、MySQL 唯一索引、条件更新、Outbox 和对账。

## 7. 第七步：Lua 怎样原子预占每日额度

相关文件：

```text
lottery/quota/DrawQuotaService.java
lottery/quota/RedisDrawQuotaService.java
lottery/quota/QuotaReservationRequest.java
lottery/quota/QuotaReservationResult.java
lottery/quota/ReservationStatus.java
resources/redis/lottery/reserve_draw_quota.lua
resources/redis/lottery/confirm_draw_quota.lua
resources/redis/lottery/release_draw_quota.lua
```

### 7.1 Redis 中保存什么

假设活动 10、用户 8、日期 2026-07-31、requestId 为 `550e8400-e29b-41d4-a716-446655440000`：

```text
draw:quota:10:8:20260731              String，当日已占用次数
draw:reservation:550e8400-e29b-41d4-a716-446655440000
                                       Hash，这次请求的预占身份和状态
draw:reservation:timeouts             ZSet，member=requestId，score=超时毫秒时间戳
lock:draw:10:8                         Redisson锁
luckyhub:stream:lottery                Redis Stream
```

Hash 字段是：`requestId/activityId/userId/drawCount/drawDate/status/createdAt`。状态为 `RESERVED → CONFIRMED` 或 `RESERVED → RELEASED`。

ZSet 为什么比扫描 Key 好？按 score 查询 `0..now` 能直接取到到期成员，复杂度和批次可控；使用 `KEYS draw:reservation:*` 会扫描整个 Redis，生产环境可能阻塞。

### 7.2 reserve_draw_quota.lua 逐段解释

脚本接收 3 个 `KEYS`：quota、reservation、timeout ZSet；接收 9 个 `ARGV`：身份、次数、上限、日期、创建/超时/过期时间。

第一段检查 Hash 是否已存在：

```lua
if redis.call('EXISTS', reservationKey) == 1 then
    local existing = redis.call('HMGET', reservationKey,
        'activityId', 'userId', 'drawCount', 'drawDate', 'status')
```

如果活动、用户或抽数不同，返回状态码 3，Java 映射成 `43006`；相同则返回 0 和原来的日期、次数、状态。这里故意不比较 dailyLimit。

第二段判断整笔额度：

```lua
local used = tonumber(redis.call('GET', quotaKey) or '0')
if used + drawCount > dailyLimit then
    return {2, 'RESERVED', drawDate, tostring(drawCount)}
end
```

十连抽不会循环扣 10 次，而是一次判断 `used+10`。不足就完全不写 Redis，所以具有整笔原子性。

第三段一次性写入：

```lua
redis.call('INCRBY', quotaKey, drawCount)
redis.call('PEXPIREAT', quotaKey, expiresAt)
redis.call('HSET', reservationKey, ... 'status', 'RESERVED', ...)
redis.call('PEXPIREAT', reservationKey, expiresAt)
redis.call('ZADD', timeoutKey, timeoutAt, requestId)
```

Redis 在一个线程中完整执行 Lua，其他请求看不到脚本执行到一半的状态。这解决了 Java 先 GET、再判断、再 INCR 时的并发穿透。

过期时间按原 `drawDate` 的上海零点加 `reservationRetention=72h` 计算。跨零点确认或释放仍然找到原日期的 quota，而不是错误操作新一天。

返回码：0=重复预占，1=新建，2=额度不足，3=身份冲突。单抽不足映射 `43004`，十连不足映射 `43005`。

### 7.3 确认与释放脚本

`confirm_draw_quota.lua` 将 `RESERVED` 改为 `CONFIRMED`，并从 ZSet 移除 requestId。重复确认、已经释放或 Hash 不存在返回 0，属于幂等无操作。

`release_draw_quota.lua` 会再次核对 requestId、activityId、userId、drawDate 和 drawCount。只有 `RESERVED` 才执行：

```lua
local remaining = used - drawCount
if remaining < 0 then remaining = 0 end
redis.call('SET', quotaKey, remaining, 'KEEPTTL')
redis.call('HSET', reservationKey, 'status', 'RELEASED')
redis.call('ZREM', timeoutKey, requestId)
```

`KEEPTTL` 保留原过期时间；下限 0 防御损坏数据。Lua 返回 `-1` 表示身份/元数据损坏，Java 只接受 0 或 1，否则统一返回额度服务不可用，不能猜测性地修改额度。

## 8. 第八步：为什么先单独提交 PROCESSING 订单

预占成功后调用：

```text
lottery/service/DrawOrderLifecycleService.java
lottery/service/impl/DrawOrderLifecycleServiceImpl.java
lottery/model/NewDrawOrder.java
lottery/entity/LotteryDrawOrder.java
lottery/mapper/LotteryDrawOrderMapper.java
```

方法：

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public LotteryDrawOrder createProcessing(NewDrawOrder command)
```

`REQUIRES_NEW` 表示无论外层是否已有事务，都开启并独立提交一个新事务。这样后面的十连抽事务失败回滚时，`PROCESSING` 订单仍存在，随后可以转成 `FAILED`，让用户查询和运维审计。

插入 SQL 使用：

```sql
INSERT ... VALUES (..., 'PROCESSING')
ON DUPLICATE KEY UPDATE id = id
```

并发请求触碰 requestId 唯一索引时，不创建第二行。之后 `SELECT ... FOR UPDATE` 是 InnoDB 当前读，能看见另一个事务刚提交的行；普通一致性读在 REPEATABLE READ 下可能还停留在旧快照。

### 8.1 一个很重要的 Spring 事务陷阱：self-invocation

`@Transactional` 通常由 Spring 代理拦截“从另一个 Bean 调用这个 Bean 的 public 方法”。如果在同一个类里写 `this.createProcessing()`，调用不会经过代理，`REQUIRES_NEW` 可能不生效。因此代码把生命周期和抽奖事务拆成独立 Bean：`LotteryServiceImpl → DrawOrderLifecycleServiceImpl`、`LotteryServiceImpl → DrawTransactionServiceImpl`。

这不是为了文件多，而是为了让事务传播边界真实生效且清晰可测。

## 9. 第九步：释放锁后进入一个完整抽奖事务

相关文件：

```text
lottery/service/DrawTransactionService.java
lottery/service/impl/DrawTransactionServiceImpl.java
lottery/model/DrawExecutionContext.java
lottery/model/DrawExecutionResult.java
lottery/model/DrawResultItem.java
```

方法使用：

```java
@Transactional(propagation = Propagation.REQUIRED)
public DrawExecutionResult execute(DrawExecutionContext context)
```

`REQUIRED` 表示加入现有事务，没有则创建一个。Controller 编排没有开启事务，因此这里创建业务事务。一次十连抽的 10 次选择、10 条记录、所有库存扣减、权益、订单 SUCCESS 和 Outbox 都在同一个事务中。

任何一步抛出运行时异常，整笔回滚。用户不会看到只完成 7 次的十连抽。

## 10. 第十步：权重算法只产生“候选”

文件：

```text
lottery/algorithm/WeightedDrawEngine.java
lottery/algorithm/WeightedDrawEngineImpl.java
lottery/algorithm/DrawRandomSource.java
lottery/algorithm/SecureDrawRandomSource.java
lottery/algorithm/PrizeWeightSnapshot.java
lottery/algorithm/DrawCandidate.java
```

生产随机源用 `SecureRandom.nextLong(bound)`，返回 `[0,bound)`。算法刻意不访问数据库，也不扣库存，所以可以用固定随机源做确定性测试。

例子：

```text
一等奖 weight=10  → [0,10)
二等奖 weight=30  → [10,40)
noWinWeight=60    → [40,100)
```

- 随机 0 或 9：一等奖候选；
- 随机 10 或 39：二等奖候选；
- 随机 40～99：明确未中奖。

所有累加使用 `long` 和 `Math.addExact`，避免多个大权重在 `int` 中溢出变成负数。总权重必须大于 0。

### 10.1 售罄或禁用为什么不重新分配概率

假设一等奖 `[0,10)` 已售罄。随机值 5 仍然落在这个区间，但算法返回 `NO_WIN`；二等奖仍只有 `[10,40)`，概率不会从 30% 上升到 33.3% 或更多。

这实现了已确认的规则：“不可用奖品原权重转成未中奖”。如果直接从候选列表删除售罄奖品，总权重缩小，其他奖品中奖率会悄悄改变。

`noWinWeight=0` 只是没有独立未中奖区间，并不保证一定中奖，因为禁用、售罄或并发扣库存失败仍会得到 `NO_WIN`。

## 11. 第十一步：候选中奖必须通过 MySQL 条件扣库存

文件：

```text
inventory/mapper/ActivityPrizeInventoryMapper.java
inventory/service/ActivityPrizeInventoryService.java
inventory/service/ActivityPrizeInventoryServiceImpl.java
```

唯一的库存 SQL 是：

```sql
UPDATE marketing_activity_prize
SET remaining_stock = remaining_stock - 1
WHERE id = #{activityPrizeId}
  AND remaining_stock > 0
```

影响 1 行才是最终 WIN；影响 0 行就是 NO_WIN。

为什么不能“先 SELECT 库存，再 UPDATE”？两个线程都可能读到 1，然后各减一次，产生超卖。条件 UPDATE 让 MySQL 在行锁和条件判断下完成原子扣减。

用户之前问过：“抽中前被别人抽走，没有库存，是不是变成未中奖？”答案正是：是。快照看到可用，所以权重产生奖品候选；真正扣减时库存已被另一个事务拿走，影响 0 行，于是本次记录为 NO_WIN，而且不重新抽。若重抽，失败请求会得到额外机会，整体概率与压力都会失真。

## 12. 第十二步：保存记录、快照和 PENDING 权益

`DrawTransactionServiceImpl.persistResult` 创建 `LotteryDrawRecord`：

- 通用字段：orderId、requestId、sequenceNo、userId、activityId、drawTime；
- WIN：写 prizeId、prizeName、prizeType、prizeImageUrl；
- NO_WIN：所有奖品字段保持 null；
- WIN 再创建一条 `UserBenefit`，初始状态 `PENDING`、数量 1。

相关 Entity/Mapper：

```text
lottery/entity/LotteryDrawRecord.java
lottery/mapper/LotteryDrawRecordMapper.java
benefit/entity/UserBenefit.java
benefit/mapper/UserBenefitMapper.java
```

为什么记录里重复保存奖品名称、类型和图片 URL？这是历史快照。管理员以后把奖品改名、换图或禁用，用户过去的中奖记录仍应显示当时看到的内容。

`uk_draw_record_request_sequence(request_id,sequence_no)` 防止同一个请求重复插入同序号；`uk_user_benefit_draw_record(draw_record_id)` 保证一条中奖记录最多一个权益。

## 13. 第十三步：条件更新订单，再把事件写进同一事务

10 次结果全部保存后执行：

```sql
UPDATE lottery_draw_order
SET status='SUCCESS', fail_reason=NULL, completed_at=?
WHERE id=? AND status='PROCESSING'
```

如果影响行数不是 1，说明对账或其他流程已经改变状态，当前事务抛错并整体回滚。`WHERE status='PROCESSING'` 是状态竞争的栅栏，避免 SUCCESS 覆盖 FAILED。

随后写事件：

- 每个成功订单一个 `DRAW_CONFIRMED`；
- 每个 WIN 权益一个 `PRIZE_FULFILLMENT_REQUESTED`。

相关文件：

```text
lottery/messaging/event/DrawEventType.java
lottery/messaging/event/DrawEventEnvelope.java
lottery/messaging/event/DrawConfirmedEvent.java
lottery/messaging/event/DrawReleaseRequestedEvent.java
lottery/messaging/event/PrizeFulfillmentRequestedEvent.java
lottery/service/OutboxService.java
lottery/service/OutboxServiceImpl.java
lottery/entity/MessageOutbox.java
lottery/mapper/MessageOutboxMapper.java
lottery/enums/OutboxStatus.java
```

通用 envelope 含 `eventId,eventType,eventVersion,requestId,userId,activityId,orderId,occurredAt,payload`。`eventVersion=1`，eventId 是 UUID。

`OutboxServiceImpl.append` 使用 `Propagation.MANDATORY`，含义是“调用它时必须已经在数据库事务中”。它不允许单独提交，否则会出现业务成功但没有事件，或事件存在但业务回滚。

### 13.1 为什么不能事务提交后直接发 Stream

若先提交 MySQL 再发 Redis，应用可能刚提交就崩溃，消息永远丢失；若先发消息再提交，消费者可能看到一个最终回滚的中奖。Outbox 的做法是把事件当普通数据库行，与业务数据一起提交。后台之后可以无限重试投递。

这解决的是本地事务与消息中间件之间没有分布式事务的问题。

## 14. 第十四步：同步响应何时返回

业务事务提交后，`LotteryServiceImpl.fromExecution` 组装：

```text
lottery/vo/DrawResultView.java
lottery/vo/DrawOrderView.java
```

响应已经是最终 WIN/NO_WIN、完整 1 或 10 条结果，并包含 PENDING 权益 ID。它不等待 Stream 消费和权益履约，所以消息系统暂时不可用不会让已经成功提交的抽奖变成失败。

对于 COUPON 等权益，刚响应时状态可能仍是 PENDING；后台消费后查询会变成 AVAILABLE。

## 15. 同步事务失败后怎样补偿

如果 PROCESSING 订单已创建，但业务事务失败，`LotteryServiceImpl.compensateIfNeeded` 调用：

```java
lifecycleService.markFailedAndRequestRelease(
    order, "DRAW_TRANSACTION_FAILED", occurredAt);
```

该方法是 `REQUIRES_NEW`，在一个新事务中条件执行 `PROCESSING → FAILED`，并写 `DRAW_RELEASE_REQUESTED` Outbox。库存、记录、权益和成功事件已经由失败的业务事务回滚；独立订单则保留下来。

若补偿事务本身也失败，代码记录日志，但不伪造“已释放”。Redis timeout ZSet 会留着，后面的对账任务是最终兜底。

## 16. 第十五步：Outbox Relay 如何安全投递

文件：

```text
lottery/service/OutboxRelayService.java
lottery/scheduler/OutboxRelayScheduler.java
lottery/mapper/MessageOutboxMapper.java
resources/db/migration/V6__add_outbox_delivery_error.sql
resources/db/migration/V7__lease_outbox_delivery.sql
```

`OutboxRelayScheduler` 默认初始延迟 60 秒，每 5 秒固定延迟调用 `relayBatch()`。

Relay 先查询到期的 `PENDING`、可重试 `FAILED`、租约过期 `PROCESSING`。对每一行生成唯一 `claimToken`，条件更新为：

```text
status=PROCESSING
claim_token=<本次UUID>
next_retry_at=now+outboxLease
```

为什么要租约，而不是 SELECT 后直接发布？多实例会同时扫到同一行。条件 claim 只有一个实例影响 1 行。发布期间不持有数据库长事务和行锁；若实例发布前崩溃，租约到期后其他实例可以重新领取。

发布成功必须用同一个 token 条件标记 SENT。旧实例即使晚回来，也不能覆盖新实例状态。失败则状态改 FAILED、retryCount 加 1、保存最多 500 字符的安全错误，并按指数退避设置下一次时间，最长 300 秒。

消息仍可能重复：例如 Redis XADD 成功后，应用在 markSent 前崩溃。租约恢复会再次 XADD。因此整套系统提供的是 **at-least-once（至少一次）**，不是恰好一次；消费者必须幂等。

## 17. 第十六步：业务为什么不直接依赖 Redis Stream

业务端口：

```text
lottery/messaging/port/DrawEventPublisher.java
```

当前适配器：

```text
lottery/messaging/redis/RedisStreamDrawEventPublisher.java
```

`OutboxRelayService` 只调用 `publisher.publish(event)`，不知道 Stream Key、Consumer Group 或 RecordId。Redis 适配器才把 envelope 写入 `luckyhub:stream:lottery`，字段中既保存可检索的 eventId/type 等，也保存完整 `envelope` JSON。

### 17.1 将来换 Kafka 要改什么

具体步骤是：

1. 新建例如 `KafkaDrawEventPublisher implements DrawEventPublisher`；
2. 用配置条件让 `provider=kafka` 时装配 Kafka 实现；
3. Kafka Consumer 反序列化同一个 `DrawEventEnvelope`，调用现有 `MessageConsumeService.consume`；
4. 业务事务、Outbox 表、事件类型、抽奖 Service、额度 Lua和权益 Service 保持不变；
5. Kafka offset 只在 `consume` 成功后提交，消费幂等仍使用 `(eventId,logicalConsumerName)`。

如果未来多个独立业务订阅同一事件，每个业务应使用稳定且不同的 logical consumer name；Kafka group 与逻辑消费者命名也需明确映射。要演进 event payload 时增加 `eventVersion` 并兼容旧版本，不要直接改变旧消息含义。

当前项目没有实现 Kafka 类，也没有真实 Kafka 配置；这里只说明已经存在的替换边界。

## 18. 第十七步：Stream Group、Pending 和 ACK

文件：

```text
lottery/messaging/redis/RedisStreamInitializer.java
lottery/messaging/redis/RedisStreamDrawEventConsumer.java
lottery/service/MessageConsumeService.java
lottery/entity/MessageConsumeRecord.java
lottery/mapper/MessageConsumeRecordMapper.java
```

应用 ready 时，Initializer 先写一个短暂 marker，创建从 `0-0` 开始的 group，再删除 marker。`BUSYGROUP` 表示组已存在，按成功处理。从 `0-0` 开始避免组创建前已有的真实消息被跳过。

Consumer 实例名由逻辑名、主机、进程和随机后缀组成，方便多实例领取消息；数据库消费幂等使用稳定的 `logicalConsumerName=lottery-core`，不会因重启变化。

每次 poll 先 claim 空闲超过 `claimIdle` 的 pending 消息，再读新消息。这样实例崩溃留下的 pending 能被另一个实例恢复，同时有毒 pending 也不会永久饿死新消息。

处理顺序是：

```text
反序列化 envelope → MessageConsumeService.consume(event)
→ 业务和幂等记录成功 → XACK
```

任何异常都不 ACK，记录保持 pending，等待重试。这就是“成功后确认”。

`message_consume_record` 的唯一索引 `(event_id,consumer_name)` 防止重复消息重复产生作用。

### 18.1 为什么额度事件先操作 Redis，再写 MySQL 消费记录

`DRAW_CONFIRMED` 调 `quotaService.confirm`；`DRAW_RELEASE_REQUESTED` 调 `quotaService.release`。Lua 状态机本身幂等。随后才在短 MySQL 事务中插消费记录。

若数据库写消费记录失败，Stream 不 ACK；重投时 Lua 看到终态返回幂等 0，再尝试写记录，不会重复扣或释放。

反过来如果先写消费记录再调 Redis，数据库提交后 Redis 失败，重投会因为“已消费”直接跳过，额度永远不修复。

## 19. 第十八步：权益异步履约

文件：

```text
benefit/service/BenefitFulfillmentService.java
benefit/service/BenefitFulfillmentServiceImpl.java
benefit/handler/BenefitFulfillmentHandler.java
benefit/handler/BenefitFulfillmentRouter.java
benefit/handler/CouponFulfillmentHandler.java
benefit/handler/PointsFulfillmentHandler.java
benefit/handler/MembershipFulfillmentHandler.java
benefit/handler/PhysicalFulfillmentHandler.java
benefit/enums/BenefitStatus.java
benefit/enums/BenefitErrorCode.java
```

收到 `PRIZE_FULFILLMENT_REQUESTED` 后，`MessageConsumeService` 取 payload 的 benefitId，调用 `fulfill(benefitId,eventId)`。

事务内先 `SELECT ... FOR UPDATE` 锁权益行，再检查状态只能是 PENDING 或 GRANT_FAILED。Router 要求四种 `PrizeType` 每种恰好一个 Handler：

| 类型 | 第一版目标状态 | 当前真实行为 |
|---|---|---|
| COUPON | AVAILABLE | 只返回状态，没有调用第三方券系统 |
| POINTS | AVAILABLE | 只返回状态，没有积分账户入账 |
| MEMBERSHIP | AVAILABLE | 只返回状态，没有真实延长会员 |
| PHYSICAL | CLAIM_PENDING | 等待以后实现地址领取/物流 |

权益状态条件更新与消费记录插入在同一 MySQL 事务中。重复 eventId 会直接视为已完成；并发重复消息由 `FOR UPDATE` 串行化。

Handler 或数据库失败时，原成功事务回滚，再用 `REQUIRES_NEW` 把权益安全地标成 `GRANT_FAILED`，`grant_error` 固定为“权益发放失败”，不泄漏内部异常。因为没有消费记录且消息不 ACK，之后能用同一个事件重试。

权益失败不会修改已经成功的抽奖订单和中奖记录。这是“中奖事实”和“奖品履约进度”分离。

## 20. 第十九步：对账任务修复跨系统缝隙

文件：

```text
lottery/scheduler/LotteryReconciliationScheduler.java
lottery/service/LotteryReconciliationService.java
lottery/service/LotteryReconciliationServiceImpl.java
lottery/model/ReconciliationResult.java
```

默认初始延迟 60 秒，每 30 秒查询：

```text
ZRANGEBYSCORE draw:reservation:timeouts 0 <nowMillis> LIMIT 0 <batchSize>
```

每项先完整读取 reservation 身份，再取得与正常抽奖相同的用户活动锁，锁内重新读取并核对身份。这个锁封住了“Lua 已预占、PROCESSING 订单尚未提交”的短窗口，避免对账错误地认为“无订单”并释放。

状态矩阵：

| Redis | MySQL | 动作 |
|---|---|---|
| RESERVED | SUCCESS | Lua confirm |
| RESERVED | FAILED | Lua release |
| RESERVED | 无订单 | Lua release |
| RESERVED | PROCESSING 且未超时 | 把 ZSet score 修正到真实截止时间 |
| RESERVED | PROCESSING 且超时 | 条件转 FAILED，reason=`PROCESSING_TIMEOUT`，同事务写释放 Outbox |
| CONFIRMED/RELEASED | 任意 | 清理残留 timeout member，不逆转终态 |

超时转 FAILED 的当前轮不直接释放，而是让同事务写出的 `DRAW_RELEASE_REQUESTED` 走标准消息链；ZSet member 暂时保留作为再次对账的兜底。

身份会比较 requestId、userId、activityId、drawCount、drawDate。损坏的 RESERVED Hash 不猜测修复，而是延期重试并记录警告；损坏的终态只移除 timeout member，不能改 quota。

每次只处理 `reconcileBatchSize` 条，单项异常计入 failed 并延期，不让坏数据占满每一批、饿死其他请求。

## 21. 单抽与十连抽完整例子

### 21.1 正常 WIN

活动每日 3 次，一等奖是 COUPON、权重 10，未中奖 90。用户第一次单抽：

1. quota 从不存在变 1，Hash=RESERVED；
2. 创建 PROCESSING 订单；
3. 随机值 5，候选一等奖；
4. 条件库存 UPDATE 影响 1 行；
5. 写 WIN 记录、PENDING 权益、订单 SUCCESS、确认与履约 Outbox；
6. 同步返回 WIN；
7. 异步确认 Hash=CONFIRMED；由于例子明确是 COUPON，权益变 AVAILABLE。若奖品是 PHYSICAL，目标状态应为 CLAIM_PENDING。

最终 quota 仍是 1，因为中奖和未中奖都消耗次数。

### 21.2 明确未中奖区间

随机值 65 落入独立 no-win `[10,100)`：不执行库存 UPDATE；保存 NO_WIN；仍写 DRAW_CONFIRMED，不写权益履约事件；额度最终 CONFIRMED。

### 21.3 售罄区间

快照中某奖品 remainingStock=0。随机命中其原区间，算法直接 NO_WIN，不把权重给其他奖品，也不执行库存扣减。

### 21.4 候选后库存竞争失败

快照 remainingStock=1，用户 A、B 都得到候选。A 的 UPDATE 先成功；B 影响 0 行。A=WIN，B=NO_WIN；B 不重抽。数据库库存停在 0，不会变 -1。

### 21.5 十连抽原子性

Lua 一次预占 10。数据库一个事务循环 sequence 1～10。第 7 次后发生系统异常，前 6 次的记录、库存扣减和权益全部回滚，订单独立转 FAILED，释放消息把 quota 减回 0。不会返回 6 条或 7 条结果。

## 22. 幂等与故障例子

### 22.1 相同请求重试

客户端超时后用相同 JWT、requestId、activityId、drawCount 重发。若订单已 SUCCESS，第一次 MySQL 查询直接返回原记录；不会再进入资格检查、锁、Lua、随机、库存或权益。

### 22.2 相同 ID 改参数

把 drawCount 从 1 改 10，MySQL 身份校验返回 43006。其他用户撞到同 requestId，同样不能取得原订单或重新执行。

### 22.3 Redis 不可用

新抽奖在预占或锁阶段失败，返回 53001/53002，不绕过每日额度。已有 SUCCESS 的幂等重试在第一次 MySQL 检查即可返回，不需要重新预占。

### 22.4 Stream 暂时不可用

抽奖事务和 Outbox 已提交，所以用户仍得到 SUCCESS。Relay 把 Outbox 标 FAILED，记录 lastError 和 nextRetryAt；Redis 恢复后重投。不能把消息故障误判为用户没中奖。

### 22.5 应用在 XADD 后崩溃

Outbox 仍是 PROCESSING。租约到期后别的实例重发，可能产生重复 Stream 消息；消费记录和 Lua 状态机吸收重复。这就是至少一次投递的正常情况。

### 22.6 应用在预占后、建订单前崩溃

Hash=RESERVED、ZSet 到期、MySQL 无订单。对账取得相同锁后确认仍无订单，Lua release 归还 1 或 10 次。

### 22.7 PROCESSING 超时

订单一直 PROCESSING，超过 2 分钟。对账用条件更新改 FAILED，failReason=`PROCESSING_TIMEOUT`，写释放事件；消息链最终释放额度。若正常事务同时完成，只有一个 `WHERE status='PROCESSING'` 更新能成功。

## 23. 查询接口如何避免越权和 N+1

文件：

```text
lottery/controller/LotteryController.java
lottery/dto/DrawOrderQuery.java
lottery/dto/DrawRecordQuery.java
lottery/service/LotteryQueryService.java
lottery/service/impl/LotteryQueryServiceImpl.java
lottery/vo/LotteryActivityView.java
lottery/vo/DrawOrderView.java
lottery/vo/DrawRecordView.java
benefit/controller/BenefitController.java
benefit/dto/BenefitQuery.java
benefit/service/BenefitQueryService.java
benefit/service/BenefitQueryServiceImpl.java
benefit/vo/BenefitView.java
```

七个端点和参数见 API 文档。这里解释实现重点：

- 活动公开 VO 不含 noWinWeight、奖品权重和库存；
- 按 requestId 查询先找到订单，再用 `lottery:draw:read:all` 解析归属；
- 订单列表 Controller 直接要求 `lottery:order:read:all`；
- 记录和权益先由 `DataScopeService` 生成本人/指定用户/所有用户范围，再构造 SQL；
- 页大小最大 100，页码最大 1,000,000，还用 `Math.multiplyExact` 防御偏移乘法溢出；
- startDate 使用 `>= 当天00:00`，endDate 使用 `< 次日00:00`，所以用户理解为包含结束日；
- endDate 最大 9999-12-30，保证 `plusDays(1)` 可表示；
- 页面关联数据批量查询，避免每条订单/权益再发一条 SQL 的 N+1 问题；
- 排序总在时间后再加 id DESC，解决相同毫秒记录的稳定顺序。

## 24. 数据库表为什么这样设计

V1 创建基础业务表，V5 增加抽奖核心，V6/V7增强 Outbox：

```text
resources/db/migration/V1__create_luckyhub_schema.sql
resources/db/migration/V5__add_lottery_core.sql
resources/db/migration/V6__add_outbox_delivery_error.sql
resources/db/migration/V7__lease_outbox_delivery.sql
```

| 表 | 作用 | 最重要约束/索引 |
|---|---|---|
| `marketing_activity` | 活动、时间、每日上限、未中奖权重 | 状态/时间索引，dailyLimit 正数 |
| `marketing_activity_prize` | 活动奖品关系、权重、MySQL库存 | `(activity_id,prize_id)` 唯一，remaining≤total |
| `lottery_draw_order` | 一次单抽/十连的幂等与状态 | requestId 唯一，drawCount 只许1/10 |
| `lottery_draw_record` | 每次结果与奖品历史快照 | `(request_id,sequence_no)` 唯一 |
| `user_benefit` | WIN 对应的履约状态 | drawRecordId 唯一 |
| `message_outbox` | 待可靠投递的通用事件 | eventId 唯一，状态/重试索引，租约 token |
| `message_consume_record` | 消费端幂等 | `(event_id,consumer_name)` 唯一 |

V5 迁移前还有一个 guard：若旧 `user_benefit` 已有历史行，迁移会确定性失败，因为新列 drawRecordId/prizeType 无法无损猜测。应先做人工数据迁移方案，不能强填假数据。

## 25. 配置清单

真实默认值位于 `src/main/resources/application.yaml`，类型约束位于 `LotteryProperties` 和 `MessagingProperties`。

### 25.1 环境配置（使用占位符）

先区分两类配置：HOST、PORT、DATABASE、USER 在 `application.yaml` 或 compose 中有本地默认值；密码和 JWT 密钥没有可安全使用的真实默认值，必须修改。`MYSQL_ROOT_PASSWORD` 主要供 Docker Compose 初始化 MySQL 使用，应用数据源本身使用 `MYSQL_USER/MYSQL_PASSWORD`。

```properties
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=luckyhub
MYSQL_USER=<数据库用户>
MYSQL_PASSWORD=<数据库密码>
MYSQL_ROOT_PASSWORD=<仅用于Docker Compose初始化的root密码>
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=<Redis密码>
JWT_SECRET=<Base64编码且足够长的随机密钥>
JWT_EXPIRATION=720000
```

不要把 `.env`、AccessKey CSV 或真实密码提交到 Git。

### 25.2 抽奖配置

| 配置 | 默认值 | 含义 |
|---|---:|---|
| `luckyhub.lottery.zone-id` | Asia/Shanghai | 每日额度自然日和业务时间 |
| `lock-wait` | 3s | Redisson 获取锁最多等待时间 |
| `processing-timeout` | 2m | PROCESSING/预占超时阈值 |
| `reconcile-interval` | 30s | 对账固定延迟 |
| `reconcile-initial-delay` | 60s | 对账首次启动延迟 |
| `reconcile-batch-size` | 100 | 每批到期 reservation 上限 |
| `reconciliation-enabled` | true | 是否装配对账调度器 |
| `reservation-retention` | 72h | quota 和 reservation 保留到原日期零点后的时长 |
| `outbox-interval` | 5s | Relay 固定延迟 |
| `outbox-initial-delay` | 60s | Relay 首次启动延迟 |
| `outbox-batch-size` | 100 | Relay 每批上限 |

### 25.3 消息配置

| 配置 | 默认值 | 含义 |
|---|---:|---|
| `luckyhub.messaging.enabled` | true | 是否启用 Relay/Stream 组件 |
| `provider` | redis-stream | 当前适配器选择 |
| `lottery-stream` | luckyhub:stream:lottery | Stream Key |
| `lottery-group` | luckyhub-lottery-consumers | Consumer Group |
| `logical-consumer-name` | lottery-core | MySQL 幂等消费者名 |
| `consumer-batch-size` | 20 | 每次读/claim 上限 |
| `consumer-poll-interval` | 1s | 消费轮询间隔 |
| `consumer-initial-delay` | 60s | 首次消费延迟 |
| `claim-idle` | 30s | pending 多久可被其他实例接管，必须大于 poll interval |
| `outbox-lease` | 30s | Relay 投递租约 |

### 25.4 OSS 与抽奖的关系

OSS 不是抽奖正确性的依赖；它负责奖品图片。奖品表保存公开 URL，抽奖时复制到记录快照。配置包括 `OSS_ENABLED/REGION/ENDPOINT/BUCKET/ACCESS_KEY_ID/ACCESS_KEY_SECRET/PUBLIC_BASE_URL`。本地只测试抽奖逻辑时可关闭 OSS；已有奖品 URL 仍可作为字符串快照保存。

## 26. 启动前检查和可复制验证

### 26.1 启动

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# 打开 .env，至少修改 MySQL/Redis 密码和 JWT_SECRET；需要图片上传时再填写 OSS。
docker compose up -d
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 spring-boot:run
```

`Copy-Item` 只用于第一次初始化；上面的 `Test-Path` 守卫会保护已有 `.env`，不要覆盖其中的真实本地配置。不要直接使用 `.env.example` 中的 `change-me` 或占位密钥。第一次启动 MySQL 后再改 `.env` 密码不会自动修改容器数据卷里的既有数据库账号；如果已经初始化过，应按数据库运维方式修改密码，并让 `.env` 保持一致。

日志应显示 Flyway 已到 V7，且 Redis/MySQL 连接成功。Swagger：`http://localhost:8080/swagger-ui.html`。

Postman 调用和全部响应示例见 `docs/lottery-api.md`。

### 26.2 MySQL 检查

把示例 requestId 替换成自己的：

```sql
SELECT * FROM lottery_draw_order
WHERE request_id='550e8400-e29b-41d4-a716-446655440000';

SELECT * FROM lottery_draw_record
WHERE request_id='550e8400-e29b-41d4-a716-446655440000'
ORDER BY sequence_no;

SELECT b.* FROM user_benefit b
JOIN lottery_draw_record r ON r.id=b.draw_record_id
WHERE r.request_id='550e8400-e29b-41d4-a716-446655440000';

SELECT id,event_id,event_type,status,retry_count,last_error,
       claim_token,next_retry_at,sent_at
FROM message_outbox
WHERE aggregate_id='<orderId>' ORDER BY id;

SELECT * FROM message_consume_record
WHERE event_id IN (
  SELECT event_id FROM message_outbox WHERE aggregate_id='<orderId>'
);
```

### 26.3 Redis CLI 检查

```text
GET draw:quota:10:8:20260731
HGETALL draw:reservation:550e8400-e29b-41d4-a716-446655440000
ZSCORE draw:reservation:timeouts 550e8400-e29b-41d4-a716-446655440000
XLEN luckyhub:stream:lottery
XINFO GROUPS luckyhub:stream:lottery
XPENDING luckyhub:stream:lottery luckyhub-lottery-consumers
```

正常成功且已消费后，Hash status 应为 CONFIRMED，ZSet 中没有该 requestId，group pending 通常为 0。失败释放后 status 为 RELEASED，quota 已减回。

## 27. 推荐断点：亲眼看一次执行链

按顺序设置断点：

1. `LotteryController.draw`：观察 JSON 已变成 DrawCommand。
2. `AuthenticationFilter.doFilterInternal`：观察 JWT payload 和 LoginContext。
3. `PermissionInterceptor.preHandle`：观察要求权限和用户权限集合。
4. `LotteryServiceImpl.draw`：观察第一次幂等查询、snapshot、locked outcome。
5. `DrawEligibilityServiceImpl.load`：观察活动与奖品快照。
6. `RedissonDrawLockService.execute`：观察 lock key 和持有线程。
7. `RedisDrawQuotaService.reserve`：观察 KEYS/ARGV 和 Lua 返回码。
8. `DrawOrderLifecycleServiceImpl.createProcessing`：观察独立事务提交后的订单。
9. `WeightedDrawEngineImpl.select`：观察 totalWeight、selected、upperExclusive。
10. `ActivityPrizeInventoryServiceImpl.decrementIfAvailable`：观察影响行数。
11. `DrawTransactionServiceImpl.persistResult`：观察 WIN/NO_WIN 分支。
12. `OutboxServiceImpl.append`：观察通用 envelope JSON。
13. `OutboxRelayService.relayBatch`：观察 claimToken、publish 和状态更新。
14. `RedisStreamDrawEventConsumer.process`：观察 pending、consume、ACK。
15. `MessageConsumeService.consume`：观察三种事件路由。
16. `BenefitFulfillmentServiceImpl.fulfillAndRecord`：观察 FOR UPDATE 和状态转换。
17. `LotteryReconciliationServiceImpl.reconcileUnderDrawLock`：观察状态矩阵。

调试定时任务时可以在本地临时调小 initial delay，但不要把测试用的极端配置提交为生产默认值。

## 28. 常见故障排查

### 返回 20004

检查 Authorization 是否完整以 `Bearer ` 开头、Token 是否过期、Redis session 是否还在、JWT_SECRET 是否与签发时一致。

### 返回 20001

检查用户角色、角色状态、`sys_role_permission` 和权限缓存。抽奖基础权限与 read:all 权限不同。

### 返回 43002

同时检查 activity.status、start_time、end_time 和应用的 Asia/Shanghai 时间。仅数据库显示 RUNNING 还不够，当前时间必须在 `[start,end)`。

### 返回 43004/43005

用 GET quota Key 和 HGETALL reservation 检查当日占用。十连需要一次性剩余 10 次；不要手工只删除 quota 而保留 reservation，这会制造损坏状态。

### 返回 53001

检查 Redis 连接、密码、Lua 脚本加载、reservation Hash 是否缺字段。代码把 `-1/null/未知返回` 都视为不可用，这是为了不错误改额度。

### 一直 PROCESSING

查订单 created_at、timeout ZSet score、对账 scheduler 是否启用，以及应用日志中的 reconciliation warning。超过 processingTimeout 后应变 FAILED/PROCESSING_TIMEOUT 并出现释放 Outbox。

### Outbox 一直 PENDING/FAILED

检查 `luckyhub.messaging.enabled`、Relay scheduler、Stream Key、Redis 写权限、last_error、next_retry_at。PROCESSING 且 claim_token 不空可能是租约未到期；超过 outboxLease 可重新领取。

### Stream XPENDING 不为 0

查看消费者日志。可能是 JSON 损坏、Redis/MySQL 暂时失败、权益 handler 异常。消息只有业务成功才 ACK；超过 claimIdle 会被重新 claim。

### 权益一直 PENDING 或 GRANT_FAILED

检查对应 fulfillment Outbox 是否 SENT、Stream pending、consume record 和 grant_error。当前 Handler 是模拟状态转换，不要寻找不存在的第三方券/积分调用。

### 同一请求返回 43006

核对 JWT 用户、activityId 和 drawCount 是否与第一次完全相同。网络重试必须原样复用；新业务尝试必须生成新 UUID。

## 29. 如何从零复写一个类似模块

建议按依赖顺序实现，而不是一上来写 Controller：

1. 先写业务不变量：幂等身份、状态机、额度规则、库存失败规则、十连原子性。
2. 设计表和唯一索引，让数据库能守住最终约束。
3. 写纯权重算法和固定随机源测试。
4. 写条件库存 UPDATE 并做真实并发测试。
5. 写 Redis Key 工厂与三个 Lua，测试并发、重复、跨零点和损坏数据。
6. 分离 REQUIRES_NEW 生命周期事务与 REQUIRED 业务事务。
7. 在业务事务内写 Outbox，不直接发消息。
8. 用 Publisher port 隔离中间件，再实现 Stream adapter。
9. 消费端先设计幂等和 ACK 时机，再写 Handler。
10. 最后写对账，逐一覆盖每个可能崩溃点。
11. 最后才暴露 DTO/VO/API 和权限数据范围。

每一步先写会失败的测试，再写最小实现。尤其不要只用 Mock 证明并发、事务和数据库约束；库存、唯一索引、事务回滚、Lua 和 Stream pending 都要用真实 MySQL/Redis 集成测试验证。

## 30. 完整文件职责索引

下面按模块列出本抽奖核心涉及的关键生产文件，便于查漏。正文已经按执行流程解释，附录用于定位。

### 30.1 lottery/controller、dto、vo

- `lottery/controller/LotteryController.java`：5 个抽奖 HTTP 端点、基础权限和统一响应。
- `lottery/dto/DrawCommand.java`：单抽/十连输入校验。
- `lottery/dto/DrawOrderQuery.java`：管理员订单分页过滤。
- `lottery/dto/DrawRecordQuery.java`：记录分页、结果类型和日期过滤。
- `lottery/vo/LotteryActivityView.java`：不泄漏权重/库存的活动公开视图。
- `lottery/vo/DrawOrderView.java`：订单和完整结果列表。
- `lottery/vo/DrawResultView.java`：单个序号的 WIN/NO_WIN 输出。
- `lottery/vo/DrawRecordView.java`：审计记录分页输出。

### 30.2 lottery/service 与内部 model

- `lottery/service/LotteryService.java`、`impl/LotteryServiceImpl.java`：同步用例总编排和幂等响应。
- `lottery/service/DrawEligibilityService.java`、`impl/DrawEligibilityServiceImpl.java`：活动资格和单次配置快照。
- `lottery/service/DrawOrderLifecycleService.java`、`impl/DrawOrderLifecycleServiceImpl.java`：独立创建 PROCESSING、条件失败和释放事件。
- `lottery/service/DrawTransactionService.java`、`impl/DrawTransactionServiceImpl.java`：一个原子 MySQL 抽奖事务。
- `lottery/service/LotteryQueryService.java`、`impl/LotteryQueryServiceImpl.java`：活动、订单、记录的数据范围查询。
- `lottery/model/NewDrawOrder.java`：生命周期创建输入。
- `lottery/model/DrawPrizeSnapshot.java`：权重和展示字段的不可变快照。
- `lottery/model/DrawExecutionContext.java`：执行事务所需全部输入。
- `lottery/model/DrawExecutionResult.java`、`DrawResultItem.java`：内部执行结果。

### 30.3 entity、enum、mapper

- `lottery/entity/LotteryDrawOrder.java`：订单表映射。
- `lottery/entity/LotteryDrawRecord.java`：记录表映射和查询投影 benefitId。
- `lottery/entity/MessageOutbox.java`：Outbox、重试与租约映射。
- `lottery/entity/MessageConsumeRecord.java`：消费幂等行。
- `lottery/enums/DrawOrderStatus.java`：PROCESSING/SUCCESS/FAILED。
- `lottery/enums/DrawResultType.java`：WIN/NO_WIN。
- `lottery/enums/OutboxStatus.java`：PENDING/PROCESSING/SENT/FAILED。
- `lottery/enums/LotteryErrorCode.java`：430xx/530xx 领域错误。
- `lottery/mapper/LotteryDrawOrderMapper.java`：幂等插入、当前读和条件状态更新。
- `lottery/mapper/LotteryDrawRecordMapper.java`：按订单批量读取并关联权益 ID。
- `lottery/mapper/MessageOutboxMapper.java`：Relay 候选、租约 claim、token 条件完成/失败。
- `lottery/mapper/MessageConsumeRecordMapper.java`：消费记录基础 Mapper。

### 30.4 algorithm、inventory、quota、lock

- `lottery/algorithm/WeightedDrawEngine.java`、`WeightedDrawEngineImpl.java`：纯权重选择。
- `lottery/algorithm/DrawRandomSource.java`、`SecureDrawRandomSource.java`：可替换随机源。
- `lottery/algorithm/PrizeWeightSnapshot.java`：算法最小输入。
- `lottery/algorithm/DrawCandidate.java`：PRIZE_CANDIDATE/NO_WIN 候选。
- `inventory/mapper/ActivityPrizeInventoryMapper.java`：原子条件扣库存 SQL。
- `inventory/service/ActivityPrizeInventoryService.java`、`ActivityPrizeInventoryServiceImpl.java`：影响行数转布尔语义。
- `lottery/quota/DrawQuotaKeys.java`：集中生成 quota/reservation/timeout/lock Key。
- `lottery/quota/DrawQuotaService.java`、`RedisDrawQuotaService.java`：Lua 调用、时间计算和错误映射。
- `lottery/quota/QuotaReservationRequest.java`、`QuotaReservationResult.java`、`ReservationStatus.java`：额度契约。
- `lottery/lock/DrawLockService.java`、`RedissonDrawLockService.java`：短临界区分布式锁。
- `resources/redis/lottery/reserve_draw_quota.lua`：原子预占与 timeout 登记。
- `resources/redis/lottery/confirm_draw_quota.lua`：幂等确认。
- `resources/redis/lottery/release_draw_quota.lua`：身份核对、幂等释放和 quota 下限。

### 30.5 event、Outbox、Stream

- `lottery/messaging/event/DrawEventType.java`：三种事件类型。
- `DrawEventEnvelope.java`：broker-neutral 版本化信封。
- `DrawConfirmedEvent.java`：成功确认额度 payload。
- `DrawReleaseRequestedEvent.java`：失败释放额度 payload。
- `PrizeFulfillmentRequestedEvent.java`：权益履约 payload。
- `lottery/messaging/port/DrawEventPublisher.java`：可替换消息发布端口。
- `lottery/service/OutboxService.java`、`OutboxServiceImpl.java`：强制加入业务事务写 Outbox。
- `lottery/service/OutboxRelayService.java`：短租约领取、发布、重试。
- `lottery/scheduler/OutboxRelayScheduler.java`：周期触发 Relay。
- `lottery/messaging/redis/RedisStreamDrawEventPublisher.java`：当前 Redis Stream 发布适配器。
- `RedisStreamInitializer.java`：安全创建 Stream Group。
- `RedisStreamDrawEventConsumer.java`：读新消息、claim pending、成功 ACK。
- `lottery/service/MessageConsumeService.java`：事件分派和消费幂等协调。

### 30.6 benefit 与 reconciliation

- `benefit/controller/BenefitController.java`：2 个权益查询端点。
- `benefit/dto/BenefitQuery.java`、`benefit/vo/BenefitView.java`：查询输入和快照输出。
- `benefit/entity/UserBenefit.java`、`benefit/mapper/UserBenefitMapper.java`：权益表、行锁和条件状态更新。
- `benefit/enums/BenefitStatus.java`、`BenefitErrorCode.java`：履约状态和错误。
- `benefit/service/BenefitFulfillmentService.java`、`BenefitFulfillmentServiceImpl.java`：事务履约、幂等与失败记录。
- `benefit/service/BenefitQueryService.java`、`BenefitQueryServiceImpl.java`：权益数据范围和批量快照查询。
- `benefit/handler/BenefitFulfillmentHandler.java`、`BenefitFulfillmentRouter.java`：按 PrizeType 路由的扩展点。
- 四个具体 Handler：第一版模拟状态转换。
- `lottery/service/LotteryReconciliationService.java`、`LotteryReconciliationServiceImpl.java`：超时状态矩阵和身份栅栏。
- `lottery/scheduler/LotteryReconciliationScheduler.java`：可配置对账调度。
- `lottery/model/ReconciliationResult.java`：scanned/confirmed/released/timedOut/deferred/failed 计数。

### 30.7 配置、权限与迁移

- `lottery/config/LotteryProperties.java`：抽奖时间、批次和正值约束。
- `lottery/config/MessagingProperties.java`：Stream、Consumer、租约配置约束。
- `lottery/config/LotteryMessagingConfiguration.java`：注册消息配置属性。
- `config/RedissonConfig.java`：由现有 Redis 配置创建并关闭 RedissonClient。
- `config/MybatisPlusConfig.java`：把 lottery、inventory、benefit Mapper 包纳入扫描并提供分页插件。
- `config/AuthenticationFilterConfig.java`、`PermissionInterceptorConfig.java`：把认证授权覆盖到统一 API。
- `rbac/constant/PermissionCodes.java`：9 个抽奖/权益权限常量。
- `rbac/service/DataScopeService.java`、`Impl/DataScopeServiceImpl.java`、`UserDataScope.java`：本人/全量范围。
- `rbac/service/Impl/UserServiceImpl.java`：创建用户时在同一事务中自动绑定启用的 USER 角色。
- `activity/dto/CreateActivityCommand.java`、`UpdateActivityCommand.java`：接收并校验 `noWinWeight >= 0`。
- `activity/entity/MarketingActivity.java`、`activity/vo/ActivityView.java`：数据库与管理端输出贯通未中奖权重。
- `activity/service/impl/ActivityServiceImpl.java`：创建、修改、发布时保存并验证未中奖权重与总权重。
- `resources/db/migration/V5__add_lottery_core.sql`：核心列、表、索引、USER/RBAC。
- `V6__add_outbox_delivery_error.sql`：last_error。
- `V7__lease_outbox_delivery.sql`：PROCESSING 和 claim_token 租约。
- `resources/application.yaml`：MySQL、Redis、抽奖、消息、OSS 和 JWT 配置入口。
- `pom.xml`：增加 Redisson core 依赖；保留 Spring Data Redis 供 Lua、Hash、ZSet 和 Stream 使用。

## 31. 最后用一句话复述原理

LuckyHub 的核心不是“生成一个随机数”，而是：用 JWT 确认是谁，用 MySQL 唯一订单确认这是不是同一次请求，用 Redisson 缩短并发临界区，用 Lua 原子预占次数，用纯权重算法选择候选，用 MySQL 条件 UPDATE 决定最终中奖，用一个事务保存记录/权益/Outbox，用至少一次消息和消费幂等完成额度与履约，再用 ZSet 对账修复任何崩溃留下的缝隙。

接口调用细节见 [`lottery-api.md`](lottery-api.md)。
