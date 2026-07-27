# Activity Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement secured MySQL-backed activity and activity-prize management with persisted scheduled state transitions, complete tests, and a request-flow teaching document.

**Architecture:** Add a focused `activity` package following the existing prize module’s Controller/DTO/Service/Mapper/Entity/VO boundaries. Persist `DRAFT`, `SCHEDULED`, `RUNNING`, `ENDED`, and `DISABLED` in `marketing_activity.status`; a Spring scheduler calls two idempotent MySQL bulk updates every 30 seconds. Keep Redis outside this module.

**Tech Stack:** Java 17, Spring Boot 4.1, Spring MVC, Jakarta Validation, MyBatis-Plus 3.5.17, MySQL 8, Flyway, JUnit 5, Mockito, AssertJ, MockMvc.

## Global Constraints

- Run all commands with PowerShell on Windows.
- Read and write Chinese text as UTF-8.
- Do not add Redis activity caching or Redis inventory operations.
- Do not physically delete activities or prizes.
- `DISABLED` activities can only be queried or restored.
- `ENDED` activities must be disabled and restored before editing.
- Published activity configuration remains editable in `SCHEDULED` and `RUNNING` when the caller has permission.
- Status refresh uses MySQL `NOW(3)`, not JVM time.
- The scheduler interval defaults to 30 seconds.
- Follow TDD: observe each new test fail before adding its production implementation.

---

## File Map

Create production files under:

```text
src/main/java/com/dongqh/luckyhub/activity/
├─ controller/ActivityController.java
├─ controller/ActivityPrizeController.java
├─ dto/CreateActivityCommand.java
├─ dto/UpdateActivityCommand.java
├─ dto/ActivityQuery.java
├─ dto/AddActivityPrizeCommand.java
├─ dto/UpdateActivityPrizeCommand.java
├─ entity/MarketingActivity.java
├─ entity/MarketingActivityPrize.java
├─ enums/ActivityStatus.java
├─ enums/ActivityErrorCode.java
├─ mapper/MarketingActivityMapper.java
├─ mapper/MarketingActivityPrizeMapper.java
├─ config/ActivitySchedulingConfiguration.java
├─ scheduler/ActivityStatusScheduler.java
├─ service/ActivityService.java
├─ service/ActivityPrizeService.java
├─ service/ActivityStatusService.java
├─ service/ActivityStatusRefreshResult.java
├─ service/impl/ActivityServiceImpl.java
├─ service/impl/ActivityPrizeServiceImpl.java
├─ service/impl/ActivityStatusServiceImpl.java
├─ vo/ActivityView.java
└─ vo/ActivityPrizeView.java
```

Create or modify:

```text
src/main/resources/db/migration/V4__add_activity_management_permissions.sql
src/main/resources/application.yaml
src/main/java/com/dongqh/luckyhub/rbac/constant/PermissionCodes.java
src/test/java/com/dongqh/luckyhub/activity/...
src/test/java/com/dongqh/luckyhub/infrastructure/DatabaseSchemaMigrationTests.java
docs/activity-management-api.md
docs/LuckyHub-活动管理实现详解.md
README.md
```

---

### Task 1: Activity persistence contracts and RBAC migration

**Files:**

- Create: `src/main/java/com/dongqh/luckyhub/activity/enums/ActivityStatus.java`
- Create: `src/main/java/com/dongqh/luckyhub/activity/enums/ActivityErrorCode.java`
- Create: `src/main/java/com/dongqh/luckyhub/activity/entity/MarketingActivity.java`
- Create: `src/main/java/com/dongqh/luckyhub/activity/entity/MarketingActivityPrize.java`
- Create: `src/main/java/com/dongqh/luckyhub/activity/mapper/MarketingActivityMapper.java`
- Create: `src/main/java/com/dongqh/luckyhub/activity/mapper/MarketingActivityPrizeMapper.java`
- Create: `src/main/resources/db/migration/V4__add_activity_management_permissions.sql`
- Modify: `src/main/java/com/dongqh/luckyhub/rbac/constant/PermissionCodes.java`
- Modify: `src/test/java/com/dongqh/luckyhub/infrastructure/DatabaseSchemaMigrationTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/activity/ActivityPersistenceContractTests.java`

