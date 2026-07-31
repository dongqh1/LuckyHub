# LuckyHub Lottery Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first complete LuckyHub single-draw and ten-draw flow with no-win results, Redis quota reservation, MySQL inventory, idempotent orders, user benefits, broker-neutral Outbox messaging, Redis Stream delivery, reconciliation, permissions, tests, and teaching documentation.

**Architecture:** The synchronous request uses JWT identity, Redisson user/activity locking, Redis Lua quota reservation, a committed `PROCESSING` order, and one MySQL draw transaction. MySQL is the source of truth; Outbox events drive Redis quota confirmation/release and benefit fulfillment through a `DrawEventPublisher` port whose first adapter is Redis Stream and whose future adapter can be Kafka.

**Tech Stack:** Java 17, Spring Boot 4.1.0, MyBatis-Plus 3.5.17, MySQL 8.4, Flyway, Spring Data Redis, Redisson Community 4.6.1, Redis Stream, JUnit 5, AssertJ, Mockito.

## Global Constraints

- Develop directly in the existing `master` workspace; do not create a Git Worktree unless the user changes this decision.
- Use PowerShell 7 and `scripts/Invoke-Maven.ps1`; direct `mvn` may select Java 8.
- Use TDD for every behavior: failing test, observed failure, minimal implementation, passing test, focused commit.
- `userId` always comes from `LoginContext.require()` and never from a draw request body.
- `drawCount` is exactly `1` or `10`; ten-draw is all-or-nothing.
- MySQL is authoritative for orders, records, stock, benefits, Outbox, and consumer idempotency.
- Redis is authoritative only for live daily quota reservation state and locks; reconciliation uses MySQL to repair it.
- A selected prize becomes `WIN` only after the conditional MySQL stock update affects one row; otherwise it becomes `NO_WIN` without rerolling.
- User-visible APIs never expose weight, `noWinWeight`, or exact stock.
- Business services depend on `DrawEventPublisher`, not Redis Stream or Kafka APIs.
- Do not commit `.env`, `docs/AccessKey*.csv`, secrets, Redis passwords, JWT secrets, or OSS credentials.
- After production-code changes, run the complete test suite and package verification before claiming completion.

---

## Planned File Map

### Existing files to modify

```text
pom.xml
src/main/resources/application.yaml
src/main/java/com/dongqh/luckyhub/config/MybatisPlusConfig.java
src/main/java/com/dongqh/luckyhub/activity/entity/MarketingActivity.java
src/main/java/com/dongqh/luckyhub/activity/dto/CreateActivityCommand.java
src/main/java/com/dongqh/luckyhub/activity/dto/UpdateActivityCommand.java
src/main/java/com/dongqh/luckyhub/activity/vo/ActivityView.java
src/main/java/com/dongqh/luckyhub/activity/service/impl/ActivityServiceImpl.java
src/main/java/com/dongqh/luckyhub/rbac/constant/PermissionCodes.java
src/main/java/com/dongqh/luckyhub/rbac/service/Impl/UserServiceImpl.java
README.md
```

### New production areas

```text
src/main/resources/db/migration/V5__add_lottery_core.sql
src/main/resources/redis/lottery/reserve_draw_quota.lua
src/main/resources/redis/lottery/confirm_draw_quota.lua
src/main/resources/redis/lottery/release_draw_quota.lua
src/main/java/com/dongqh/luckyhub/lottery/**
src/main/java/com/dongqh/luckyhub/inventory/**
src/main/java/com/dongqh/luckyhub/benefit/**
src/main/java/com/dongqh/luckyhub/config/RedissonConfig.java
```

Every new class must have one responsibility. Keep Controller mapping, orchestration, random selection, quota scripts, inventory SQL, message delivery, and benefit fulfillment in separate files.

---

### Task 1: Database migration and persistence contract

**Files:**
- Create: `src/main/resources/db/migration/V5__add_lottery_core.sql`
- Modify: `src/test/java/com/dongqh/luckyhub/DatabaseSchemaMigrationTests.java`
- Create: `src/test/java/com/dongqh/luckyhub/lottery/LotterySchemaContractTests.java`

**Interfaces:**
- Produces the exact tables and columns consumed by all later mapper/entity tasks.

- [ ] **Step 1: Write failing schema assertions**

Add assertions that JDBC metadata contains:

```java
assertThat(column("marketing_activity", "no_win_weight")).isPresent();
assertThat(column("lottery_draw_order", "draw_date")).isPresent();
assertThat(column("lottery_draw_record", "result_type")).isPresent();
assertThat(column("lottery_draw_record", "prize_name")).isPresent();
assertThat(column("user_benefit", "draw_record_id")).isPresent();
assertThat(table("message_outbox")).isPresent();
assertThat(table("message_consume_record")).isPresent();
```

Also query `sys_permission` and assert all lottery permissions exist, and query `sys_role` for `USER`.

- [ ] **Step 2: Run the schema tests and observe failure**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 "-Dtest=DatabaseSchemaMigrationTests,LotterySchemaContractTests" test
```

Expected: failure because Flyway has no V5 migration and required columns/tables are absent.

- [ ] **Step 3: Create V5 migration**

The migration must:

```sql
ALTER TABLE marketing_activity
    ADD COLUMN no_win_weight INT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '独立未中奖权重' AFTER daily_limit;

ALTER TABLE lottery_draw_order
    ADD COLUMN draw_date DATE NULL COMMENT '上海时区抽奖日期' AFTER draw_count;
UPDATE lottery_draw_order SET draw_date = DATE(created_at) WHERE draw_date IS NULL;
ALTER TABLE lottery_draw_order MODIFY draw_date DATE NOT NULL COMMENT '上海时区抽奖日期';

ALTER TABLE lottery_draw_record
    ADD COLUMN result_type VARCHAR(20) NOT NULL DEFAULT 'WIN' AFTER activity_id,
    MODIFY prize_id BIGINT UNSIGNED NULL,
    ADD COLUMN prize_name VARCHAR(100) NULL AFTER prize_id,
    ADD COLUMN prize_type VARCHAR(30) NULL AFTER prize_name,
    ADD COLUMN prize_image_url VARCHAR(1000) NULL AFTER prize_type;

ALTER TABLE user_benefit
    ADD COLUMN draw_record_id BIGINT UNSIGNED NOT NULL AFTER id,
    ADD COLUMN prize_type VARCHAR(30) NOT NULL AFTER prize_id,
    ADD COLUMN grant_error VARCHAR(500) NULL AFTER status,
    ADD UNIQUE KEY uk_user_benefit_draw_record (draw_record_id);
```

Create `message_outbox` with unique `event_id`, JSON `payload`, indexed `status/next_retry_at`, and `PENDING/SENT/FAILED` status check. Create `message_consume_record` with unique `(event_id, consumer_name)`.

Create the `USER` role idempotently. Insert the five ordinary permissions and four `read:all` permissions idempotently. Grant ordinary permissions to USER and ADMIN, all permissions to ADMIN, and associate every existing user with USER without duplicating relations.

Before applying V5, assert the currently unused `user_benefit` table has no legacy rows. This matches the confirmed NOT NULL design and prevents benefits without a draw source; if a deployed environment contains rows, stop migration and create an explicit data-backfill migration instead of inventing source records.

- [ ] **Step 4: Run schema tests**

Expected: both schema tests pass and Flyway reports schema version 5.

- [ ] **Step 5: Commit**

```powershell
git add src/main/resources/db/migration/V5__add_lottery_core.sql src/test/java/com/dongqh/luckyhub/DatabaseSchemaMigrationTests.java src/test/java/com/dongqh/luckyhub/lottery/LotterySchemaContractTests.java
git commit -m "feat: add lottery core schema"
```

---

### Task 2: Redisson dependency and lottery configuration

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yaml`
- Create: `src/main/java/com/dongqh/luckyhub/config/RedissonConfig.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/config/LotteryProperties.java`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/config/LotteryConfigurationTests.java`

**Interfaces:**
- Produces: `RedissonClient`, `LotteryProperties`, and broker/timeout settings used by quota, lock, stream, Outbox, and reconciliation tasks.

- [ ] **Step 1: Write failing configuration test**

```java
@SpringBootTest
class LotteryConfigurationTests {
    @Autowired LotteryProperties properties;
    @Autowired RedissonClient redissonClient;

    @Test
    void bindsSafeLotteryDefaults() {
        assertThat(properties.zoneId()).isEqualTo(ZoneId.of("Asia/Shanghai"));
        assertThat(properties.processingTimeout()).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.reconcileInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(redissonClient).isNotNull();
    }
}
```

- [ ] **Step 2: Run and observe compilation failure**

Expected: missing Redisson and `LotteryProperties` types.

- [ ] **Step 3: Add dependency and properties**

Add:

```xml
<redisson.version>4.6.1</redisson.version>
<dependency>
  <groupId>org.redisson</groupId>
  <artifactId>redisson</artifactId>
  <version>${redisson.version}</version>
