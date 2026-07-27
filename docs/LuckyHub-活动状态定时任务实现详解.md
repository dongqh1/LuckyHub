# LuckyHub 活动状态定时任务实现详解

> 本文沿着一次真实执行流程讲解：没有 HTTP 请求时，Spring 为什么会自动调用 Java 方法，以及活动怎样真正从 `SCHEDULED` 变成 `RUNNING`，再变成 `ENDED`。

## 一、先理解最终效果

假设数据库里有一条活动：

| 字段 | 值 |
| --- | --- |
| `status` | `SCHEDULED` |
| `start_time` | `2026-08-01 10:00:00.000` |
| `end_time` | `2026-08-01 12:00:00.000` |

系统运行期间不需要管理员点击按钮：

```text
10:00之前：SCHEDULED
    ↓ 到达开始时间后的下一轮任务
10:00之后：RUNNING
    ↓ 到达结束时间后的下一轮任务
12:00之后：ENDED
```

MySQL 不会因为 `end_time` 到了，就自动理解业务并修改 `status`。时间字段只是数据，真正检查时间并执行 `UPDATE` 的是 Spring 定时任务。

完整调用链：

```text
Spring Boot启动
    ↓
ActivitySchedulingConfiguration开启调度
    ↓
Spring登记ActivityStatusScheduler中的任务
    ↓
等待配置的时间
    ↓
refreshActivityStatuses()
    ↓
ActivityStatusService.refreshStatuses()
    ↓
ActivityStatusServiceImpl开启事务
    ↓
MarketingActivityMapper执行两条UPDATE
    ├─ SCHEDULED → RUNNING
    └─ SCHEDULED/RUNNING → ENDED
    ↓
事务提交，状态保存到MySQL
```

## 二、为什么要持久化状态

如果只在查询时比较 `end_time`，临时向前端返回 `ENDED`，会出现：

- 数据库里的真实值仍然是 `RUNNING`；
- 直接查询数据库和其他后台程序会看到旧状态；
- 按 `status = 'ENDED'` 统计会漏数据；
- 每个读取入口都必须重复计算。

LuckyHub 的方案是：

```text
时间到达 → 定时UPDATE → 数据库status改变 → 所有查询方看到同一结果
```

普通查询接口只读数据，不负责顺便修改状态。这个任务只依赖 MySQL 中的 `status`、`start_time`、`end_time`，所以不需要 Redis。

## 三、应用启动：打开定时功能总开关

代码位置：

```text
src/main/java/com/dongqh/luckyhub/activity/config/
└─ ActivitySchedulingConfiguration.java
```

代码：

```java
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ActivitySchedulingConfiguration {
}
```

### `@Configuration`

它告诉 Spring 这是配置类。应用启动进行组件扫描时，Spring 会处理该类上的配置注解。

`proxyBeanMethods = false` 表示不需要为配置方法之间的调用创建代理。这个类没有 `@Bean` 方法，关闭代理即可。

### `@EnableScheduling`

它是定时任务总开关：

```text
没有@EnableScheduling
    ↓
即使方法上写了@Scheduled，也不会按计划执行

存在@EnableScheduling
    ↓
Spring寻找由自己管理、且方法上有@Scheduled的Bean
```

这个类只在应用启动时被处理，不会每30秒重新创建。启动流程大致是：

```text
LuckyhubApplication.main()
    ↓
SpringApplication.run(...)
    ↓
扫描com.dongqh.luckyhub
    ↓
发现ActivitySchedulingConfiguration
    ↓
处理@EnableScheduling并开启任务登记能力
```

它解决的问题是：“Spring 默认不知道我们需要运行定时任务。”

## 四、Spring发现需要执行的方法

代码位置：

```text
src/main/java/com/dongqh/luckyhub/activity/scheduler/
└─ ActivityStatusScheduler.java
```

代码：

```java
@Component
public class ActivityStatusScheduler {

    private final ActivityStatusService service;

    public ActivityStatusScheduler(ActivityStatusService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString =
                    "${luckyhub.activity.status-refresh-interval:30000}",
            initialDelayString =
                    "${luckyhub.activity.status-refresh-initial-delay:0}"
    )
    public void refreshActivityStatuses() {
        service.refreshStatuses();
    }
}
```

### 1. 为什么需要 `@Component`

`@Scheduled` 只对 Spring 管理的 Bean 生效。`@Component` 让组件扫描创建 `ActivityStatusScheduler`：

