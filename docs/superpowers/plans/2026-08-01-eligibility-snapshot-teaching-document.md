# EligibilitySnapshot Teaching Document Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 编写一篇按照真实抽奖执行流程讲解 `EligibilitySnapshot` 的中文教学文档，并将它加入项目文档入口。

**Architecture:** 正文从 `LotteryServiceImpl.draw()` 中的目标代码出发，先解释业务问题，再进入 `DrawEligibilityServiceImpl.load()` 追踪数据组装，最后跟随快照进入额度预占、十连抽循环、权重选择、MySQL 条件扣减和结果落库。所有示例与结论都通过当前源码和测试交叉核对，明确内存配置快照与数据库事务快照的区别。

**Tech Stack:** Markdown、Mermaid、Java 21 `record`、Spring Boot、MyBatis-Plus、JUnit 5、Mockito、Git

## Global Constraints

- 使用中文 UTF-8 编写。
- 正式文档路径固定为 `docs/LuckyHub-抽奖资格快照EligibilitySnapshot详解.md`。
- 只修改教学文档和 `README.md` 文档入口，不修改抽奖业务代码、数据库结构或接口行为。
- 讲解必须对应当前仓库真实代码，不把建议方案写成已实现功能。
- 讲解顺序必须以程序执行流程为主，Java 语法解释穿插其中。
- 必须明确快照不能代替数据库原子库存扣减，也不是 Redis 缓存或 MySQL 事务快照。

---

## File Structure

- Create: `docs/LuckyHub-抽奖资格快照EligibilitySnapshot详解.md`
  - 独立教学正文，负责完整解释目标代码、快照构造、后续流转、并发边界、示例和调试方法。
- Modify: `README.md`
  - 在现有抽奖核心流程文档入口之后添加新教学文档链接。
- Read-only source: `src/main/java/com/dongqh/luckyhub/lottery/service/DrawEligibilityService.java`
- Read-only source: `src/main/java/com/dongqh/luckyhub/lottery/service/impl/DrawEligibilityServiceImpl.java`
- Read-only source: `src/main/java/com/dongqh/luckyhub/lottery/service/impl/LotteryServiceImpl.java`
- Read-only source: `src/main/java/com/dongqh/luckyhub/lottery/model/DrawPrizeSnapshot.java`
- Read-only source: `src/main/java/com/dongqh/luckyhub/lottery/model/DrawExecutionContext.java`
- Read-only source: `src/main/java/com/dongqh/luckyhub/lottery/service/impl/DrawTransactionServiceImpl.java`
- Read-only source: `src/main/java/com/dongqh/luckyhub/lottery/algorithm/WeightedDrawEngineImpl.java`
- Read-only source: `src/main/java/com/dongqh/luckyhub/inventory/mapper/ActivityPrizeInventoryMapper.java`
- Read-only source: `src/test/java/com/dongqh/luckyhub/lottery/service/impl/DrawEligibilityServiceTests.java`
- Read-only source: `src/test/java/com/dongqh/luckyhub/lottery/service/LotteryServiceTests.java`

### Task 1: 编写执行流程与快照构造主体

**Files:**
- Create: `docs/LuckyHub-抽奖资格快照EligibilitySnapshot详解.md`

**Interfaces:**
- Consumes: `DrawEligibilityService.EligibilitySnapshot load(long activityId)` 及上述只读源码中的真实字段和调用关系。
- Produces: 一篇包含目标代码逐项解释、`load()` 执行流程、五个字段来源以及不可变性说明的中文 Markdown 文档。

- [ ] **Step 1: 创建正文骨架和学习目标**

写入标题、适用读者、阅读后应能回答的问题，以及以下主流程：

```text
LotteryServiceImpl.draw
  -> 第一次 MySQL 幂等检查
  -> eligibilityService.load(activityId)
  -> 得到 EligibilitySnapshot
  -> 用户活动锁
  -> Redis 额度预占（使用 dailyLimit）
  -> 创建 PROCESSING 订单
  -> DrawExecutionContext（使用 noWinWeight、prizes、snapshotTime）
  -> DrawTransactionServiceImpl.execute
```

- [ ] **Step 2: 逐项解释目标 Java 代码**

准确解释：

```java
DrawEligibilityService.EligibilitySnapshot snapshot =
        eligibilityService.load(command.activityId());
```