</dependency>
```

Keep `spring-boot-starter-data-redis`; do not add the Redisson starter. Manually build a single-server `RedissonClient` from the existing Redis host, port, password, and database properties.

Bind immutable properties under:

```yaml
luckyhub:
  lottery:
    zone-id: Asia/Shanghai
    lock-wait: 3s
    processing-timeout: 2m
    reconcile-interval: 30s
    reservation-retention: 72h
    outbox-interval: 5s
    outbox-batch-size: 100
  messaging:
    provider: redis-stream
    lottery-stream: luckyhub:stream:lottery
    lottery-group: luckyhub-lottery-consumers
```

- [ ] **Step 4: Run configuration test and full context test**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 "-Dtest=LotteryConfigurationTests,LuckyhubApplicationTests" test
```

- [ ] **Step 5: Commit**

```powershell
git add pom.xml src/main/resources/application.yaml src/main/java/com/dongqh/luckyhub/config/RedissonConfig.java src/main/java/com/dongqh/luckyhub/lottery/config/LotteryProperties.java src/test/java/com/dongqh/luckyhub/lottery/config/LotteryConfigurationTests.java
git commit -m "feat: configure lottery redis infrastructure"
```

---

### Task 3: Lottery, benefit, and Outbox persistence model

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/lottery/enums/{DrawOrderStatus,DrawResultType,OutboxStatus,LotteryErrorCode}.java`
- Create: `src/main/java/com/dongqh/luckyhub/benefit/enums/{BenefitStatus,BenefitErrorCode}.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/entity/{LotteryDrawOrder,LotteryDrawRecord,MessageOutbox,MessageConsumeRecord}.java`
- Create: `src/main/java/com/dongqh/luckyhub/benefit/entity/UserBenefit.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/mapper/{LotteryDrawOrderMapper,LotteryDrawRecordMapper,MessageOutboxMapper,MessageConsumeRecordMapper}.java`
- Create: `src/main/java/com/dongqh/luckyhub/benefit/mapper/UserBenefitMapper.java`
- Modify: `src/main/java/com/dongqh/luckyhub/config/MybatisPlusConfig.java`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/LotteryPersistenceContractTests.java`

**Interfaces:**
- Produces typed persistence contracts for all services.

- [ ] **Step 1: Write failing entity and mapper contract tests**

Assert enum values exactly match database states and reflection finds snapshot fields, `drawDate`, Outbox retry fields, and benefit source fields. Assert every mapper extends `BaseMapper<ExpectedEntity>`.

- [ ] **Step 2: Run and observe compilation failure**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 "-Dtest=LotteryPersistenceContractTests" test
```

- [ ] **Step 3: Implement focused enums, entities, and mappers**

Use Java types:

```java
enum DrawOrderStatus { PROCESSING, SUCCESS, FAILED }
enum DrawResultType { WIN, NO_WIN }
enum BenefitStatus { PENDING, AVAILABLE, CLAIM_PENDING, GRANT_FAILED }
enum OutboxStatus { PENDING, SENT, FAILED }
```

Use `LocalDate` for `drawDate`, `LocalDateTime` for timestamps, `String` for JSON payload, and nullable snapshot fields for `NO_WIN`. Add mapper packages for `lottery`, `inventory`, and `benefit` to `@MapperScan`.

- [ ] **Step 4: Run contract and application context tests**

Expected: mapper beans register successfully.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/dongqh/luckyhub/lottery src/main/java/com/dongqh/luckyhub/benefit src/main/java/com/dongqh/luckyhub/config/MybatisPlusConfig.java src/test/java/com/dongqh/luckyhub/lottery/LotteryPersistenceContractTests.java
git commit -m "feat: add lottery persistence contracts"
```

---

### Task 4: Activity no-win configuration

**Files:**
- Modify: `src/main/java/com/dongqh/luckyhub/activity/entity/MarketingActivity.java`
- Modify: `src/main/java/com/dongqh/luckyhub/activity/dto/CreateActivityCommand.java`
- Modify: `src/main/java/com/dongqh/luckyhub/activity/dto/UpdateActivityCommand.java`
- Modify: `src/main/java/com/dongqh/luckyhub/activity/vo/ActivityView.java`
- Modify: `src/main/java/com/dongqh/luckyhub/activity/service/impl/ActivityServiceImpl.java`
- Modify: `src/test/java/com/dongqh/luckyhub/activity/service/ActivityServiceTests.java`
- Modify: `src/test/java/com/dongqh/luckyhub/activity/controller/ActivityControllerTests.java`