**Interfaces:**

- Produces: `ActivityStatus`, `ActivityErrorCode`, two MyBatis entities, and two `BaseMapper` types used by every later task.
- Produces permissions: `ACTIVITY_CREATE`, `ACTIVITY_READ`, `ACTIVITY_UPDATE`, `ACTIVITY_PUBLISH`, `ACTIVITY_DISABLE`, `ACTIVITY_RESTORE`, `ACTIVITY_PRIZE_MANAGE`.

- [ ] **Step 1: Write failing persistence and migration tests**

Create a reflection contract test that asserts:

```java
assertThat(ActivityStatus.values()).containsExactly(
        ActivityStatus.DRAFT,
        ActivityStatus.SCHEDULED,
        ActivityStatus.RUNNING,
        ActivityStatus.ENDED,
        ActivityStatus.DISABLED
);
assertThat(MarketingActivity.class.getAnnotation(TableName.class).value())
        .isEqualTo("marketing_activity");
assertThat(MarketingActivityPrize.class.getAnnotation(TableName.class).value())
        .isEqualTo("marketing_activity_prize");
```

Extend `DatabaseSchemaMigrationTests` with:

```java
private static final Set<String> ACTIVITY_PERMISSIONS = Set.of(
        "activity:create",
        "activity:read",
        "activity:update",
        "activity:publish",
        "activity:disable",
        "activity:restore",
        "activity:prize:manage"
);
```

Add a test that checks Flyway version `4`, all seven permission codes, and all seven ADMIN grants.

- [ ] **Step 2: Run tests and confirm RED**

Run:

```powershell
.\mvnw.cmd -Dtest=ActivityPersistenceContractTests,DatabaseSchemaMigrationTests test
```

Expected: test compilation fails because the activity contracts do not exist.

- [ ] **Step 3: Add enums, entities, mappers, constants, and migration**

Implement:

```java
public enum ActivityStatus {
    DRAFT, SCHEDULED, RUNNING, ENDED, DISABLED
}
```

Implement error codes starting at `42001`:

```text
42001 ACTIVITY_NOT_FOUND                  404
42002 ACTIVITY_TIME_INVALID               400
42003 ACTIVITY_STATE_CONFLICT             409
42004 ACTIVITY_PRIZE_NOT_FOUND            404
42005 ACTIVITY_PRIZE_DUPLICATE            409
42006 ACTIVITY_HAS_NO_PRIZE               409
42007 ACTIVITY_HAS_DISABLED_PRIZE          409
42008 ACTIVITY_HAS_NO_AVAILABLE_STOCK      409
42009 ACTIVITY_STOCK_BELOW_CONSUMED        409
```

Map `MarketingActivity` fields exactly to the V1 table:

```java
Long id;
String activityName;
String description;
ActivityStatus status;
LocalDateTime startTime;
LocalDateTime endTime;
Integer dailyLimit;
Long createdBy;
LocalDateTime createdAt;
LocalDateTime updatedAt;
```

Map `MarketingActivityPrize` fields:

```java
Long id;
Long activityId;
Long prizeId;
Integer weight;
Integer totalStock;
Integer remainingStock;
Integer sortOrder;
LocalDateTime createdAt;
LocalDateTime updatedAt;
```

Both mappers extend `BaseMapper<T>`.

Flyway V4 must use the same idempotent `INSERT ... SELECT ... WHERE NOT EXISTS` and ADMIN grant pattern as V3.

