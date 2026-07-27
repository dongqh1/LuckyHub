# LuckyHub 活动管理：从一个 POST 请求开始

> 本文按照程序真实执行顺序讲解。
>
> 我们从 `POST /api/admin/activities` 开始，跟踪一个活动怎样进入 MySQL；然后继续跟踪配置奖品、发布活动、定时变为 `RUNNING` 和 `ENDED`、禁用、恢复和重新发布。

---

## 0. 先看完整业务流程

```text
创建活动
  ↓
DRAFT
  ↓ 配置奖品、权重和库存
发布
  ├─开始时间未到 → SCHEDULED
  └─开始时间已到 → RUNNING
                         ↓ 定时任务
                       ENDED

任意活动状态可以由管理员禁用
  ↓
DISABLED
  ↓ 恢复
DRAFT
```

本模块只使用 MySQL，不使用 Redis。

Redis 应该在后面的高并发抽奖、库存预扣和次数限制中使用。后台管理操作频率低，而且需要强一致的数据，所以直接使用 MySQL 更清楚。

---

# 第一段：创建活动

## 1. 从 Postman 发送 POST

请求：

```http
POST http://localhost:8080/api/admin/activities
Authorization: Bearer 你的Token
Content-Type: application/json
```

Body：

```json
{
  "activityName": "八月幸运抽奖",
  "description": "八月会员抽奖活动",
  "startTime": "2026-08-01T10:00:00",
  "endTime": "2026-08-10T22:00:00",
  "dailyLimit": 3
}
```

这次请求的数据变化是：

```text
HTTP JSON
→ CreateActivityCommand
→ MarketingActivity
→ INSERT SQL
→ marketing_activity
→ ActivityView
→ JSON 响应
```

接下来逐步执行。

---

## 2. 请求先经过身份认证

请求还没有进入活动 Controller，首先经过：

```text
AuthenticationFilter
```

现在缺少什么？

```text
系统还不知道请求是谁发的。
```

Filter 从请求头读取：

```http
Authorization: Bearer eyJ...
```

验证 JWT 和 Redis Session 后创建：

```java
LoginPrincipal principal = new LoginPrincipal(
        payload.userId(),
        payload.username(),
        payload.sessionId()
);
```

然后放入：

```java
LoginContext.set(principal);
```

为什么活动创建需要这个信息？

因为数据库字段：

```text
marketing_activity.created_by
```

必须记录真正的登录用户，不能让客户端在 JSON 中随便填写。

所以请求 JSON 里没有 `createdBy`。稍后 Service 从 `LoginContext` 读取它。

---

## 3. 接着检查活动创建权限

身份通过后，请求经过：

```text
PermissionInterceptor
```

Controller 方法声明：

```java
@RequirePermission(PermissionCodes.ACTIVITY_CREATE)
```

常量内容：

```java
public static final String ACTIVITY_CREATE =
        "activity:create";
```

拦截器会读取当前用户拥有的权限。如果没有 `activity:create`，请求在进入 Controller 前就被拒绝。

Flyway V4 创建了七项活动权限，并授予 `ADMIN`：

```text
activity:create
activity:read
activity:update
activity:publish
activity:disable
activity:restore
activity:prize:manage
```

为什么使用常量而不是直接写字符串？

```java
@RequirePermission("activty:create")
```

手写字符串很容易拼错，而且编译器不能发现。使用 `PermissionCodes.ACTIVITY_CREATE` 后，常量名写错会直接编译失败。

---

## 4. ActivityController 接住 HTTP 请求

权限通过后，Spring 找到：

```java
@RestController
@RequestMapping("/api/admin/activities")
public class ActivityController {

    private final ActivityService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.ACTIVITY_CREATE)
    public ApiResponse<ActivityView> create(
            @Valid @RequestBody CreateActivityCommand command
    ) {
        return ApiResponse.success(service.create(command));
    }
}
```

### `@RestController`

表示这个类处理 HTTP 请求，并把返回对象序列化为 JSON。