其中 `DrawEligibilityService.EligibilitySnapshot` 是接口中的嵌套类型，`snapshot` 是持有返回对象引用的局部变量，`eligibilityService.load(...)` 负责校验和组装。说明“保存成 snapshot”不是另存数据库，而是让当前方法后续步骤复用同一份内存数据。

- [ ] **Step 3: 按执行顺序讲解 load 方法**

覆盖以下真实步骤和错误出口：

```text
selectById(activityId)
  -> 活动不存在：ACTIVITY_NOT_FOUND
  -> 取得上海时区当前时间并截断到毫秒
  -> 状态、开始时间、结束时间校验
  -> 查询活动奖品关系并排序
  -> 批量查询奖品定义
  -> 映射为 DrawPrizeSnapshot
  -> 校验 dailyLimit、noWinWeight 和空奖池
  -> new EligibilitySnapshot(...)
```

- [ ] **Step 4: 解释 record 和不可变列表**

逐字段解释：

```java
record EligibilitySnapshot(
        long activityId,
        int dailyLimit,
        int noWinWeight,
        List<DrawPrizeSnapshot> prizes,
        LocalDateTime snapshotTime)
```

说明 Java 自动生成构造器、访问器、`equals`、`hashCode`、`toString`，并解释紧凑构造器中的 `prizes = List.copyOf(prizes)` 只防止列表结构被后续增删，不代表锁住数据库。

- [ ] **Step 5: 检查主体章节的源码一致性**

运行：

```powershell
rg -n "record EligibilitySnapshot|EligibilitySnapshot load|return new EligibilitySnapshot|List.copyOf" src/main/java/com/dongqh/luckyhub/lottery
```

预期：正文所列类型、字段和构造过程都能在真实源码中定位，且正文没有声称 `EligibilitySnapshot` 被整体持久化或缓存到 Redis。

### Task 2: 编写快照后续流转、十连抽示例与并发边界

**Files:**
- Modify: `docs/LuckyHub-抽奖资格快照EligibilitySnapshot详解.md`

**Interfaces:**
- Consumes: Task 1 的 `EligibilitySnapshot` 字段解释；`QuotaReservationRequest`、`DrawExecutionContext`、`DrawTransactionServiceImpl.execute`、`ActivityPrizeInventoryMapper.decrementIfAvailable`。
- Produces: 能够从快照一路追踪到最终抽奖记录的完整调用链讲解。

- [ ] **Step 1: 添加字段来源与使用位置对照表**

表格必须明确：

| 字段 | 主要来源 | 后续用途 |
|---|---|---|
| `activityId` | 抽奖命令 | 锁、订单和事件身份 |
| `dailyLimit` | 活动表 | Redis 每日额度预占 |
| `noWinWeight` | 活动表 | 权重抽取中的独立未中奖区间 |
| `prizes` | 活动奖品关系表 + 奖品表 | 权重选择、库存扣减、结果字段快照 |
| `snapshotTime` | 配置时区的当前时间 | 抽奖完成时间、失败补偿和事件时间 |

- [ ] **Step 2: 添加 Mermaid 时序图**

图中至少出现 `LotteryServiceImpl`、`DrawEligibilityServiceImpl`、MySQL、Redis/Lock、`DrawTransactionServiceImpl`，并清楚表现 `load()` 只为新订单调用一次，十连抽循环发生在事务服务内。

- [ ] **Step 3: 添加完整十连抽示例**

示例使用固定、容易计算的数据：每日上限 20、未中奖权重 30、三个奖品及各自权重/库存/启用状态。逐步说明一次加载得到同一配置输入，十次循环复用它；禁用或初始库存为 0 的奖品不会成为有效中奖，抽中候选但数据库条件扣减失败时最终记为 `NO_WIN`。

- [ ] **Step 4: 解释配置并发修改与库存并发变化**

明确以下时间线：

```text
T1 当前请求加载 snapshot
T2 管理员修改活动或奖品配置
T3 当前请求继续使用 T1 的内存配置
T4 新请求重新 load，读取 T2 后的配置
```

同时说明库存的最终真相是：

```sql
UPDATE marketing_activity_prize
SET remaining_stock = remaining_stock - 1
WHERE id = #{activityPrizeId}
  AND remaining_stock > 0
```

只有受影响行数为 1 才算最终中奖。

- [ ] **Step 5: 区分三种“快照”概念**

分别定义：当前请求的内存配置快照、MySQL 事务一致性读快照、抽奖记录中保存的历史奖品字段。列出它们的保存位置、生命周期和作用，避免术语混淆。