```text
发现@Component
    ↓
调用构造方法创建Scheduler
    ↓
放进Spring容器
    ↓
检查Bean的方法
    ↓
发现@Scheduled并登记任务
```

自己通过 `new ActivityStatusScheduler(...)` 创建的普通对象不会被 Spring 自动调度。

### 2. Service 是怎样进来的

```java
public ActivityStatusScheduler(ActivityStatusService service)
```

Spring 创建 Scheduler 时，会找到实现该接口的 `ActivityStatusServiceImpl`，再通过构造方法注入。

这里的职责分工是：

```text
Scheduler：什么时候执行
Service：一次执行包含哪些业务步骤和事务
Mapper：具体执行什么SQL
```

Scheduler 依赖接口而非实现类，测试时就可以传入 Mock Service。

### 3. `initialDelayString`

它表示应用启动并登记任务后，第一次执行前等待多久。默认：

```text
0毫秒
```

应用如果停机两小时，重启后会尽快执行第一次刷新，修复停机期间已经过期的活动。

### 4. `fixedDelayString`

它表示上一次任务执行完成后，再等待多久开始下一次。默认：

```text
30000毫秒 = 30秒
```

假设本轮耗时200毫秒：

```text
10:00:00.000 本轮开始
10:00:00.200 本轮结束
               等待30秒
10:00:30.200 下轮开始
```

`fixedDelay` 与 `fixedRate` 的区别：

```text
fixedDelay：上次完成 → 等待30秒 → 下次开始
fixedRate ：上次开始 → 间隔30秒 → 下次开始
```

状态刷新选择 `fixedDelay`，上一轮执行时间会自然计入周期，避免按固定启动频率堆积本任务。

### 5. 谁调用 `refreshActivityStatuses`

不是 Controller，也不是前端。调用者是 Spring 的任务调度器：

```java
public void refreshActivityStatuses() {
    service.refreshStatuses();
}
```

这段逻辑只负责触发业务，不在入口里堆放 SQL。

## 五、执行间隔从哪里来

代码位置：

```text
src/main/resources/application.yaml
```

配置：

```yaml
luckyhub:
  activity:
    status-refresh-interval:
      ${ACTIVITY_STATUS_REFRESH_INTERVAL:30000}
    status-refresh-initial-delay:
      ${ACTIVITY_STATUS_REFRESH_INITIAL_DELAY:0}
```

占位符：

```text
${环境变量名称:默认值}
```

所以：

```text
ACTIVITY_STATUS_REFRESH_INTERVAL存在 → 使用环境变量
不存在                               → 使用30000
```

值的传递路径：

```text
.env或系统环境变量
    ↓
application.yaml中的luckyhub.activity配置
    ↓
@Scheduled读取配置
    ↓
Spring调度器使用该间隔
```

不填写也能工作。测试时可在 `.env` 临时加入：

```properties
ACTIVITY_STATUS_REFRESH_INTERVAL=5000
ACTIVITY_STATUS_REFRESH_INITIAL_DELAY=0
```

这会改为每轮完成5秒后再执行。修改后需要重启，因为执行计划是在应用启动时登记的。生产环境不宜设置得过小，否则会增加数据库检查频率。

## 六、进入业务服务和事务

接口位置：

```text
src/main/java/com/dongqh/luckyhub/activity/service/
└─ ActivityStatusService.java
```

```java
public interface ActivityStatusService {
    ActivityStatusRefreshResult refreshStatuses();
}
```

实现位置：

```text
src/main/java/com/dongqh/luckyhub/activity/service/impl/
└─ ActivityStatusServiceImpl.java
```

```java
@Service
public class ActivityStatusServiceImpl
        implements ActivityStatusService {

    private final MarketingActivityMapper mapper;

    public ActivityStatusServiceImpl(
            MarketingActivityMapper mapper
    ) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ActivityStatusRefreshResult refreshStatuses() {
        int runningCount =
                mapper.promoteScheduledToRunning();
        int endedCount =
                mapper.finishExpiredActivities();
        return new ActivityStatusRefreshResult(
                runningCount,
                endedCount
        );
    }
}
```

### 1. `@Service`

它让实现类成为 Spring Bean。Scheduler 依赖 `ActivityStatusService` 接口，Spring 将这个实现注入进去。