### `@RequestMapping`

定义类的公共路径：

```text
/api/admin/activities
```

### `@PostMapping`

表示这个方法处理 POST 请求。类路径和方法路径合并后得到：

```text
POST /api/admin/activities
```

### `@RequestBody`

告诉 Spring：

```text
读取 HTTP Body 中的 JSON，
把它转换成 CreateActivityCommand。
```

这个转换由 Jackson 完成。

### `@Valid`

JSON 转换成功后，立即执行 DTO 上的 Jakarta Validation 规则。

### `@ResponseStatus(HttpStatus.CREATED)`

创建成功返回：

```text
HTTP 201 Created
```

### 为什么 Controller 只有一行

Controller 的职责是：

```text
HTTP 输入 → 调用业务 Service → HTTP 输出
```

时间比较、状态流转、数据库操作不应该写在 Controller 中，否则其他入口无法复用，而且 Controller 会越来越难测试。

---

## 5. 为什么创建 CreateActivityCommand

Spring 需要一个明确类型承接客户端输入：

```java
public record CreateActivityCommand(
        @NotBlank
        @Size(max = 100)
        String activityName,

        @Size(max = 1000)
        String description,

        @NotNull
        LocalDateTime startTime,

        @NotNull
        LocalDateTime endTime,

        @NotNull
        @Positive
        Integer dailyLimit
) {
}
```

现在缺少什么？

```text
客户端输入还没有格式边界。
```

DTO 解决的是“请求格式是否正确”：

```text
activityName 是否为空？
长度是否超过 100？
时间字段是否提供？
dailyLimit 是否大于零？
```

各注解含义：

```text
@NotBlank      不能是 null、空字符串或只有空格
@NotNull       不能是 null
@Positive      必须大于 0
@Size(max=100) 字符串最多 100 个字符
```

为什么 DTO 不直接使用 Entity？

Entity 表示数据库行，包含：

```text
id
status
createdBy
createdAt
updatedAt
```

这些字段不能让客户端控制。

DTO 只暴露允许客户端提交的字段，这叫输入边界。

---

## 6. 参数校验和业务校验的区别

DTO 可以检查：

```text
startTime 不为空
endTime 不为空
```

但它不能只靠单字段注解判断：

```text
endTime 是否晚于 startTime
```

因为这需要比较两个字段，所以进入 Service 后执行：

```java
validateTimeRange(command.startTime(), command.endTime());
```

方法：

```java
private void validateTimeRange(
        LocalDateTime startTime,
        LocalDateTime endTime
) {
    if (startTime == null
            || endTime == null
            || !endTime.isAfter(startTime)) {
        throw new BusinessException(
                ActivityErrorCode.ACTIVITY_TIME_INVALID
        );
    }
}
```

可以这样区分：

```text
DTO 校验：单个输入字段的格式
Service 校验：多个字段或数据库数据共同决定的业务规则
```

时间非法时抛出 `BusinessException`，全局异常处理器转换成统一错误 JSON。

---

## 7. Service 为什么要创建 MarketingActivity

Controller 调用：

```java
service.create(command)
```

核心代码：

```java
@Transactional
public ActivityView create(CreateActivityCommand command) {
    validateTimeRange(command.startTime(), command.endTime());

    MarketingActivity activity = new MarketingActivity();

    apply(
            activity,
            command.activityName(),
            command.description(),
            command.startTime(),
            command.endTime(),
            command.dailyLimit()
    );

    activity.setStatus(ActivityStatus.DRAFT);
    activity.setCreatedBy(LoginContext.require().userId());

    activityMapper.insert(activity);

    return toView(activity);
}
```

### 为什么状态固定为 DRAFT

客户端不能创建时直接提交：

```text
status = RUNNING
```

因为活动还没有配置奖品、权重和库存。

所以系统强制：

```java
activity.setStatus(ActivityStatus.DRAFT);
```

规范流程必须是：

```text
创建草稿
→ 配置奖品
→ 发布前校验
→ 正式发布
```