**Interfaces:**
- Produces: persisted `MarketingActivity.noWinWeight` and validates `sum(prize.weight) + noWinWeight > 0` at publish.

- [ ] **Step 1: Add failing tests**

Cover create/update persistence, negative command validation, zero allowed, and publish rejection only when total weight is zero. Preserve existing rule that publishing requires configured enabled prizes.

- [ ] **Step 2: Run selected activity tests and observe failure**

- [ ] **Step 3: Implement field propagation and validation**

Use `@NotNull @PositiveOrZero Integer noWinWeight` in commands. Extend the existing `apply(...)` and `toView(...)` methods rather than duplicating conversion logic.

- [ ] **Step 4: Run all activity tests**

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/dongqh/luckyhub/activity src/test/java/com/dongqh/luckyhub/activity
git commit -m "feat: configure activity no-win weight"
```

---

### Task 5: USER role assignment and data-scope permission helper

**Files:**
- Modify: `src/main/java/com/dongqh/luckyhub/rbac/constant/PermissionCodes.java`
- Modify: `src/main/java/com/dongqh/luckyhub/rbac/service/Impl/UserServiceImpl.java`
- Create: `src/main/java/com/dongqh/luckyhub/rbac/service/DataScopeService.java`
- Create: `src/main/java/com/dongqh/luckyhub/rbac/service/Impl/DataScopeServiceImpl.java`
- Test: `src/test/java/com/dongqh/luckyhub/rbac/service/UserDefaultRoleTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/rbac/service/DataScopeServiceTests.java`

**Interfaces:**
- Produces: `boolean hasPermission(long userId, String code)` and `UserDataScope resolveUserScope(Long requestedUserId, String readAllPermission)`.

- [ ] **Step 1: Write failing role and scope tests**

Tests must verify new users receive USER in the same transaction, ordinary callers are forced to self, explicit foreign `userId` is rejected, and `read:all` callers may request a user or all-user query.

- [ ] **Step 2: Run and observe failure**

- [ ] **Step 3: Implement permission constants and services**

Add all nine codes from the design. In `createUser`, find enabled USER role and insert `SysUserRole` after `userMapper.insert`; absence of USER is a configuration error and rolls back user creation.

Represent unrestricted scope separately from a user ID, for example:

```java
public record UserDataScope(boolean all, Long userId) {
    public static UserDataScope allUsers() { return new UserDataScope(true, null); }
    public static UserDataScope one(long id) { return new UserDataScope(false, id); }
}
```

- [ ] **Step 4: Run RBAC tests**

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/dongqh/luckyhub/rbac src/test/java/com/dongqh/luckyhub/rbac
git commit -m "feat: add lottery user permissions"
```

---

### Task 6: Redis quota reservation and draw lock

**Files:**
- Create: `src/main/resources/redis/lottery/{reserve_draw_quota,confirm_draw_quota,release_draw_quota}.lua`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/quota/{DrawQuotaService,RedisDrawQuotaService,DrawQuotaKeys,QuotaReservationRequest,QuotaReservationResult,ReservationStatus}.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/lock/{DrawLockService,RedissonDrawLockService}.java`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/quota/RedisDrawQuotaServiceTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/lock/RedissonDrawLockServiceTests.java`

**Interfaces:**
- Produces:

```java
QuotaReservationResult reserve(QuotaReservationRequest request);
void confirm(String requestId);
void release(String requestId);
<T> T execute(long activityId, long userId, Supplier<T> action);
```

- [ ] **Step 1: Write failing Redis integration tests**

Cover reserve 1/10, insufficient quota, duplicate request, confirm/release idempotency, Shanghai `drawDate`, timeout Sorted Set membership/removal, and 100 concurrent reservations never exceeding `dailyLimit`.

- [ ] **Step 2: Run and observe missing implementation**

- [ ] **Step 3: Implement centralized keys and three Lua scripts**

Reservation stores `userId`, `activityId`, `drawCount`, `drawDate`, `status=RESERVED`, and creation time in a hash, increments quota, and adds request ID to `draw:reservation:timeouts`. Confirmation/removal scripts validate current status atomically and remove the timeout member. Never call Redis `KEYS`.

- [ ] **Step 4: Implement Redisson lock wrapper**

Use `tryLock(properties.lockWait().toMillis(), TimeUnit.MILLISECONDS)`, execute the callback only while held by the current thread, and unlock in `finally`. Translate timeout/interruption into `LotteryErrorCode.DRAW_LOCK_UNAVAILABLE`; restore interrupt status.

- [ ] **Step 5: Run quota and lock tests**