接口描述“能做什么”，实现类描述“怎样做”。这样 Scheduler 不必知道数据库规则，也方便独立测试。

### 2. `@Transactional`

调用这个方法时，Spring 事务代理执行：

```text
开启事务
    ↓
执行第一条UPDATE
    ↓
执行第二条UPDATE
    ↓
方法正常结束
    ↓
提交事务
```

如果中途出现触发回滚的运行时异常：

```text
本轮失败 → 事务回滚 → 下一轮任务再次尝试
```

### 3. 为什么先开始，再结束

```java
mapper.promoteScheduledToRunning();
mapper.finishExpiredActivities();
```

第一步推进正常到达开始时间的活动，第二步结束过期活动。

已经错过整个活动时间段的 `SCHEDULED` 不会满足第一步的“结束时间仍在未来”，但会在第二步直接变成 `ENDED`。

### 4. 返回结果

位置：

```text
ActivityStatusRefreshResult.java
```

```java
public record ActivityStatusRefreshResult(
        int runningCount,
        int endedCount
) {
}
```

- `runningCount`：本轮多少行变成 `RUNNING`；
- `endedCount`：本轮多少行变成 `ENDED`。

当前 Scheduler 忽略返回值，但以后可用于日志、监控和管理后台。Mapper 返回的影响行数也方便测试验证。

## 七、Mapper真正修改MySQL

代码位置：

```text
src/main/java/com/dongqh/luckyhub/activity/mapper/
└─ MarketingActivityMapper.java
```

### 1. 到达开始时间

```java
@Update("""
        UPDATE marketing_activity
        SET status = 'RUNNING'
        WHERE status = 'SCHEDULED'
          AND start_time <= NOW(3)
          AND end_time > NOW(3)
        """)
int promoteScheduledToRunning();
```

对应规则：

```text
状态是SCHEDULED
并且开始时间已经到达
并且结束时间还没有到达
    ↓
改成RUNNING
```

它不会修改：

- `DRAFT`：草稿不能被任务自动发布；
- `DISABLED`：管理员禁用的活动不能被任务重新启用；
- `ENDED`：已结束活动保持结束；
- 已经是 `RUNNING` 的活动。

### 2. 到达结束时间

```java
@Update("""
        UPDATE marketing_activity
        SET status = 'ENDED'
        WHERE status IN ('SCHEDULED', 'RUNNING')
          AND end_time <= NOW(3)
        """)
int finishExpiredActivities();
```

为什么还处理 `SCHEDULED`？考虑：

```text
活动时间：10:00—11:00
应用停机：09:00—12:00
```

12:00重启时，数据库仍是 `SCHEDULED`。第一条 SQL 因为活动已经结束，不会先改成 `RUNNING`；第二条 SQL 会直接：

```text
SCHEDULED → ENDED
```

这使应用重启后能修复错过的状态变化。

### 3. `NOW(3)`

`NOW(3)` 使用 MySQL 当前时间并保留毫秒精度。表中字段也是：

```sql
start_time DATETIME(3) NOT NULL,
end_time   DATETIME(3) NOT NULL
```

使用数据库时间可直接批量比较，无需把活动全部加载到 Java 中逐个判断。项目连接参数指定 `serverTimezone=Asia/Shanghai`，部署时仍需确保数据库、应用和业务约定时区一致。

可检查 MySQL 时间：

```sql
SELECT NOW(3), @@session.time_zone, @@global.time_zone;
```

### 4. 为什么是两条批量UPDATE

不推荐：

```text
SELECT全部活动 → Java循环判断 → 每条分别UPDATE
```

当前实现：

```text
一条UPDATE启动全部符合条件的活动
一条UPDATE结束全部符合条件的活动
```

筛选和更新都在 MySQL 内完成，减少数据库往返和 Java 对象数量。`UPDATE ... WHERE ...` 也避免典型的“先查出来、稍后再改”时间窗口。

Mapper 方法返回 `int`，就是 SQL 影响的行数。

## 八、完整示例

当前数据库时间是 `10:00:05`：

| 活动 | 状态 | 开始 | 结束 |
| --- | --- | --- | --- |
| A | `SCHEDULED` | 10:00 | 12:00 |
| B | `RUNNING` | 09:00 | 10:00 |
| C | `DISABLED` | 09:00 | 10:00 |

执行过程：