### createdBy 从哪里来

```java
activity.setCreatedBy(
        LoginContext.require().userId()
);
```

`LoginContext` 是本次请求的 ThreadLocal 登录上下文。

如果当前用户 ID 是 9，数据库最终保存：

```text
created_by = 9
```

### 为什么要 trim

`apply` 中：

```java
activity.setActivityName(activityName.trim());
```

用户输入：

```text
"  八月幸运抽奖  "
```

保存为：

```text
"八月幸运抽奖"
```

避免数据库出现看起来相同、实际包含多余空格的名称。

---

## 8. Entity 怎样映射数据库

`MarketingActivity`：

```java
@TableName("marketing_activity")
public class MarketingActivity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String activityName;
    private String description;
    private ActivityStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer dailyLimit;
    private Long createdBy;
}
```

现在缺少什么？

```text
Java 对象需要知道对应哪张表、哪个主键。
```

`@TableName` 指定：

```text
marketing_activity
```

`@TableId(type = IdType.AUTO)` 表示 MySQL 自增主键。

配置：

```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
```

会自动完成：

```text
activity_name ↔ activityName
start_time    ↔ startTime
daily_limit   ↔ dailyLimit
created_by    ↔ createdBy
```

---

## 9. Mapper 怎样执行 INSERT

Mapper：

```java
public interface MarketingActivityMapper
        extends BaseMapper<MarketingActivity> {
}
```

为什么接口里面没有写 `insert`？

因为 MyBatis-Plus 的 `BaseMapper` 已经提供：

```java
insert(entity)
selectById(id)
selectPage(page, wrapper)
updateById(entity)
deleteById(id)
```

Service 调用：

```java
activityMapper.insert(activity);
```

MyBatis-Plus 生成类似 SQL：

```sql
INSERT INTO marketing_activity (
    activity_name,
    description,
    status,
    start_time,
    end_time,
    daily_limit,
    created_by
) VALUES (?, ?, ?, ?, ?, ?, ?);
```

参数来自 Entity。

数据库自增 ID 会回填到：

```java
activity.getId()
```

---

## 10. 为什么返回 ActivityView

数据库操作成功后：

```java
return toView(activity);
```

`ActivityView`：

```java
public record ActivityView(
        Long id,
        String activityName,
        String description,
        ActivityStatus status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer dailyLimit,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
```

VO 是输出边界。

```text
DTO：客户端 → 系统
Entity：Java ↔ 数据库
VO：系统 → 客户端
```

Controller 使用：

```java
ApiResponse.success(service.create(command))
```

最终响应类似：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "activityName": "八月幸运抽奖",
    "description": "八月会员抽奖活动",
    "status": "DRAFT",
    "startTime": "2026-08-01T10:00:00",
    "endTime": "2026-08-10T22:00:00",
    "dailyLimit": 3,
    "createdBy": 9
  }
}
```

第一个 POST 请求到这里结束。

---

# 第二段：给活动添加奖品

## 11. 为什么活动和奖品要用关联表

一个活动可以有多个奖品，一个奖品也可以用于多个活动。

因此不能把 `prize_id` 直接放在活动表中，而是使用：

```text
marketing_activity_prize
```

它除了保存关联，还保存该活动自己的配置：

```text
weight
total_stock
remaining_stock
sort_order
```

同一个奖品在不同活动中的库存和权重可以不同。

---

## 12. 添加奖品请求

```http
POST /api/admin/activities/1/prizes
Authorization: Bearer 你的Token
Content-Type: application/json
```

```json
{
  "prizeId": 7,
  "weight": 20,
  "totalStock": 100,
  "sortOrder": 1
}
```

执行链：

```text
ActivityPrizeController
→ AddActivityPrizeCommand
→ ActivityPrizeServiceImpl
→ 检查活动
→ 检查奖品
→ 检查重复关系
→ MarketingActivityPrize
→ INSERT
→ ActivityPrizeView
```

---

## 13. 为什么先检查活动状态

Service 第一项检查：

```java
requireConfigurableActivity(activityId);
```

规则：

```java
if (status == DISABLED || status == ENDED) {
    throw ACTIVITY_STATE_CONFLICT;
}
```

原因：

- `DISABLED` 表示管理员已经关闭配置；
- `ENDED` 是历史活动，不能直接修改；
- 它们要先经过恢复流程回到草稿。

`DRAFT`、`SCHEDULED` 和 `RUNNING` 允许有权限的管理员配置。

---

## 14. 为什么必须检查奖品是否启用

```java
MarketingPrize prize =
        requireEnabledPrize(command.prizeId());