- [ ] **Step 4: Run tests and confirm GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=ActivityPersistenceContractTests,DatabaseSchemaMigrationTests test
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- src/main/java/com/dongqh/luckyhub/activity src/main/java/com/dongqh/luckyhub/rbac/constant/PermissionCodes.java src/main/resources/db/migration/V4__add_activity_management_permissions.sql src/test/java/com/dongqh/luckyhub/activity/ActivityPersistenceContractTests.java src/test/java/com/dongqh/luckyhub/infrastructure/DatabaseSchemaMigrationTests.java
git commit -m "feat: add activity persistence contracts"
```

---

### Task 2: Persist scheduled status transitions

**Files:**

- Modify: `src/main/java/com/dongqh/luckyhub/activity/mapper/MarketingActivityMapper.java`
- Create: `src/main/java/com/dongqh/luckyhub/activity/service/ActivityStatusService.java`
- Create: `src/main/java/com/dongqh/luckyhub/activity/service/ActivityStatusRefreshResult.java`
- Create: `src/main/java/com/dongqh/luckyhub/activity/service/impl/ActivityStatusServiceImpl.java`
- Create: `src/main/java/com/dongqh/luckyhub/activity/config/ActivitySchedulingConfiguration.java`
- Create: `src/main/java/com/dongqh/luckyhub/activity/scheduler/ActivityStatusScheduler.java`
- Modify: `src/main/resources/application.yaml`
- Test: `src/test/java/com/dongqh/luckyhub/activity/service/ActivityStatusServiceTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/activity/scheduler/ActivityStatusSchedulerTests.java`

**Interfaces:**

- `MarketingActivityMapper.promoteScheduledToRunning(): int`
- `MarketingActivityMapper.finishExpiredActivities(): int`
- `ActivityStatusService.refreshStatuses(): ActivityStatusRefreshResult`
- `ActivityStatusRefreshResult(int runningCount, int endedCount)`

- [ ] **Step 1: Write failing service and scheduler tests**

Service test:

```java
when(mapper.promoteScheduledToRunning()).thenReturn(2);
when(mapper.finishExpiredActivities()).thenReturn(3);

ActivityStatusRefreshResult result = service.refreshStatuses();

assertThat(result.runningCount()).isEqualTo(2);
assertThat(result.endedCount()).isEqualTo(3);
verify(mapper).promoteScheduledToRunning();
verify(mapper).finishExpiredActivities();
```

Scheduler test:

```java
scheduler.refreshActivityStatuses();
verify(service).refreshStatuses();
```

Also reflect on both mapper methods’ `@Update` values and assert they contain `NOW(3)`, `SCHEDULED`, `RUNNING`, and `ENDED`.

- [ ] **Step 2: Run tests and confirm RED**

```powershell
.\mvnw.cmd -Dtest=ActivityStatusServiceTests,ActivityStatusSchedulerTests test
```

Expected: compilation fails for missing status service and scheduler.

- [ ] **Step 3: Implement atomic mapper updates and scheduler**

Mapper methods:

```java
@Update("""
        UPDATE marketing_activity
        SET status = 'RUNNING'
        WHERE status = 'SCHEDULED'
          AND start_time <= NOW(3)
          AND end_time > NOW(3)
        """)
int promoteScheduledToRunning();

@Update("""
        UPDATE marketing_activity
        SET status = 'ENDED'
        WHERE status IN ('SCHEDULED', 'RUNNING')
          AND end_time <= NOW(3)
        """)
int finishExpiredActivities();
```

Scheduler:

```java
@Component
public class ActivityStatusScheduler {
    @Scheduled(
        fixedDelayString = "${luckyhub.activity.status-refresh-interval:30000}",
        initialDelayString = "${luckyhub.activity.status-refresh-initial-delay:0}"
    )
    public void refreshActivityStatuses() {
        service.refreshStatuses();
    }
}
```

Add `@EnableScheduling` on a focused activity scheduling configuration class or the application class. Add YAML defaults:

```yaml
luckyhub:
  activity:
    status-refresh-interval: ${ACTIVITY_STATUS_REFRESH_INTERVAL:30000}
    status-refresh-initial-delay: ${ACTIVITY_STATUS_REFRESH_INITIAL_DELAY:0}