```text
Spring触发Scheduler
    ↓
Scheduler调用Service
    ↓
事务开启
    ↓
第一条UPDATE：A满足条件，SCHEDULED → RUNNING
runningCount = 1
    ↓
第二条UPDATE：B满足条件，RUNNING → ENDED
endedCount = 1
    ↓
C是DISABLED，两条SQL都不修改
    ↓
事务提交
```

最终：

| 活动 | 新状态 |
| --- | --- |
| A | `RUNNING` |
| B | `ENDED` |
| C | `DISABLED` |

返回：

```java
new ActivityStatusRefreshResult(1, 1)
```

默认每30秒检查一次，不是给每个活动设置精确到那一秒的闹钟。若活动在 `10:00:01` 开始，而任务在 `10:00:00` 和 `10:00:30` 执行，它会在 `10:00:30` 变成 `RUNNING`。最大延迟接近一个刷新间隔。

## 九、查询接口为什么能看到新状态

事务提交后，`marketing_activity.status` 已经真正改变。详情与列表接口：

```text
GET /api/admin/activities/{id}
GET /api/admin/activities
```

只需读取数据库：

```text
Controller → ActivityService → Mapper → MySQL → ActivityView
```

数据库是 `ENDED`，查询自然返回：

```json
{
  "status": "ENDED"
}
```

因此不是“每次查询顺便修改状态”，而是定时任务统一提前修改。

## 十、异常和重启

### MySQL暂时不可用

Mapper 抛出异常，本轮事务回滚。应用仍正常运行时，下一轮会重新尝试。

SQL 是幂等的：

- 已经是 `RUNNING` 的记录不再满足 `status = 'SCHEDULED'`；
- 已经是 `ENDED` 的记录不再满足第二条状态条件。

重复执行不会反复改变同一状态。

### 应用停机

停机期间 Java 任务不会执行。重启后首次延迟默认是0，且结束 SQL 同时匹配 `SCHEDULED` 和 `RUNNING`，第一次刷新会修复已经过期的记录。

### 没有记录需要更新

两条 SQL 影响行数都是0：

```java
new ActivityStatusRefreshResult(0, 0)
```

这是正常结果。

## 十一、怎样自己测试

### 1. 手工观察

在 `.env` 临时配置：

```properties
ACTIVITY_STATUS_REFRESH_INTERVAL=5000
ACTIVITY_STATUS_REFRESH_INITIAL_DELAY=0
```

重启应用，创建并发布一个开始、结束时间都很近的活动，然后查询：

```sql
SELECT
    id,
    activity_name,
    status,
    start_time,
    end_time,
    NOW(3) AS database_now
FROM marketing_activity
ORDER BY id DESC;
```

预期：

```text
发布后、开始前：SCHEDULED
开始后的下一轮：RUNNING
结束后的下一轮：ENDED
```

测试结束后删除临时配置或恢复为30000。

### 2. IDE断点

按调用顺序打断点：

1. `ActivityStatusScheduler.refreshActivityStatuses()`
2. `ActivityStatusServiceImpl.refreshStatuses()`
3. Service 中调用两个 Mapper 方法的代码行

第一次停在 Scheduler，说明不是前端，而是 Spring 自动触发。

Mapper 是 MyBatis 动态生成的代理，通常不会进入普通实现类方法体。观察 Service 调用前后，或开启 SQL 日志查看数据库语句即可。

### 3. 自动化测试

Scheduler 测试：

```text
src/test/java/com/dongqh/luckyhub/activity/scheduler/
ActivityStatusSchedulerTests.java
```

它直接调用任务方法，再验证：

```java
verify(service).refreshStatuses();
```

测试的是：

```text
Scheduler → Service
```

Service 测试：

```text
src/test/java/com/dongqh/luckyhub/activity/service/
ActivityStatusServiceTests.java
```

它让两个 Mock Mapper 方法返回2和3，再验证结果及两次调用，测试：

```text
Service → Mapper
```

真实数据库测试：

```text
src/test/java/com/dongqh/luckyhub/activity/mapper/
ActivityMapperTests.java
```

它插入两条测试活动，真实执行批量 SQL，再从 MySQL 查询并断言为 `RUNNING` 和 `ENDED`。测试使用 `@Transactional`，完成后回滚。