```

如果奖品不存在：

```text
42004 活动奖品不存在
```

如果奖品已禁用：

```text
42007 活动包含已禁用奖品
```

这是活动模块和奖品模块之间的重要边界：

```text
被禁用的奖品不能进入新的活动配置。
```

---

## 15. 为什么检查重复关系

```java
if (findRelation(activityId, prizeId) != null) {
    throw ACTIVITY_PRIZE_DUPLICATE;
}
```

业务代码先返回友好错误。

数据库还有唯一索引：

```sql
UNIQUE KEY uk_activity_prize (
    activity_id,
    prize_id
)
```

为什么两层都要？

```text
Service 校验：提供明确业务错误
数据库唯一索引：防止并发请求同时通过校验
```

数据库是最后一道防线。

---

## 16. 为什么 remainingStock 等于 totalStock

新增关系：

```java
relation.setTotalStock(command.totalStock());
relation.setRemainingStock(command.totalStock());
```

刚加入活动时还没有抽奖消耗，所以：

```text
剩余库存 = 总库存
```

例如：

```text
totalStock = 100
remainingStock = 100
```

以后抽出 20 个：

```text
totalStock = 100
remainingStock = 80
```

---

# 第三段：修改活动奖品库存

## 17. 为什么不能直接重置 remainingStock

请求：

```http
PUT /api/admin/activities/1/prizes/7
```

```json
{
  "weight": 30,
  "totalStock": 150,
  "sortOrder": 2
}
```

原数据：

```text
totalStock = 100
remainingStock = 80
```

说明已经消耗：

```text
100 - 80 = 20
```

如果把总库存改成 150 后直接设置：

```text
remainingStock = 150
```

之前消耗的 20 个就被错误恢复了。

正确代码：

```java
int consumedStock =
        relation.getTotalStock()
                - relation.getRemainingStock();

if (command.totalStock() < consumedStock) {
    throw ACTIVITY_STOCK_BELOW_CONSUMED;
}

relation.setTotalStock(command.totalStock());
relation.setRemainingStock(
        command.totalStock() - consumedStock
);
```

结果：

```text
新总库存 = 150
已消耗 = 20
新剩余库存 = 130
```

这段逻辑保护已经发生的历史消耗。

---

# 第四段：发布活动

## 18. 发布请求

```http
PATCH /api/admin/activities/1/publish
Authorization: Bearer 你的Token
```

发布不是简单修改一个字段。它是活动正式生效前的总检查。

执行链：

```text
ActivityController.publish
→ ActivityServiceImpl.publish
→ 检查 DRAFT
→ 检查时间
→ 查询全部活动奖品
→ 查询奖品当前状态
→ 检查权重与库存
→ 决定 SCHEDULED 或 RUNNING
→ UPDATE marketing_activity
```

---

## 19. 为什么只有 DRAFT 可以发布

```java
if (activity.getStatus() != ActivityStatus.DRAFT) {
    throw ACTIVITY_STATE_CONFLICT;
}
```

防止：

- 重复发布；
- 禁用后绕过恢复流程直接发布；
- 已结束活动直接重新开始。

规范的重新启用流程：

```text
DISABLED
→ restore
→ DRAFT
→ 修改时间和奖品
→ publish
```

---

## 20. 发布前检查了什么

### 至少一个奖品

```java
if (relations.isEmpty()) {
    throw ACTIVITY_HAS_NO_PRIZE;
}
```

### 所有奖品必须启用

Service 批量查询奖品，并按 ID 建立 Map：

```java
Map<Long, MarketingPrize> prizes =
        prizeMapper.selectBatchIds(prizeIds)
                .stream()
                .collect(toMap(
                        MarketingPrize::getId,
                        identity()
                ));