```

- [ ] **Step 4: Run tests and confirm GREEN**

```powershell
.\mvnw.cmd -Dtest=ActivityStatusServiceTests,ActivityStatusSchedulerTests test
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- src/main/java/com/dongqh/luckyhub/activity src/main/resources/application.yaml src/test/java/com/dongqh/luckyhub/activity
git commit -m "feat: persist scheduled activity statuses"
```

---

### Task 3: Activity CRUD and lifecycle service

**Files:**

- Create: `src/main/java/com/dongqh/luckyhub/activity/dto/CreateActivityCommand.java`
- Create: `src/main/java/com/dongqh/luckyhub/activity/dto/UpdateActivityCommand.java`
- Create: `src/main/java/com/dongqh/luckyhub/activity/dto/ActivityQuery.java`
- Create: `src/main/java/com/dongqh/luckyhub/activity/vo/ActivityView.java`
- Create: `src/main/java/com/dongqh/luckyhub/activity/service/ActivityService.java`
- Create: `src/main/java/com/dongqh/luckyhub/activity/service/impl/ActivityServiceImpl.java`
- Test: `src/test/java/com/dongqh/luckyhub/activity/service/ActivityServiceTests.java`

**Interfaces:**

```java
ActivityView create(CreateActivityCommand command);
ActivityView getById(long id);
PageResponse<ActivityView> page(ActivityQuery query);
ActivityView update(long id, UpdateActivityCommand command);
ActivityView publish(long id);
void disable(long id);
ActivityView restore(long id);
```

- [ ] **Step 1: Write failing CRUD and lifecycle tests**

Use mocked mappers and a fixed `Clock` at `2026-08-01T09:00:00+08:00`.

Cover exact cases:

```text
create trims strings, stores DRAFT, and uses LoginContext userId
endTime <= startTime throws ACTIVITY_TIME_INVALID
get missing throws ACTIVITY_NOT_FOUND
page applies name/status/page/size
DRAFT update stays DRAFT
SCHEDULED update with future start stays SCHEDULED
RUNNING update with start in the past stays RUNNING
ENDED and DISABLED update throw ACTIVITY_STATE_CONFLICT
publish requires DRAFT, future end, relations, enabled prizes, and available stock
publish future start stores SCHEDULED
publish reached start stores RUNNING
disable is idempotent
restore only accepts DISABLED and stores DRAFT
```

Set and clear:

```java
LoginContext.set(new LoginPrincipal(9L, "admin", "session"));
...
LoginContext.clear();
```

- [ ] **Step 2: Run test and confirm RED**

```powershell
.\mvnw.cmd -Dtest=ActivityServiceTests test
```

Expected: compilation fails because DTO, VO, interface, and implementation are absent.

- [ ] **Step 3: Implement DTO validation and service rules**

DTO validation:

```java
@NotBlank @Size(max = 100) String activityName
@Size(max = 1000) String description
@NotNull LocalDateTime startTime
@NotNull LocalDateTime endTime
@NotNull @Positive Integer dailyLimit
```

`ActivityQuery` defaults:

```text
page = 1
size = 20
size maximum = 100
```

Use injected `Clock` so tests can control current time. Production constructor supplies `Clock.systemDefaultZone()`.

Publishing reads all activity-prize rows and all referenced `MarketingPrize` rows in the same transaction. Reject missing, disabled, invalid-weight, invalid-stock, or no-available-stock configurations before changing status.

- [ ] **Step 4: Run test and confirm GREEN**

```powershell
.\mvnw.cmd -Dtest=ActivityServiceTests test
```

Expected: all activity service tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- src/main/java/com/dongqh/luckyhub/activity src/test/java/com/dongqh/luckyhub/activity/service/ActivityServiceTests.java
git commit -m "feat: manage activity lifecycle"
```

---

### Task 4: Activity HTTP API

**Files:**

- Create: `src/main/java/com/dongqh/luckyhub/activity/controller/ActivityController.java`
- Test: `src/test/java/com/dongqh/luckyhub/activity/controller/ActivityControllerTests.java`

**Interfaces:**

Expose:

```text
POST  /api/admin/activities
GET   /api/admin/activities/{id}
GET   /api/admin/activities
PUT   /api/admin/activities/{id}
PATCH /api/admin/activities/{id}/publish
PATCH /api/admin/activities/{id}/disable
PATCH /api/admin/activities/{id}/restore
```

- [ ] **Step 1: Write failing MockMvc and permission tests**

Test success HTTP status and JSON for every route, invalid create returns code `30000`, and reflection checks exact permission annotations:

```text
create  → activity:create
get/page → activity:read
update → activity:update
publish → activity:publish
disable → activity:disable
restore → activity:restore
```

- [ ] **Step 2: Run test and confirm RED**

```powershell
.\mvnw.cmd -Dtest=ActivityControllerTests test
```

Expected: compilation fails because `ActivityController` is absent.

- [ ] **Step 3: Implement thin Controller**

Follow `PrizeController` conventions:

```java
@RestController
@RequestMapping("/api/admin/activities")
@Validated
```

Use `@Valid @RequestBody`, `@Valid @ModelAttribute`, `@ResponseStatus(HttpStatus.CREATED)`, `ApiResponse.success(...)`, and exact `@RequirePermission` constants. Keep business rules out of Controller.

- [ ] **Step 4: Run test and confirm GREEN**

```powershell
.\mvnw.cmd -Dtest=ActivityControllerTests test
```

Expected: all Controller tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- src/main/java/com/dongqh/luckyhub/activity/controller/ActivityController.java src/test/java/com/dongqh/luckyhub/activity/controller/ActivityControllerTests.java
git commit -m "feat: expose activity lifecycle API"
```

---

### Task 5: Activity prize configuration

**Files:**

- Create: `src/main/java/com/dongqh/luckyhub/activity/dto/AddActivityPrizeCommand.java`
- Create: `src/main/java/com/dongqh/luckyhub/activity/dto/UpdateActivityPrizeCommand.java`
- Create: `src/main/java/com/dongqh/luckyhub/activity/vo/ActivityPrizeView.java`
- Create: `src/main/java/com/dongqh/luckyhub/activity/service/ActivityPrizeService.java`
- Create: `src/main/java/com/dongqh/luckyhub/activity/service/impl/ActivityPrizeServiceImpl.java`
- Create: `src/main/java/com/dongqh/luckyhub/activity/controller/ActivityPrizeController.java`
- Test: `src/test/java/com/dongqh/luckyhub/activity/service/ActivityPrizeServiceTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/activity/controller/ActivityPrizeControllerTests.java`

**Interfaces:**

```java
ActivityPrizeView add(long activityId, AddActivityPrizeCommand command);
List<ActivityPrizeView> list(long activityId);
ActivityPrizeView update(long activityId, long prizeId, UpdateActivityPrizeCommand command);
void remove(long activityId, long prizeId);
```

- [ ] **Step 1: Write failing service tests**

Cover:

```text
add rejects missing activity
add rejects DISABLED and ENDED activity
add rejects missing or disabled prize
add rejects duplicate relation
add initializes remainingStock = totalStock
list joins current prize display fields and sorts by sortOrder/id
update preserves consumed stock
update rejects new total below consumed count
remove deletes only relation
```

Inventory assertion:

```java
relation.setTotalStock(100);
relation.setRemainingStock(80);
service.update(activityId, prizeId, new UpdateActivityPrizeCommand(30, 150, 1));
assertThat(relation.getRemainingStock()).isEqualTo(130);
```

- [ ] **Step 2: Run service test and confirm RED**

```powershell
.\mvnw.cmd -Dtest=ActivityPrizeServiceTests test
```

Expected: compilation fails because the activity-prize service contracts are absent.

- [ ] **Step 3: Implement activity-prize DTO, VO, and service**

Validation:

```text
prizeId: @NotNull @Positive
weight: @NotNull @Positive
totalStock: @NotNull @Positive
sortOrder: @NotNull @PositiveOrZero
```

Use a transaction for add/update/remove. Detect duplicates before insert and also allow the database unique index to protect races.

- [ ] **Step 4: Run service test and confirm GREEN**

```powershell
.\mvnw.cmd -Dtest=ActivityPrizeServiceTests test
```

Expected: all service tests pass.

- [ ] **Step 5: Write failing Controller test**

Test:

```text
POST   /api/admin/activities/5/prizes           → 201
GET    /api/admin/activities/5/prizes           → 200
PUT    /api/admin/activities/5/prizes/7         → 200
DELETE /api/admin/activities/5/prizes/7         → 200
```

Assert GET requires `ACTIVITY_READ`; all mutations require `ACTIVITY_PRIZE_MANAGE`.

- [ ] **Step 6: Run Controller test and confirm RED**

```powershell
.\mvnw.cmd -Dtest=ActivityPrizeControllerTests test
```

Expected: compilation fails because `ActivityPrizeController` is absent.

- [ ] **Step 7: Implement Controller and confirm GREEN**

```powershell
.\mvnw.cmd -Dtest=ActivityPrizeControllerTests,ActivityPrizeServiceTests test
```

Expected: all selected tests pass.

- [ ] **Step 8: Commit**

```powershell
git add -- src/main/java/com/dongqh/luckyhub/activity src/test/java/com/dongqh/luckyhub/activity
git commit -m "feat: configure activity prizes"
```

---

### Task 6: API guide and execution-flow teaching document

**Files:**

- Create: `docs/activity-management-api.md`
- Create: `docs/LuckyHub-活动管理实现详解.md`
- Modify: `README.md`

**Interfaces:**

- Documents all implemented activity endpoints, permissions, request bodies, responses, transitions, scheduler behavior, and error codes.

- [ ] **Step 1: Write the concise API guide**

Include:

```text
all endpoint/method/permission mappings
create/update JSON examples
activity-prize JSON examples
DRAFT/SCHEDULED/RUNNING/ENDED/DISABLED transition rules
30-second scheduler interval configuration
all ActivityErrorCode values
```

- [ ] **Step 2: Write the teaching document in execution order**

Start with:

```http
POST /api/admin/activities
```

Follow:

```text
HTTP JSON
→ AuthenticationFilter
→ PermissionInterceptor
→ ActivityController
→ CreateActivityCommand
→ validation
→ ActivityServiceImpl
→ LoginContext
→ MarketingActivity
→ MarketingActivityMapper
→ MySQL
→ ActivityView
→ JSON
```

Then follow separate real requests for adding a prize, publishing, scheduler-driven `SCHEDULED → RUNNING → ENDED`, disabling, restoring, editing, and republishing. At every step explain “what is missing, why this class exists, input, key lines, output, and next receiver.”

- [ ] **Step 3: Link both guides from README**

Use relative Markdown links.

- [ ] **Step 4: Validate documentation**

```powershell
git diff --check
rg -n "POST /api/admin/activities|ActivityStatusScheduler|NOW\\(3\\)|DISABLED|restore|activity:prize:manage" docs README.md
```

Expected: no whitespace errors and all required concepts appear.

- [ ] **Step 5: Commit**

```powershell
git add -- README.md docs/activity-management-api.md 'docs/LuckyHub-活动管理实现详解.md'
git commit -m "docs: explain activity management flow"
```

---

### Task 7: Full verification and handoff

**Files:**

- Verify all modified files.

**Interfaces:**

- Produces a test-verified, package-verified feature and clean Git diff.

- [ ] **Step 1: Run all tests**

```powershell
.\mvnw.cmd test
```

Expected: `BUILD SUCCESS`, zero failures and zero errors.

- [ ] **Step 2: Build the deployable package**

```powershell
.\mvnw.cmd package -DskipTests
```

Expected: `BUILD SUCCESS` and the application JAR exists under `target`.

- [ ] **Step 3: Audit requirements**

```powershell
rg -n "activity:create|activity:read|activity:update|activity:publish|activity:disable|activity:restore|activity:prize:manage" src/main src/test
rg -n "RedisTemplate|StringRedisTemplate|RedisCache" src/main/java/com/dongqh/luckyhub/activity
git diff --check
git status --short
```

Expected:

- all seven permissions are present;
- no Redis API appears in the activity package;
- no whitespace errors;
- only intended files are modified.

- [ ] **Step 4: Review changes against the specification**

Compare implementation with:

```text
docs/superpowers/specs/2026-07-27-activity-management-design.md
```

Verify each API, state transition, validation, permission, scheduler behavior, and documentation requirement.

- [ ] **Step 5: Commit any verification-only corrections**

If corrections were required, commit only the corrected files with:

```powershell
git commit -m "fix: complete activity management verification"
```

If no correction was required, do not create an empty commit.