- [ ] **Step 6: Commit**

```powershell
git add src/main/resources/redis/lottery src/main/java/com/dongqh/luckyhub/lottery/quota src/main/java/com/dongqh/luckyhub/lottery/lock src/test/java/com/dongqh/luckyhub/lottery/quota src/test/java/com/dongqh/luckyhub/lottery/lock
git commit -m "feat: reserve draw quota atomically"
```

---

### Task 7: Deterministic weight engine and atomic inventory

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/lottery/algorithm/{DrawRandomSource,SecureDrawRandomSource,WeightedDrawEngine,WeightedDrawEngineImpl,DrawCandidate,PrizeWeightSnapshot}.java`
- Create: `src/main/java/com/dongqh/luckyhub/inventory/mapper/ActivityPrizeInventoryMapper.java`
- Create: `src/main/java/com/dongqh/luckyhub/inventory/service/{ActivityPrizeInventoryService,ActivityPrizeInventoryServiceImpl}.java`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/algorithm/WeightedDrawEngineTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/inventory/ActivityPrizeInventoryTests.java`

**Interfaces:**
- Produces:

```java
DrawCandidate select(List<PrizeWeightSnapshot> prizes, int noWinWeight);
boolean decrementIfAvailable(long activityPrizeId);
```

- [ ] **Step 1: Write failing deterministic algorithm tests**

Use a fake `DrawRandomSource` returning exact boundary values. Test independent no-win, zero no-win, disabled/empty prize interval becoming `NO_WIN`, positive total requirement, and no weight redistribution.

- [ ] **Step 2: Run and observe failure**

- [ ] **Step 3: Implement engine without database access**

The engine only returns `PRIZE_CANDIDATE` or `NO_WIN`; it never decrements stock and never rerolls. Reject integer overflow by accumulating weights in `long`.

- [ ] **Step 4: Write and implement atomic inventory test**

Mapper SQL is exactly the conditional update from the design. Run 100 concurrent calls against stock 10 and assert ten successes, stock zero, never negative.

- [ ] **Step 5: Run both suites and commit**

```powershell
git add src/main/java/com/dongqh/luckyhub/lottery/algorithm src/main/java/com/dongqh/luckyhub/inventory src/test/java/com/dongqh/luckyhub/lottery/algorithm src/test/java/com/dongqh/luckyhub/inventory
git commit -m "feat: select weighted prizes safely"
```

---

### Task 8: Broker-neutral events and Outbox persistence

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/lottery/messaging/event/{DrawEventEnvelope,DrawConfirmedEvent,DrawReleaseRequestedEvent,PrizeFulfillmentRequestedEvent,DrawEventType}.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/messaging/port/DrawEventPublisher.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/service/{OutboxService,OutboxServiceImpl}.java`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/messaging/OutboxServiceTests.java`

**Interfaces:**
- Produces:

```java
void append(DrawEventEnvelope event);
void publish(DrawEventEnvelope event);
```

- [ ] **Step 1: Write failing serialization and persistence tests**

Assert a stable event envelope with event/version/request metadata, JSON payload round-trip, unique event ID handling, and no Redis-specific field in entity or event.

- [ ] **Step 2: Run and observe failure**

- [ ] **Step 3: Implement immutable events and Outbox service**

Use Jackson `ObjectMapper`, UUID event IDs, version `1`, and `PENDING` initial status. `append` participates in the caller's MySQL transaction.

- [ ] **Step 4: Run tests and commit**

```powershell
git add src/main/java/com/dongqh/luckyhub/lottery/messaging src/main/java/com/dongqh/luckyhub/lottery/service src/test/java/com/dongqh/luckyhub/lottery/messaging
git commit -m "feat: persist broker-neutral draw events"
```

---

### Task 9: Order lifecycle and atomic draw transaction

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/lottery/service/{DrawOrderLifecycleService,DrawTransactionService}.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/service/impl/{DrawOrderLifecycleServiceImpl,DrawTransactionServiceImpl}.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/model/{DrawExecutionContext,DrawExecutionResult,DrawResultItem}.java`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/service/DrawOrderLifecycleServiceTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/service/DrawTransactionServiceTests.java`

**Interfaces:**
- Produces:

```java
LotteryDrawOrder createProcessing(NewDrawOrder command); // REQUIRES_NEW
void markFailed(long orderId, String safeReason);          // REQUIRES_NEW
DrawExecutionResult execute(DrawExecutionContext context); // REQUIRED
```

- [ ] **Step 1: Write failing lifecycle tests**