```

批量查询避免循环里一条一条查询数据库形成 N+1 问题。

### 权重和库存合法

```text
weight > 0
totalStock >= 0
0 <= remainingStock <= totalStock
```

### 至少一个奖品有库存

```java
if (relations.stream().noneMatch(
        relation -> relation.getRemainingStock() > 0
)) {
    throw ACTIVITY_HAS_NO_AVAILABLE_STOCK;
}
```

所有检查成功后才改变状态。

`@Transactional` 保证校验和状态更新属于同一个业务事务。

---

## 21. 发布后为什么有两种状态

```java
private ActivityStatus resolvePublishedStatus(
        LocalDateTime startTime
) {
    return now().isBefore(startTime)
            ? ActivityStatus.SCHEDULED
            : ActivityStatus.RUNNING;
}
```

例如当前时间：

```text
2026-08-01 09:00:00
```

活动开始时间：

```text
2026-08-01 10:00:00
```

发布后保存：

```text
SCHEDULED
```

如果当前已经是 11:00，但结束时间还没到，则保存：

```text
RUNNING
```

这里的状态会真正写入数据库，不是临时返回值。

---

# 第五段：数据库如何自动变成 RUNNING 和 ENDED

## 22. 不是查询时更新

查询接口只执行 SELECT。

我们没有这样写：

```text
GET 查询
→ 发现过期
→ UPDATE
```

因为查询不应该产生写操作，而且没人查询时状态也不会变化。

真正负责更新的是：

```text
ActivityStatusScheduler
```

---

## 23. 如何开启 Spring 定时任务

配置类：

```java
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ActivitySchedulingConfiguration {
}
```

`@EnableScheduling` 告诉 Spring：

```text
扫描并执行带有 @Scheduled 的方法。
```

如果没有这个注解，即使方法写了 `@Scheduled` 也不会自动运行。

---

## 24. Scheduler 每30秒触发

```java
@Component
public class ActivityStatusScheduler {