运行三个测试：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 "-Dtest=ActivityStatusSchedulerTests,ActivityStatusServiceTests,ActivityMapperTests" test
```

## 十二、每个文件在哪里被使用

| 文件 | 作用 | 使用者 |
| --- | --- | --- |
| `ActivitySchedulingConfiguration.java` | 开启调度 | Spring启动过程 |
| `ActivityStatusScheduler.java` | 定义触发时机 | Spring调度器 |
| `application.yaml` | 提供间隔和首次延迟 | `@Scheduled` |
| `ActivityStatusService.java` | 定义刷新能力 | Scheduler |
| `ActivityStatusServiceImpl.java` | 组织规则和事务 | Scheduler通过接口调用 |
| `ActivityStatusRefreshResult.java` | 保存更新数量 | Service |
| `MarketingActivityMapper.java` | 执行批量SQL | Service |
| `ActivityStatusSchedulerTests.java` | 验证触发委托 | Maven测试 |
| `ActivityStatusServiceTests.java` | 验证服务流程 | Maven测试 |
| `ActivityMapperTests.java` | 验证真实持久化 | Maven测试 |

核心分工：

```text
Configuration负责开启
Scheduler负责按时触发
Service负责业务流程与事务
Mapper负责SQL
MySQL保存最终状态
```

## 十三、多实例部署注意事项

如果以后同时运行三个应用实例，三个实例都可能执行更新。当前 SQL 有状态条件并且幂等：

```text
实例A先把SCHEDULED改成RUNNING
    ↓
实例B执行时已不满足status = 'SCHEDULED'
```

最终状态仍安全，但会重复检查数据库。

如果以后状态变化还要发送短信、优惠券或消息，就不能只依赖当前 SQL，需要额外考虑：

- 数据库条件更新和事件表；
- Outbox可靠事件；
- ShedLock等分布式任务锁；
- 业务幂等键。

否则多个实例或失败重试可能重复发送。当前仅更新最终状态的单实例场景不必提前引入这些复杂度。

## 十四、常见问题

### 为什么结束时间那一秒没有立即变化

默认30秒轮询，状态在截止时间后的下一轮更新。

### 修改 `.env` 后为什么没变化

需要重启应用，让 Spring 使用新配置重新登记任务。

### 为什么禁用活动到期后仍是 `DISABLED`

结束 SQL 只处理 `SCHEDULED`、`RUNNING`。禁用是管理员的显式决定，定时任务不能覆盖。

### 为什么草稿到开始时间仍是 `DRAFT`

草稿没有发布。启动 SQL 只处理 `SCHEDULED`，任务不能替管理员自动发布。

### 为什么不为每个活动创建一个内存定时器

活动会创建、修改和禁用，应用也会重启或扩容。批量轮询以数据库为事实来源，重启后仍能恢复判断，管理成本更低。

## 十五、下次自己实现的模板

例如“订单30分钟未支付自动关闭”：

1. 先确定状态和时间条件。
2. 编写一条幂等批量 SQL。
3. Mapper 返回影响行数。
4. Service 组织事务。
5. Scheduler 只负责定时触发 Service。
6. 间隔放进 YAML。
7. 分别测试 Scheduler、Service、真实 SQL。

SQL 可以是：

```sql
UPDATE orders
SET status = 'CLOSED'
WHERE status = 'PENDING'
  AND created_at <= NOW(3) - INTERVAL 30 MINUTE;
```

结构仍然相同：

```text
什么时候执行 → Scheduler
执行哪些规则 → Service
怎样修改数据 → Mapper
最终事实在哪 → MySQL
```

## 十六、最后再走一遍

```text
1. Spring Boot启动并扫描配置类
2. @EnableScheduling开启调度
3. Spring创建@Component Scheduler
4. 注入ActivityStatusServiceImpl
5. 读取application.yaml中的间隔
6. 首次延迟结束，Spring调用任务方法
7. Scheduler调用Service
8. Spring事务代理开启事务
9. Mapper批量执行SCHEDULED → RUNNING
10. Mapper批量执行SCHEDULED/RUNNING → ENDED
11. Service取得两条SQL的影响行数
12. 方法正常结束，事务提交
13. MySQL中的status真正改变
14. 查询接口直接读取新状态
15. 本轮结束，等待30秒后进入下一轮
```

最应该记住的不是注解名称，而是：

> Spring 调度器解决“什么时候调用”，Service 解决“这一轮做什么”，Mapper 解决“数据库怎样修改”，MySQL 保存所有调用方都能看到的最终状态。