Verify PROCESSING is committed separately, duplicate request returns existing order, parameter mismatch raises conflict, and `markFailed` only updates `PROCESSING`.

- [ ] **Step 2: Write failing transaction tests**

Verify one/ten records, snapshot fields, no-win null fields, conditional inventory behavior, `PENDING` benefits only for wins, SUCCESS conditional update, confirmed/fulfillment Outbox events, and rollback when sequence 7 throws.

- [ ] **Step 3: Run and observe failure**

- [ ] **Step 4: Implement lifecycle and transaction services**

Use `@Transactional(propagation = REQUIRES_NEW)` for order creation/failure. The business transaction inserts all records and benefits, changes `PROCESSING -> SUCCESS` with a conditional mapper update, appends events, and throws if the status update affects zero rows.

- [ ] **Step 5: Run service tests and commit**

```powershell
git add src/main/java/com/dongqh/luckyhub/lottery/service src/main/java/com/dongqh/luckyhub/lottery/model src/test/java/com/dongqh/luckyhub/lottery/service
git commit -m "feat: persist atomic draw results"
```

---

### Task 10: Synchronous draw orchestration and idempotency

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/lottery/dto/DrawCommand.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/vo/{DrawOrderView,DrawResultView}.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/service/{LotteryService,LotteryServiceImpl,DrawEligibilityService,DrawEligibilityServiceImpl}.java`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/service/LotteryServiceTests.java`

**Interfaces:**
- Produces: `DrawOrderView draw(DrawCommand command)` and `DrawOrderView getByRequestId(String requestId)`.

- [ ] **Step 1: Write failing orchestration tests**

Cover JWT user source, validation, existing SUCCESS/FAILED/PROCESSING behavior, ownership, parameter conflict, RUNNING requirement, one configuration snapshot, lock ordering, quota reservation, PROCESSING creation, successful transaction, failure marking/release event, and exact synchronous result.

- [ ] **Step 2: Run and observe failure**

- [ ] **Step 3: Implement orchestration in this order**

```text
LoginContext → MySQL idempotency → eligibility/snapshot → lock
→ second idempotency check → Lua reserve → PROCESSING order
→ release lock → draw transaction → return result
```

On failure after reservation, mark the order FAILED when it exists and append a release event in a new transaction. If MySQL is unavailable, leave the Redis reservation for timeout reconciliation.

- [ ] **Step 4: Run orchestration tests and commit**

```powershell
git add src/main/java/com/dongqh/luckyhub/lottery/dto src/main/java/com/dongqh/luckyhub/lottery/vo src/main/java/com/dongqh/luckyhub/lottery/service src/test/java/com/dongqh/luckyhub/lottery/service
git commit -m "feat: orchestrate idempotent lottery draws"
```

---

### Task 11: Redis Stream adapter, Outbox relay, and idempotent consumers

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/lottery/messaging/redis/{RedisStreamDrawEventPublisher,RedisStreamDrawEventConsumer,RedisStreamInitializer}.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/scheduler/OutboxRelayScheduler.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/service/{OutboxRelayService,MessageConsumeService}.java`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/messaging/RedisStreamMessagingTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/scheduler/OutboxRelaySchedulerTests.java`

**Interfaces:**
- Consumes `DrawEventPublisher`, Outbox mappers, quota confirm/release methods.
- Produces reliable Stream transport behind the port.

- [ ] **Step 1: Write failing adapter and relay tests**

Test stream/group initialization, publish envelope fields, relay PENDING/FAILED selection, SENT update only after publish, retry metadata on failure, consumer duplicate event handling, quota confirm/release dispatch, and XACK only after success.

- [ ] **Step 2: Run and observe failure**

- [ ] **Step 3: Implement Redis adapter and relay**

Use `StringRedisTemplate` Stream operations only inside the Redis adapter. Claim Outbox batches with a database-safe conditional update or `FOR UPDATE SKIP LOCKED`; never mark SENT before broker acknowledgement. Consumer names include application instance identity.

- [ ] **Step 4: Run messaging tests and commit**

```powershell
git add src/main/java/com/dongqh/luckyhub/lottery/messaging/redis src/main/java/com/dongqh/luckyhub/lottery/scheduler src/main/java/com/dongqh/luckyhub/lottery/service src/test/java/com/dongqh/luckyhub/lottery/messaging src/test/java/com/dongqh/luckyhub/lottery/scheduler
git commit -m "feat: deliver draw events through redis stream"
```

---