    private final ActivityStatusService service;

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

### `fixedDelay`

上一次任务执行完成后，等待 30 秒，再执行下一次。

### `initialDelay = 0`

应用启动后立即执行一次，用来修复停机期间错过的活动状态。

环境变量可以调整：

```properties
ACTIVITY_STATUS_REFRESH_INTERVAL=30000
ACTIVITY_STATUS_REFRESH_INITIAL_DELAY=0
```

单位是毫秒。

---

## 25. Scheduler 为什么不直接写 SQL

Scheduler 只做：

```java
service.refreshStatuses();
```

它的职责是“什么时候触发”。

Service 的职责是“按什么业务顺序执行”：

```java
int runningCount =
        mapper.promoteScheduledToRunning();

int endedCount =
        mapper.finishExpiredActivities();
```

Mapper 的职责是“具体怎样访问数据库”。

分开后可以单独测试：

- Scheduler 有没有调用 Service；
- Service 有没有按顺序调用两条 SQL；
- Mapper SQL 是否使用正确状态和时间。

---

## 26. SCHEDULED 怎样变成 RUNNING

Mapper：

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

逐项解释：

```text
status = SCHEDULED
```

只处理已经发布、正在等待开始的活动。

```text
start_time <= NOW(3)
```

开始时间已经到达。

```text
end_time > NOW(3)
```

结束时间还没有到。

满足全部条件后：

```text
status = RUNNING
```

这是一次批量 UPDATE。无论有 1 个还是 100 个到期活动，都不需要 Java 逐行加载。

---

## 27. RUNNING 怎样变成 ENDED

```java
@Update("""
    UPDATE marketing_activity
    SET status = 'ENDED'
    WHERE status IN ('SCHEDULED', 'RUNNING')
      AND end_time <= NOW(3)
    """)
int finishExpiredActivities();
```

为什么同时包含 `SCHEDULED`？

考虑：

```text
活动开始前服务器关闭
→ 开始时间到了
→ 结束时间也到了
→ 服务器重新启动
```

数据库还保留：

```text
SCHEDULED
```

启动后的第一次定时任务应该直接改成：

```text
ENDED
```

不能先变成 RUNNING 再等 30 秒。

---

## 28. 为什么使用 MySQL NOW(3)

SQL 使用：

```sql
NOW(3)
```

优点：

- 比较和字段处在同一个数据库时间体系；
- 多台应用服务器即使本机时间略有差异，结果仍一致；
- `DATETIME(3)` 和 `NOW(3)` 都保留毫秒精度。

状态最多延迟一个定时周期，即默认约 30 秒。

未来抽奖接口仍应同时检查：

```sql
status = 'RUNNING'
AND start_time <= NOW(3)
AND end_time > NOW(3)
```

这样即使定时任务还没来得及更新，也不能越界抽奖。

---

## 29. 多实例会不会重复修改

如果部署两台 LuckyHub，它们都可能执行定时任务。

第一台执行：

```text
SCHEDULED → RUNNING
```

第二台再执行时，WHERE 条件：

```text
status = SCHEDULED
```

已经匹配不到这条记录，因此不会重复修改。

这两条 SQL 是幂等的，当前不需要 Redis 分布式锁。

---

# 第六段：禁用与恢复

## 30. 禁用活动

请求：

```http
PATCH /api/admin/activities/1/disable
```

Service：

```java
if (activity.getStatus() == ActivityStatus.DISABLED) {
    return;
}

activity.setStatus(ActivityStatus.DISABLED);
activityMapper.updateById(activity);
```

重复禁用直接返回成功，这叫幂等：

```text
执行一次和执行多次，最终状态相同。
```

禁用后：

- 可以查询；
- 可以恢复；
- 不能普通修改；
- 不能配置活动奖品；
- 不能直接发布。

---

## 31. 恢复为什么回到 DRAFT

请求：

```http
PATCH /api/admin/activities/1/restore
```

核心代码：

```java
if (activity.getStatus() != ActivityStatus.DISABLED) {
    throw ACTIVITY_STATE_CONFLICT;
}

activity.setStatus(ActivityStatus.DRAFT);
activityMapper.updateById(activity);
```

为什么不直接恢复到 RUNNING？

因为禁用期间：

- 原活动时间可能已经过期；
- 关联奖品可能已被禁用；
- 库存和权重可能需要重新检查。

所以恢复后必须：

```text
DRAFT
→ 修改时间
→ 检查或替换奖品
→ 重新发布
```

发布方法会重新执行全部校验。

---

# 第七段：分页查询

## 32. ActivityQuery 是怎样接收 URL 参数的

请求：

```http
GET /api/admin/activities?name=八月&status=SCHEDULED&page=1&size=20
```

Controller：

```java
public ApiResponse<PageResponse<ActivityView>> page(
        @Valid @ModelAttribute ActivityQuery query
) {
    return ApiResponse.success(service.page(query));
}
```

`@ModelAttribute` 把 URL 查询参数绑定到普通 Java 对象。

默认值：

```text
page = 1
size = 20
```

限制：

```text
page >= 1
1 <= size <= 100
```

Service 构造条件：

```java
new LambdaQueryWrapper<MarketingActivity>()
    .like(name != null,
          MarketingActivity::getActivityName,
          name)
    .eq(status != null,
        MarketingActivity::getStatus,
        status)
    .orderByDesc(MarketingActivity::getCreatedAt)
    .orderByDesc(MarketingActivity::getId);
```

条件参数为空时不添加对应 SQL 条件。

---

# 第八段：错误如何返回

## 33. ActivityErrorCode

活动模块使用独立错误码：

```text
42001 活动不存在
42002 活动时间非法
42003 当前状态不允许操作
42004 活动奖品不存在
42005 重复关联奖品
42006 没有配置奖品
42007 包含禁用奖品
42008 没有可用库存
42009 总库存小于已消耗库存
```

Service 抛出：

```java
throw new BusinessException(
        ActivityErrorCode.ACTIVITY_NOT_FOUND
);
```

`GlobalExceptionHandler` 统一转换为：

```json
{
  "code": 42001,
  "message": "活动不存在",
  "requestId": "...",
  "timestamp": 178...
}
```

Controller 不需要重复写 `try/catch`。

---

# 第九段：一次完整操作示例

## 34. 从创建到结束

### 第一步：创建

```http
POST /api/admin/activities
```

得到：

```text
status = DRAFT
```

### 第二步：添加奖品

```http
POST /api/admin/activities/1/prizes
```

添加一个或多个启用奖品。

### 第三步：发布

```http
PATCH /api/admin/activities/1/publish
```

如果开始时间未到：

```text
status = SCHEDULED
```

### 第四步：定时开始

开始时间到达后，Scheduler 执行：

```text
SCHEDULED → RUNNING
```

数据库真正发生 UPDATE。

### 第五步：定时结束

结束时间到达后：

```text
RUNNING → ENDED
```

数据库中直接查询也能看到 `ENDED`。

### 第六步：重新启用

```text
ENDED
→ PATCH disable
→ DISABLED
→ PATCH restore
→ DRAFT
→ PUT 修改新时间
→ 检查奖品
→ PATCH publish
```

---

# 第十段：下次怎样自己写

## 35. 推荐实现顺序

### 1. 先画状态机

明确：

```text
有哪些状态？
谁触发变化？
哪些状态允许哪些操作？
```

### 2. 定义 API 契约

先确定路径、请求字段、响应和权限。

### 3. 定义 DTO 与 VO

```text
DTO 保护输入
VO 稳定输出
```

### 4. 定义 Entity 和 Mapper

对照真实数据库字段，不凭感觉命名。

### 5. 编写 Service 测试

先测试：

- 正常路径；
- 非法时间；
- 非法状态；
- 缺少奖品；
- 禁用奖品；
- 库存边界；
- 重复操作。

### 6. 实现业务 Service

Service 负责规则和事务，不负责 HTTP。

### 7. 编写 Controller 测试和 Controller

验证路由、JSON、状态码和权限。

### 8. 最后实现定时任务

把：

```text
什么时候触发
```

和：

```text
怎样更新数据库
```

分成 Scheduler、Service、Mapper 三层。

### 9. 全量回归

活动模块通过不代表整个项目没被影响，最后必须执行全部测试。

---

## 36. 用一条链路记住整个模块

```text
POST JSON
→ AuthenticationFilter
→ PermissionInterceptor
→ ActivityController
→ CreateActivityCommand
→ Jakarta Validation
→ ActivityServiceImpl
→ LoginContext
→ MarketingActivity
→ MarketingActivityMapper
→ MyBatis-Plus
→ MySQL
→ ActivityView
→ ApiResponse
→ JSON
```

时间状态链：

```text
@EnableScheduling
→ @Scheduled
→ ActivityStatusScheduler
→ ActivityStatusService
→ MarketingActivityMapper
→ UPDATE ... NOW(3)
→ SCHEDULED / RUNNING / ENDED 真正保存进数据库
```

最重要的思考方式：

```text
程序执行到这里缺少什么能力？
→ 这个能力应该由哪个类负责？
→ 它接收什么？
→ 它验证什么？
→ 它产生什么？
→ 输出交给下一步的谁？
```

掌握这套思路后，下一次实现优惠券活动、秒杀活动或签到活动时，你可以自己设计出清晰的 Controller、DTO、Service、Mapper、状态机和定时任务。

