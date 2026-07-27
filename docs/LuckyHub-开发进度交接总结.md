# LuckyHub 开发进度交接总结

> 更新时间：2026-07-28  
> 用途：压缩聊天上下文，供下一次开发开始时读取。

## 明天如何继续

新会话开始时直接告诉 Codex：

```text
请先读取 docs/LuckyHub-开发进度交接总结.md，
检查当前 Git 状态，然后根据“下一步任务”继续。
```

开始开发前应执行：

```powershell
git status --short
git log -5 --oneline
```

当前约定是在现有项目和当前 `master` 分支直接开发，不创建 Git Worktree 隔离工作区。

---

## 一、项目当前状态

- 项目目录：`E:\ANotes\software\luckyhub`
- Java：17
- Spring Boot：4.1.0
- 数据库：MySQL 8.4
- MyBatis-Plus：负责数据库访问
- Redis：项目已连接，但活动管理模块目前不使用 Redis
- 数据库迁移：Flyway
- Git 分支：`master`
- 远程分支：`origin/master`
- 本总结创建前的功能基线提交：`d34e515 docs: explain activity status scheduler`
- 当前本地代码已经推送到 GitHub
- 生成本总结前，Git 工作区干净

项目没有 `mvnw.cmd`，不要直接依赖系统默认 Maven/JDK。统一通过脚本执行：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 test
```

该脚本会使用项目需要的 Java 17。直接运行 `mvn` 可能使用 Java 8 并导致构建失败。

---

## 二、已经完成的功能

### 1. 认证、授权与 RBAC

项目已经具备用户认证、JWT 和基于权限码的接口授权基础。

相关教学文档：

```text
docs/LuckyHub-认证授权与RBAC教学文档.md
```

### 2. 奖品管理模块

已完成：

- 创建奖品
- 查询奖品详情和列表
- 修改奖品
- 使用禁用代替物理删除
- 奖品管理权限
- DTO、VO、Entity、Enum、Service、Mapper、Controller 分层
- 奖品图片 URL 保存到 MySQL

主要包：

```text
src/main/java/com/dongqh/luckyhub/prize
```

接口文档：

```text
docs/prize-management-api.md
```

### 3. 阿里云 OSS 图片上传

已完成：

- 接收 `MultipartFile`
- 图片类型和大小校验
- 生成 Object Key
- 上传阿里云 OSS
- 拼接公开访问 URL
- 把公开 URL 保存进奖品数据
- OSS 配置和异常处理

主要配置位于：

```text
src/main/resources/application.yaml
```

环境变量主要包括：

```properties
OSS_ENABLED=true
OSS_REGION=
OSS_ENDPOINT=
OSS_BUCKET=
OSS_ACCESS_KEY_ID=
OSS_ACCESS_KEY_SECRET=
OSS_PUBLIC_BASE_URL=
```

真实密钥只能保存在本地 `.env` 或部署环境变量中，不能提交到 Git。

详细教学文档：

```text
docs/LuckyHub-OSS图片上传实现详解.md
```

### 4. 活动管理模块

主要包：

```text
src/main/java/com/dongqh/luckyhub/activity
```

已经完成：

- 创建活动
- 活动详情查询
- 活动分页查询
- 修改活动
- 发布活动
- 禁用活动
- 恢复活动
- 给活动添加奖品
- 查询活动奖品
- 修改活动奖品配置
- 移除活动奖品关联
- 活动权限控制
- 状态持久化
- 定时更新活动状态

活动状态：

```text
DRAFT      草稿
SCHEDULED  已发布，等待开始
RUNNING    进行中
ENDED      已结束
DISABLED   已禁用
```

核心状态规则：

```text
创建 → DRAFT

发布DRAFT：
开始时间未到 → SCHEDULED
已经到开始时间且未结束 → RUNNING

定时任务：
SCHEDULED → RUNNING
SCHEDULED/RUNNING → ENDED

DRAFT/SCHEDULED/RUNNING/ENDED → 可禁用为DISABLED
DISABLED → 恢复为DRAFT
```

补充规则：

- `DISABLED` 只能查询或恢复，不能直接修改；
- `ENDED` 不能直接修改；
- 已结束活动如需重用：禁用 → 恢复为草稿 → 修改 → 重新发布；
- 恢复只回到 `DRAFT`，不会立即重新运行；
- `SCHEDULED` 和 `RUNNING` 在有权限时可以修改；
- 重新启用和发布前，需要保证关联奖品处于启用状态；
- 活动奖品配置保存权重、总库存、剩余库存和排序；
- 修改库存时保留已经消耗的数量。

### 5. 活动权限

已经加入以下权限码：

```text
activity:create
activity:read
activity:update
activity:publish
activity:disable
activity:restore
activity:prize:manage
```

权限通过 Flyway 的 `V4` 数据库迁移创建，并授予管理员角色。

---

## 三、活动状态定时任务实现

相关类：

```text
activity/config/ActivitySchedulingConfiguration.java
activity/scheduler/ActivityStatusScheduler.java
activity/service/ActivityStatusService.java
activity/service/ActivityStatusRefreshResult.java
activity/service/impl/ActivityStatusServiceImpl.java
activity/mapper/MarketingActivityMapper.java
```

完整调用链：

```text
Spring Boot启动
    ↓