### Task 12: Benefit fulfillment handlers

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/benefit/handler/{BenefitFulfillmentHandler,CouponFulfillmentHandler,PointsFulfillmentHandler,MembershipFulfillmentHandler,PhysicalFulfillmentHandler,BenefitFulfillmentRouter}.java`
- Create: `src/main/java/com/dongqh/luckyhub/benefit/service/{BenefitFulfillmentService,BenefitFulfillmentServiceImpl}.java`
- Test: `src/test/java/com/dongqh/luckyhub/benefit/BenefitFulfillmentServiceTests.java`

**Interfaces:**
- Produces: `void fulfill(long benefitId, String eventId)`.

- [ ] **Step 1: Write failing handler tests**

Assert COUPON/POINTS/MEMBERSHIP become AVAILABLE, PHYSICAL becomes CLAIM_PENDING, failure becomes GRANT_FAILED with safe error, duplicate event creates no second effect, and draw order/result are untouched.

- [ ] **Step 2: Run and observe failure**

- [ ] **Step 3: Implement router and handlers**

Select exactly one handler by `PrizeType`. Perform benefit state update and `message_consume_record` insert in one MySQL transaction. Treat an existing consume record as successful idempotent completion.

- [ ] **Step 4: Connect fulfillment event in Redis consumer, run tests, commit**

```powershell
git add src/main/java/com/dongqh/luckyhub/benefit src/main/java/com/dongqh/luckyhub/lottery/messaging/redis src/test/java/com/dongqh/luckyhub/benefit
git commit -m "feat: fulfill lottery benefits asynchronously"
```

---

### Task 13: Reconciliation tasks

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/lottery/service/{LotteryReconciliationService,LotteryReconciliationServiceImpl}.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/scheduler/LotteryReconciliationScheduler.java`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/service/LotteryReconciliationServiceTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/scheduler/LotteryReconciliationSchedulerTests.java`

**Interfaces:**
- Produces: `ReconciliationResult reconcileExpiredReservations(Instant now)`.

- [ ] **Step 1: Write failing reconciliation tests**

Cover `RESERVED+SUCCESS -> CONFIRMED`, `RESERVED+FAILED/no order -> RELEASED`, fresh PROCESSING unchanged, expired PROCESSING conditionally failed plus release event, and a concurrent SUCCESS transition preventing FAILED overwrite.

- [ ] **Step 2: Run and observe failure**

- [ ] **Step 3: Implement Sorted Set batch reconciliation**

Read only due request IDs by score, process bounded batches, and use conditional order transitions. Never use Redis `KEYS`. A zero-row state update means another worker won; reload final state before confirming/releasing quota.

- [ ] **Step 4: Run reconciliation tests and commit**

```powershell
git add src/main/java/com/dongqh/luckyhub/lottery/service src/main/java/com/dongqh/luckyhub/lottery/scheduler src/test/java/com/dongqh/luckyhub/lottery/service src/test/java/com/dongqh/luckyhub/lottery/scheduler
git commit -m "feat: reconcile lottery reservations"
```

---

### Task 14: Unified HTTP APIs and permission-scoped queries

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/lottery/controller/LotteryController.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/dto/{DrawOrderQuery,DrawRecordQuery}.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/vo/{LotteryActivityView,DrawRecordView}.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/service/{LotteryQueryService,LotteryQueryServiceImpl}.java`
- Create: `src/main/java/com/dongqh/luckyhub/benefit/controller/BenefitController.java`
- Create: `src/main/java/com/dongqh/luckyhub/benefit/dto/BenefitQuery.java`
- Create: `src/main/java/com/dongqh/luckyhub/benefit/vo/BenefitView.java`
- Create: `src/main/java/com/dongqh/luckyhub/benefit/service/{BenefitQueryService,BenefitQueryServiceImpl}.java`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/controller/LotteryControllerTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/benefit/controller/BenefitControllerTests.java`

**Interfaces:**
- Exposes the seven unified endpoints from the spec.

- [ ] **Step 1: Write failing MockMvc tests**

Cover all routes, request validation, permission annotations, synchronous single/ten results, self-only lookup, `read:all` queries, foreign `userId` rejection, pagination, and absence of weights/exact stock in activity JSON.

- [ ] **Step 2: Run and observe failure**

- [ ] **Step 3: Implement thin controllers and scoped query services**

Controllers return `ApiResponse`; services obtain `LoginContext`, resolve `UserDataScope`, and build MyBatis-Plus page queries. `/api/lottery/orders` requires `lottery:order:read:all`; draw lookup/records/benefits use base read permission and apply ownership unless all-read is present.

- [ ] **Step 4: Run controller and query tests, commit**

```powershell
git add src/main/java/com/dongqh/luckyhub/lottery src/main/java/com/dongqh/luckyhub/benefit src/test/java/com/dongqh/luckyhub/lottery/controller src/test/java/com/dongqh/luckyhub/benefit/controller
git commit -m "feat: expose permission-scoped lottery APIs"
```

---

### Task 15: End-to-end failure, concurrency, and security verification

**Files:**
- Create: `src/test/java/com/dongqh/luckyhub/lottery/LotteryEndToEndTests.java`
- Create: `src/test/java/com/dongqh/luckyhub/lottery/LotteryConcurrencyTests.java`
- Modify focused production files only if these tests reveal a defect.

**Interfaces:**
- Verifies the assembled vertical slice, not new production APIs.

- [ ] **Step 1: Add end-to-end tests using real MySQL and Redis**

Scenarios: WIN, independent NO_WIN, sold-out NO_WIN, candidate stock race NO_WIN, ten complete results, identical retry, conflicting retry, failed transaction/returned quota, Stream unavailable/Outbox retained, timeout reconciliation, USER scope, ADMIN all scope.

- [ ] **Step 2: Add concurrency tests**

Run concurrent requests against quota and stock barriers. Assert daily use never exceeds limit, stock never becomes negative, unique `requestId` produces one order, and every successful order has exactly `drawCount` records.

- [ ] **Step 3: Run focused end-to-end tests and fix only demonstrated defects**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 "-Dtest=LotteryEndToEndTests,LotteryConcurrencyTests" test
```