### Task 3: 增加调试教学、阅读测试与可迁移设计方法

**Files:**
- Modify: `docs/LuckyHub-抽奖资格快照EligibilitySnapshot详解.md`

**Interfaces:**
- Consumes: Task 1、Task 2 的完整调用链。
- Produces: 学习者可以实际调试、验证并在其他业务中复现该设计的操作指南。

- [ ] **Step 1: 添加 IDE 断点路径**

依次给出以下断点及应观察的变量：

```text
LotteryServiceImpl.draw：command、userId
DrawEligibilityServiceImpl.load：activity、now、relations、prizes、snapshots
LotteryServiceImpl.draw 返回后：snapshot 五个字段
DrawTransactionServiceImpl.execute：context、weights、candidate、wonPrize
ActivityPrizeInventoryServiceImpl.decrementIfAvailable：activityPrizeId、受影响行数
```

- [ ] **Step 2: 用现有单元测试解释设计验证**

引用 `DrawEligibilityServiceTests.loadsOneImmutableShanghaiTimeConfigurationSnapshot` 和 `LotteryServiceTests.orchestratesInRequiredOrderAndReturnsExactSynchronousResult`，解释它们分别验证不可变性、时区、只加载一次和字段传递，不虚构新的测试结果。

- [ ] **Step 3: 添加通用设计模板**

给出从业务需求反推只读快照的五步方法：确定一个操作周期、列出必须一致的输入、集中查询和校验、构造不可变对象、把最终并发真相留给原子写操作。配套一个非抽奖场景的简短伪代码示例。

- [ ] **Step 4: 添加常见误解和自测题**

至少回答：为什么不直接保存为 `var`、为什么不每抽一次重新查询、`record` 是否绝对不可变、快照是否会过期、是否能防超卖、为什么幂等命中时不重新加载。

### Task 4: 添加文档入口并执行文档质量检查

**Files:**
- Modify: `README.md`
- Verify: `docs/LuckyHub-抽奖资格快照EligibilitySnapshot详解.md`

**Interfaces:**
- Consumes: Task 1-3 完成的正式文档。
- Produces: 可从 README 发现、无占位符、无明显 Markdown 格式错误且与源码一致的最终文档。

- [ ] **Step 1: 在 README 添加文档链接**

在现有 `LuckyHub-抽奖核心流程实现详解.md` 链接之后增加两行说明，链接到：

```markdown
[`docs/LuckyHub-抽奖资格快照EligibilitySnapshot详解.md`](../../LuckyHub-抽奖资格快照EligibilitySnapshot详解.md)
```

- [ ] **Step 2: 执行占位符和关键事实扫描**

运行：

```powershell
rg -n "TBD|TODO|待补充|EligibilitySnapshot|List.copyOf|decrementIfAvailable|十连抽" docs/LuckyHub-抽奖资格快照EligibilitySnapshot详解.md README.md
```

预期：没有 `TBD`、`TODO` 或“待补充”；所有核心主题都能被检索到。

- [ ] **Step 3: 检查 Markdown 代码围栏和 Git 空白错误**

运行：

```powershell
$fenceCount = (Select-String -Path 'docs/LuckyHub-抽奖资格快照EligibilitySnapshot详解.md' -Pattern '^```').Count
if ($fenceCount % 2 -ne 0) { throw "Markdown code fence count is odd: $fenceCount" }
git diff --check
```

预期：代码围栏数量为偶数，`git diff --check` 无输出且退出码为 0。

- [ ] **Step 4: 对照源码执行最终事实核查**

运行：

```powershell
rg -n "eligibilityService.load|snapshot.dailyLimit|snapshot.noWinWeight|snapshot.prizes|snapshot.snapshotTime" src/main/java/com/dongqh/luckyhub/lottery/service/impl/LotteryServiceImpl.java
rg -n "selectById|selectList|selectByIds|new DrawPrizeSnapshot|new EligibilitySnapshot" src/main/java/com/dongqh/luckyhub/lottery/service/impl/DrawEligibilityServiceImpl.java
```

预期：文档描述的字段流转和数据查询均有源码依据。

- [ ] **Step 5: 提交文档**

```powershell
git add -- 'docs/LuckyHub-抽奖资格快照EligibilitySnapshot详解.md' 'README.md'
git commit -m "docs: explain lottery eligibility snapshot"
```

预期：提交只包含正式教学文档和 README 入口，不包含 `.codex-progress/`、`.superpowers/` 或业务代码改动。