@EnableScheduling开启调度
    ↓
Spring登记@Scheduled方法
    ↓
ActivityStatusScheduler
    ↓
ActivityStatusServiceImpl（事务）
    ↓
MarketingActivityMapper
    ↓
MySQL批量UPDATE并持久化状态
```

默认配置：

```yaml
luckyhub:
  activity:
    status-refresh-interval: 30000
    status-refresh-initial-delay: 0
```

可通过环境变量覆盖：

```properties
ACTIVITY_STATUS_REFRESH_INTERVAL=30000
ACTIVITY_STATUS_REFRESH_INITIAL_DELAY=0
```

两条核心 SQL 的业务含义：

```text
status = SCHEDULED
且 start_time <= NOW(3)
且 end_time > NOW(3)
→ RUNNING
```

```text
status属于SCHEDULED或RUNNING
且 end_time <= NOW(3)
→ ENDED
```

结束 SQL 同时处理 `SCHEDULED`，是为了让应用重启后修复停机期间已经错过整个活动时间段的数据。

详细教学文档：

```text
docs/LuckyHub-活动状态定时任务实现详解.md
```

---

## 四、活动管理接口

```text
POST   /api/admin/activities
GET    /api/admin/activities/{id}
GET    /api/admin/activities
PUT    /api/admin/activities/{id}
PATCH  /api/admin/activities/{id}/publish
PATCH  /api/admin/activities/{id}/disable
PATCH  /api/admin/activities/{id}/restore

POST   /api/admin/activities/{activityId}/prizes
GET    /api/admin/activities/{activityId}/prizes
PUT    /api/admin/activities/{activityId}/prizes/{prizeId}
DELETE /api/admin/activities/{activityId}/prizes/{prizeId}
```

相关文档：

```text
docs/activity-management-api.md
docs/LuckyHub-活动管理实现详解.md
```

---

## 五、测试与验证状态

活动模块完成时运行过完整测试：

```text
Tests run: 80
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

定时任务文档完成后，重新运行了定时任务相关测试：

```text
ActivityMapperTests
ActivityStatusSchedulerTests
ActivityStatusServiceTests

Tests run: 4
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

相关测试文件：

```text
src/test/java/com/dongqh/luckyhub/activity/mapper/ActivityMapperTests.java
src/test/java/com/dongqh/luckyhub/activity/scheduler/ActivityStatusSchedulerTests.java
src/test/java/com/dongqh/luckyhub/activity/service/ActivityStatusServiceTests.java
```

下一次修改生产代码后，必须重新运行完整测试，不能只依赖这里记录的历史结果。

---

## 六、最近的重要提交

```text
d34e515 docs: explain activity status scheduler
2846e15 test: verify activity status SQL
0f21d24 fix: register activity mappers
441a5a1 docs: explain activity management flow
f7c1b3c feat: configure activity prizes
962773f feat: expose activity lifecycle API
9750c88 feat: manage activity lifecycle
bbc4fae feat: persist scheduled activity statuses
c89ba17 feat: add activity persistence contracts
```

---

## 七、下一步任务

奖品管理、OSS 上传和活动管理已经形成完整的后台配置链路。

建议下一阶段进入“抽奖核心流程”，但尚未开始设计和实现。开始编码前需要先确认：

1. 用户如何参加活动；
2. 是否需要登录以及使用哪个用户 ID；
3. 每日抽奖次数如何计算；
4. 活动总次数是否有限制；
5. 权重算法如何选择奖品；
6. 未中奖是否作为一种奖品或独立结果；
7. 如何保证并发扣减库存不超卖；
8. 抽奖订单何时创建，失败如何记录；
9. 实物奖品、优惠券、积分等奖品怎样发放；
10. 是否使用 Redis 做频控、库存预扣或幂等；
11. 如何防止重复请求；
12. 抽奖结果和用户中奖记录需要哪些查询接口。

建议明天先完成：

```text
抽奖核心领域设计
```

设计确认后再按顺序实现：

```text
资格校验
→ 抽奖次数限制
→ 权重抽奖
→ 并发库存扣减
→ 抽奖订单
→ 奖品发放
→ 中奖记录查询
→ 权限和测试
→ 执行流程教学文档
```

不要在规则未确认前直接编写抽奖算法和库存代码。

---

## 八、安全提醒

`docs/AccessKey.csv` 当前是本地未跟踪/被忽略的文件，没有提交到 Git。

如果文件中包含真实阿里云 AccessKey：

- 不要执行 `git add -f`；
- 不要复制进 README 或教学文档；
- 确认密钥已经安全保存后，建议从项目目录移走；
- 如果密钥曾经上传到公开位置，应立即在阿里云控制台禁用并轮换。

---

## 九、明天恢复上下文时的最小读取清单

第一步先读本文件：

```text
docs/LuckyHub-开发进度交接总结.md
```

准备继续抽奖模块时，再按需读取：

```text
docs/LuckyHub-活动管理实现详解.md
docs/activity-management-api.md
src/main/java/com/dongqh/luckyhub/activity
src/main/java/com/dongqh/luckyhub/prize
src/main/resources/db/migration/V1__create_luckyhub_schema.sql
```

不需要一开始重新读取所有长篇 OSS 和定时任务教学文档。