- [ ] **Step 4: Run complete suite**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 test
```

Expected: zero failures and zero errors.

- [ ] **Step 5: Commit**

```powershell
git add src/test/java/com/dongqh/luckyhub/lottery src/main/java
git commit -m "test: verify lottery core flow"
```

---

### Task 16: API guide and execution-flow teaching document

**Files:**
- Create: `docs/lottery-api.md`
- Create: `docs/LuckyHub-抽奖核心流程实现详解.md`
- Modify: `README.md`

**Interfaces:**
- Documents the actual final implementation; documentation must not describe planned classes that differ from code.

- [ ] **Step 1: Write concise API guide**

Include authentication, permissions, every request/response, error code, single/ten examples, request ID retry rules, query parameters, and Postman commands.

- [ ] **Step 2: Write the detailed teaching document in runtime order**

Start at `POST /api/lottery/draws` and explain every new/modified file, class, annotation, method, field, parameter, return value, DTO/VO/Entity/Mapper/Service distinction, JWT identity, lock, Lua scripts line by line, weight boundaries, stock SQL, transaction split, Outbox, Stream, Kafka replacement, benefit handlers, reconciliation, breakpoints, MySQL/Redis inspection, and troubleshooting.

Use concrete examples for single draw, ten draw, explicit no-win, sold-out no-win, duplicate ID, transaction failure, Redis outage, and app crash.

- [ ] **Step 3: Verify docs against code**

```powershell
rg -n "POST /api/lottery/draws|reserve_draw_quota|DRAW_CONFIRMED|NO_WIN|Kafka|PROCESSING_TIMEOUT" docs README.md
git diff --check
```

Every referenced path and method must exist. Markdown code fences must be even.

- [ ] **Step 4: Commit**

```powershell
git add docs README.md
git commit -m "docs: explain lottery core flow"
```

---

### Task 17: Final verification and handoff

**Files:**
- Modify only files required by observed verification failures.

- [ ] **Step 1: Run complete tests fresh**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 test
```

Record exact test count, failures, errors, and skipped count.

- [ ] **Step 2: Build the executable artifact**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 package -DskipTests
```

Expected: `BUILD SUCCESS` and `target/luckyhub-0.0.1-SNAPSHOT.jar` exists.

- [ ] **Step 3: Audit secrets, formatting, and Git state**

```powershell
git diff --check
rg -n "TODO|TBD|AccessKeySecret|OSS_ACCESS_KEY_SECRET=" src docs README.md .env.example
git status --short
```

Only documented placeholders may match; no real secret or unfinished implementation may remain.

- [ ] **Step 4: Perform requirement checklist against the confirmed spec**

Verify each of the 14 acceptance items in `docs/superpowers/specs/2026-07-31-lottery-core-design.md` against tests, code, schema, and docs.

- [ ] **Step 5: Commit any verification-only corrections and push**

```powershell
git push origin master
```

Handoff must list changed modules, migrations, environment variables, Redis keys, endpoints, permissions, test/build evidence, documentation links, and final commit.
