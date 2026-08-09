# LuckyHub Phase 5 Lottery Unified Reward Fulfillment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate newly configured lottery prizes to immutable `reward_definition` snapshots, unified asynchronous fulfillment, usable local assets, quarantined identity failures, and persistent rewarded draw chances while preserving legacy prizes.

**Architecture:** The draw transaction snapshots a typed reward and emits an Outbox event but never calls a provider. A validated event dispatcher either creates a Phase 4 fulfillment task, credits draw chances, marks a product claim pending, or delegates an unbound legacy prize; a separate idempotent projector materializes successful coupon, points, and membership tasks into local commerce assets. Rewarded draw chances use MySQL account/reservation truth and the existing Redis quota as the operational daily counter.

**Tech Stack:** Java 17, Spring Boot 4.1, MyBatis-Plus, MySQL 8/Flyway, Redis Lua/Redisson, Jackson, JUnit 5, AssertJ, Spring Boot integration tests, PowerShell 7.

## Global Constraints

- Treat `docs/superpowers/specs/2026-08-09-lottery-unified-reward-fulfillment-design.md` as the approved source of truth.
- Never modify V1-V15; add V16 only.
- Use `RewardType` as new-path authority; `PrizeType` remains a compatibility projection.
- Existing `marketing_prize.reward_definition_id IS NULL` rows must keep the legacy flow and must not be guessed or auto-migrated.
- No Gateway call may occur inside the draw transaction or a database transaction.
- Phase 5 must not collect addresses or create logistics tasks, packages, waybills, or tracking events.
- All event errors, projection errors, and quarantine rows must contain bounded safe codes/messages, never raw payloads, secrets, stack traces, full addresses, or phone numbers.
- Use PowerShell 7 and `scripts/Invoke-Maven.ps1`; read and write Chinese as UTF-8.
- Every task creates one `docs/progress/阶段5-任务N-*.md` explaining why, implementation, and a concrete example.
- Preserve user-owned untracked `.codex-progress/` and `.superpowers/` directories.

---

### Task 1: V16 reward snapshot, draw-chance and quarantine persistence

**Files:**
- Create: `src/main/resources/db/migration/V16__integrate_lottery_rewards.sql`
- Create: `src/main/java/com/dongqh/luckyhub/drawchance/enums/DrawChanceBusinessType.java`
- Create: `src/main/java/com/dongqh/luckyhub/drawchance/enums/DrawChanceDirection.java`
- Create: `src/main/java/com/dongqh/luckyhub/drawchance/enums/DrawChanceReservationStatus.java`
- Create: `src/main/java/com/dongqh/luckyhub/drawchance/entity/DrawChanceAccount.java`
- Create: `src/main/java/com/dongqh/luckyhub/drawchance/entity/DrawChanceLedger.java`
- Create: `src/main/java/com/dongqh/luckyhub/drawchance/entity/DrawChanceReservation.java`
- Create: `src/main/java/com/dongqh/luckyhub/drawchance/mapper/DrawChanceAccountMapper.java`
- Create: `src/main/java/com/dongqh/luckyhub/drawchance/mapper/DrawChanceLedgerMapper.java`
- Create: `src/main/java/com/dongqh/luckyhub/drawchance/mapper/DrawChanceReservationMapper.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/enums/RewardQuarantineStatus.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/entity/LotteryRewardQuarantine.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/mapper/LotteryRewardQuarantineMapper.java`
- Modify: `src/main/java/com/dongqh/luckyhub/lottery/entity/LotteryDrawRecord.java`
- Modify: `src/main/java/com/dongqh/luckyhub/benefit/entity/UserBenefit.java`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/LotteryRewardSchemaContractTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/LotteryRewardDomainContractTests.java`
- Document: `docs/progress/阶段5-任务1-V16与奖励领域骨架完成介绍.md`

**Interfaces:**
- Produces nullable snapshot fields `rewardDefinitionId`, `rewardType`, `rewardTargetId`, `rewardQuantity`, `rewardPayload`, `rewardFingerprint` on draw records and benefits.
- Produces nullable unique `UserBenefit.fulfillmentNo`.
- Produces 4 new business tables, taking the empty-schema business table count from 39 to 43.

- [x] **Step 1: Write failing migration and reflection contracts**

Assert exact V16 columns, JSON/CHAR/VARCHAR types, unique indexes, non-negative balances, the four table names, all CHECK values, mapper/entity bindings, and V16 Flyway success. Include a test that inserts a legacy benefit with every new field null and a test that rejects negative balances or duplicate reservation/event identities.

```java
assertThat(column("user_benefit", "reward_fingerprint")).get()
        .returns("char", ColumnMetadata::dataType)
        .returns(64L, ColumnMetadata::maximumLength);
assertThat(uniqueIndexes()).contains(
        "user_benefit:fulfillment_no",
        "draw_chance_account:user_id",
        "draw_chance_reservation:request_id",
        "lottery_reward_quarantine:event_id");
```

- [x] **Step 2: Run RED**

Run:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 '-Dtest=LotteryRewardSchemaContractTests,LotteryRewardDomainContractTests' test
```

Expected: tests fail because V16 and the new Java types do not exist.

- [x] **Step 3: Add V16 and minimal domain mappings**

V16 must add the six snapshot columns to both existing tables, `fulfillment_no` only to `user_benefit`, and create:

```sql
CREATE TABLE draw_chance_account (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  available_balance BIGINT UNSIGNED NOT NULL DEFAULT 0,
  reserved_balance BIGINT UNSIGNED NOT NULL DEFAULT 0,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_draw_chance_account_user (user_id)
);
```

`draw_chance_ledger` has unique `(business_type,business_id)`; `draw_chance_reservation` has unique `request_id`; `lottery_reward_quarantine` has unique `event_id`. Use checks with exactly the enum values approved by the spec.

- [x] **Step 4: Run GREEN and regress the previous schema**

Run the focused command plus `'-Dtest=DatabaseSchemaMigrationTests,LotterySchemaContractTests,FulfillmentSchemaContractTests' test`. Expected: all selected tests pass and Flyway reports V16.

- [x] **Step 5: Document, check this task, and commit**

Document a legacy null-snapshot row and a new draw-chance reservation row. Commit:

```powershell
git commit -m "feat: persist lottery reward integration"
```

### Task 2: Typed reward snapshots and bound prize management

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/reward/model/RewardSnapshot.java`
- Create: `src/main/java/com/dongqh/luckyhub/reward/model/CouponRewardPayload.java`
- Create: `src/main/java/com/dongqh/luckyhub/reward/model/PointsRewardPayload.java`
- Create: `src/main/java/com/dongqh/luckyhub/reward/model/MembershipRewardPayload.java`
- Create: `src/main/java/com/dongqh/luckyhub/reward/model/ProductRewardPayload.java`
- Create: `src/main/java/com/dongqh/luckyhub/reward/model/DrawChanceRewardPayload.java`
- Create: `src/main/java/com/dongqh/luckyhub/reward/service/RewardSnapshotService.java`
- Create: `src/main/java/com/dongqh/luckyhub/reward/service/impl/RewardSnapshotServiceImpl.java`
- Create: `src/main/java/com/dongqh/luckyhub/reward/support/RewardPrizeTypeMapping.java`
- Modify: `src/main/java/com/dongqh/luckyhub/prize/enums/PrizeType.java`
- Modify: `src/main/java/com/dongqh/luckyhub/reward/service/impl/RewardDefinitionServiceImpl.java`
- Modify: `src/main/java/com/dongqh/luckyhub/prize/dto/CreatePrizeCommand.java`
- Modify: `src/main/java/com/dongqh/luckyhub/prize/dto/UpdatePrizeCommand.java`
- Modify: `src/main/java/com/dongqh/luckyhub/prize/vo/PrizeView.java`
- Modify: `src/main/java/com/dongqh/luckyhub/prize/service/impl/PrizeServiceImpl.java`
- Modify: `src/main/java/com/dongqh/luckyhub/lottery/model/DrawPrizeSnapshot.java`
- Modify: `src/main/java/com/dongqh/luckyhub/lottery/service/impl/DrawEligibilityServiceImpl.java`
- Test: `src/test/java/com/dongqh/luckyhub/reward/RewardSnapshotServiceTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/prize/PrizeRewardBindingTests.java`
- Modify tests: existing reward, prize, activity and eligibility tests whose constructors gain `rewardDefinitionId`
- Document: `docs/progress/阶段5-任务2-奖励快照与奖品绑定完成介绍.md`

**Interfaces:**
- Produces `Map<Long, RewardSnapshot> RewardSnapshotService.resolveForPrizes(List<MarketingPrize> prizes)`; map keys are prize IDs and legacy unbound prizes are absent.
- Produces `PrizeType RewardPrizeTypeMapping.toPrizeType(RewardType type)` and `boolean matches(RewardType, PrizeType)`.
- `RewardSnapshot` contains definition ID/code/type, target ID, quantity, canonical payload JSON and SHA-256 fingerprint.

- [x] **Step 1: Write failing mapping, validation, fingerprint and API tests**

Cover all five mappings, disabled/missing targets, coupon and membership target validation, integer/multiplication overflow, stable canonical JSON/fingerprint, required binding for new prizes, first binding of a legacy prize, forbidden unbind/rebind, and legacy read compatibility.

```java
RewardSnapshot snapshot = snapshots.resolveForPrizes(List.of(boundPrize)).get(boundPrize.getId());
assertThat(snapshot.rewardType()).isEqualTo(RewardType.COUPON);
assertThat(snapshot.payloadJson()).isEqualTo(
        "{\"templateId\":12,\"templateCode\":\"WELCOME\",\"quantity\":2}");
assertThat(snapshot.fingerprint()).matches("[0-9a-f]{64}");
```

- [x] **Step 2: Run RED**

Run `'-Dtest=RewardSnapshotServiceTests,PrizeRewardBindingTests,RewardDefinitionServiceTests,PrizeServiceTests' test`. Expected: compilation/test failure for the absent types and binding rules.

- [x] **Step 3: Implement the five payloads and resolver**

Use records with constructor invariants and this service boundary:

```java
public interface RewardSnapshotService {
    Map<Long, RewardSnapshot> resolveForPrizes(List<MarketingPrize> prizes);
}
```

Load definitions and each target type in batches. Serialize payload records with the project `ObjectMapper`, hash the exact UTF-8 canonical JSON plus immutable definition fields, and reject invalid enabled-state/quantity combinations with `RewardErrorCode.REWARD_TARGET_INVALID` or `REWARD_CONFIG_INVALID`.

- [x] **Step 4: Implement prize binding without removing legacy rows**

Append `@NotNull @Positive Long rewardDefinitionId` to create, append nullable `@Positive Long rewardDefinitionId` to update, expose `rewardDefinitionId` and `rewardType` in `PrizeView`, add `DRAW_CHANCE`, and validate compatibility through `RewardPrizeTypeMapping`. Existing database rows remain nullable; only service-created new rows are required to bind.

- [x] **Step 5: Run GREEN and regress activity/prize APIs**

Run the focused tests plus `'-Dtest=PrizeControllerTests,ActivityPrizeServiceTests,ActivityServiceTests,DrawEligibilityServiceTests' test`. Expected: all pass.

- [x] **Step 6: Document and commit**

Explain why a coupon reward stores both template ID and template code and show a legacy unbound prize beside a bound prize. Commit `feat: snapshot unified lottery rewards`.

### Task 3: Persist reward snapshots and publish compatible events

**Files:**
- Modify: `src/main/java/com/dongqh/luckyhub/lottery/model/DrawResultItem.java`
- Modify: `src/main/java/com/dongqh/luckyhub/lottery/service/impl/DrawTransactionServiceImpl.java`
- Modify: `src/main/java/com/dongqh/luckyhub/lottery/messaging/event/PrizeFulfillmentRequestedEvent.java`
- Modify: `src/main/java/com/dongqh/luckyhub/lottery/vo/DrawResultView.java` only if nullable reward identity is exposed without removing fields
- Test: `src/test/java/com/dongqh/luckyhub/lottery/service/DrawRewardSnapshotTransactionTests.java`
- Modify tests: `DrawTransactionServiceTests`, event serialization tests, lottery query/controller contract tests
- Document: `docs/progress/阶段5-任务3-中奖快照与事件完成介绍.md`

**Interfaces:**
- New-path `PrizeFulfillmentRequestedEvent` adds nullable `rewardDefinitionId`, `rewardType`, and `rewardFingerprint`; its legacy four-argument constructor remains usable.
- Draw record and benefit receive byte-for-byte equal snapshot values in the same transaction.

- [x] **Step 1: Write failing transaction and serialization tests**

Prove a bound win stores equal definition/type/target/quantity/payload/fingerprint in record and benefit, emits the same ID/type/fingerprint, and a legacy win keeps all new fields null and produces a legacy-deserializable payload. Roll back the whole draw if the snapshot is partially populated.

- [x] **Step 2: Run RED**

Run `'-Dtest=DrawRewardSnapshotTransactionTests,DrawTransactionServiceTests,OutboxServiceTests' test`. Expected: assertions fail because snapshot values are not persisted or emitted.

- [x] **Step 3: Implement snapshot propagation**

Extend `DrawPrizeSnapshot`/`DrawResultItem` only with immutable nullable reward data. In `persistResult`, copy the same snapshot into both entities; in `appendFulfillmentEvent`, emit:

```java
new PrizeFulfillmentRequestedEvent(
    item.benefitId(), item.recordId(), item.prizeId(), item.prizeType(),
    reward == null ? null : reward.rewardDefinitionId(),
    reward == null ? null : reward.rewardType(),
    reward == null ? null : reward.fingerprint())
```

The compact constructor accepts either all three new fields or none; mixed presence is rejected.

- [x] **Step 4: Run GREEN and old lottery regressions**

Run focused tests plus `'-Dtest=LotteryServiceTests,LotteryPersistenceContractTests,LotteryControllerTests' test`.

- [x] **Step 5: Document and commit**

Show why changing a reward definition after the win does not alter the stored reward. Commit `feat: persist lottery reward snapshots`.

### Task 4: Persistent rewarded draw-chance account and reservations

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/drawchance/enums/DrawChanceErrorCode.java`
- Create: `src/main/java/com/dongqh/luckyhub/drawchance/dto/DrawChanceReservationCommand.java`
- Create: `src/main/java/com/dongqh/luckyhub/drawchance/model/DrawChanceReservationResult.java`
- Create: `src/main/java/com/dongqh/luckyhub/drawchance/vo/DrawChanceAccountView.java`
- Create: `src/main/java/com/dongqh/luckyhub/drawchance/service/DrawChanceService.java`
- Create: `src/main/java/com/dongqh/luckyhub/drawchance/service/impl/DrawChanceServiceImpl.java`
- Test: `src/test/java/com/dongqh/luckyhub/drawchance/DrawChanceServiceTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/drawchance/DrawChanceConcurrencyTests.java`
- Document: `docs/progress/阶段5-任务4-奖励抽奖次数账户完成介绍.md`

**Interfaces:**

```java
public interface DrawChanceService {
    DrawChanceAccountView credit(long userId, String businessId, long chances);
    DrawChanceReservationResult reserve(DrawChanceReservationCommand command);
    void confirm(String requestId);
    void release(String requestId);
    int reconcileExpired(int limit, LocalDateTime cutoff);
    DrawChanceAccountView get(long userId);
}
```

`DrawChanceReservationResult` returns request identity, bonusReserved, cumulativeBonusForDate, status, and duplicate.

```java
public record DrawChanceReservationCommand(
    String requestId, long activityId, long userId,
    int drawCount, LocalDate drawDate) {}

public record DrawChanceReservationResult(
    String requestId, long activityId, long userId,
    int drawCount, LocalDate drawDate, long bonusReserved,
    long cumulativeBonusForDate,
    DrawChanceReservationStatus status, boolean duplicate) {}
```

- [x] **Step 1: Write failing credit/reserve/settle/concurrency tests**

Test idempotent credit, identity conflict, reserve moves available to reserved, zero-balance reservation, cumulative daily bonus, confirm debit ledger, release restoration ledger, duplicate confirm/release, 20 concurrent reservations never making either balance negative, and stale reconciliation using actual order state.

- [x] **Step 2: Run RED**

Run `'-Dtest=DrawChanceServiceTests,DrawChanceConcurrencyTests' test`. Expected: compilation failure.

- [x] **Step 3: Implement short row-locked transactions**

Use `SELECT ... FOR UPDATE`, exact arithmetic, bounded IDs, enabled-user validation for credit, conditional terminal transitions, and unique-ledger checks before balance mutation. `reserve` creates a zero-bonus row too, so a retry can cross-check all identity fields.

- [x] **Step 4: Run GREEN twice**

Run the focused tests twice to expose leaked state or order dependence. Expected: both runs pass with no duplicate ledgers.

- [x] **Step 5: Document and commit**

Use the approved “1 free + 2 reward chances” example. Commit `feat: account for rewarded draw chances`.

### Task 5: Use rewarded chances in the real draw quota lifecycle

**Files:**
- Modify: `src/main/java/com/dongqh/luckyhub/lottery/service/impl/LotteryServiceImpl.java`
- Modify: `src/main/java/com/dongqh/luckyhub/lottery/service/MessageConsumeService.java`
- Modify: `src/main/java/com/dongqh/luckyhub/lottery/quota/QuotaReservationRequest.java`
- Modify: `src/main/java/com/dongqh/luckyhub/lottery/quota/RedisDrawQuotaService.java`
- Modify: `src/main/java/com/dongqh/luckyhub/lottery/scheduler/LotteryReconciliationScheduler.java`
- Modify: `src/main/java/com/dongqh/luckyhub/lottery/config/LotteryProperties.java`
- Modify: `src/main/resources/application.yaml`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/RewardedDrawQuotaIntegrationTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/RewardedDrawRecoveryTests.java`
- Modify tests: constructor-based lottery orchestration/message/scheduler tests
- Document: `docs/progress/阶段5-任务5-奖励次数接入抽奖完成介绍.md`

**Interfaces:**
- `LotteryServiceImpl` derives one Shanghai `drawDate` from the eligibility snapshot, reserves bonus inside the existing user/activity lock before Redis, and passes the same date plus `Math.addExact(dailyLimit, cumulativeBonusForDate)` to Redis.
- `DRAW_CONFIRMED` confirms Redis then draw-chance reservation; `DRAW_RELEASE_REQUESTED` releases Redis then draw-chance reservation. Both sides are independently idempotent.

- [x] **Step 1: Write failing real MySQL/Redis lifecycle tests**

Cover bonus-first behavior, insufficient ten-draw rejection with bonus restoration, Redis failure restoration, exact request retry, changed identity conflict, success confirmation, draw transaction failure release, and stale reservation reconciliation.

- [x] **Step 2: Run RED**

Run `'-Dtest=RewardedDrawQuotaIntegrationTests,RewardedDrawRecoveryTests' test`. Expected: free daily quota is exceeded or bonus balances remain unchanged because integration is absent.

- [x] **Step 3: Integrate reserve and compensation**

Inside the existing lock:

```java
var bonus = drawChanceService.reserve(new DrawChanceReservationCommand(
    requestId, activityId, userId, drawCount,
    snapshot.snapshotTime().toLocalDate()));
try {
    int effectiveLimit = Math.addExact(snapshot.dailyLimit(),
        Math.toIntExact(bonus.cumulativeBonusForDate()));
    quotaService.reserve(new QuotaReservationRequest(
        requestId, activityId, userId, drawCount,
        bonus.drawDate(), effectiveLimit));
} catch (RuntimeException failure) {
    drawChanceService.release(requestId);
    throw failure;
}
```

Extend `QuotaReservationRequest` with `LocalDate drawDate` and retain a compatibility constructor for existing focused tests. Make `RedisDrawQuotaService` use the request date rather than reading its clock again. Configure a bounded reconciliation batch and interval through `LotteryProperties`.

- [x] **Step 4: Integrate confirmation/release and recovery**

Call the draw-chance transition next to the corresponding Redis transition in `MessageConsumeService`. Add reconciliation after existing draw-order reconciliation; it may confirm only a successful matching order and otherwise release reservations past the processing cutoff.

- [x] **Step 5: Run GREEN and existing quota regressions**

Run focused tests plus `'-Dtest=RedisDrawQuotaServiceTests,LotteryOrchestrationIntegrationTests,LotteryReconciliationServiceTests' test`.

- [x] **Step 6: Document and commit**

Explain why Redis still counts the full draw count while MySQL increases the effective daily ceiling. Commit `feat: consume rewarded draw chances`.

### Task 6: Validate reward event identity, quarantine mismatches and dispatch five rewards

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/lottery/model/ValidatedLotteryReward.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/enums/RewardQuarantineReason.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/service/LotteryRewardDispatchService.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/service/impl/LotteryRewardDispatchServiceImpl.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/service/LotteryRewardIdentityService.java`
- Create: `src/main/java/com/dongqh/luckyhub/lottery/service/impl/LotteryRewardIdentityServiceImpl.java`
- Modify: `src/main/java/com/dongqh/luckyhub/lottery/service/MessageConsumeService.java`
- Modify: `src/main/java/com/dongqh/luckyhub/benefit/mapper/UserBenefitMapper.java`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/LotteryRewardIdentityTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/LotteryRewardDispatchTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/lottery/LotteryRewardSafetyTests.java`
- Document: `docs/progress/阶段5-任务6-奖励事件校验与隔离完成介绍.md`

**Interfaces:**

```java
public interface LotteryRewardDispatchService {
    void dispatch(DrawEventEnvelope envelope, PrizeFulfillmentRequestedEvent payload);
}
public interface LotteryRewardIdentityService {
    ValidatedLotteryReward validate(
        DrawEventEnvelope envelope, PrizeFulfillmentRequestedEvent payload);
}
```

`ValidatedLotteryReward` contains locked benefit identity and its immutable snapshot; it never contains secrets or mutable current reward configuration.

- [x] **Step 1: Write failing cross-identity and zero-effect tests**

Mutate each envelope/payload identity separately: request, user, activity, order, benefit, record, prize, definition, type and fingerprint. Each case must create exactly one quarantine/consume record and zero fulfillment tasks, simulator records, points ledgers, coupons, memberships or draw-chance ledgers. Repeat the same event to prove idempotency.

- [x] **Step 2: Run RED**

Run `'-Dtest=LotteryRewardIdentityTests,LotteryRewardDispatchTests,LotteryRewardSafetyTests' test`. Expected: current consumer trusts only benefitId and the tests fail.

- [x] **Step 3: Implement one locked database identity read**

Use a join across order, record and benefit keyed by payload benefit ID, then compare every approved field to the envelope/payload. Return the immutable database snapshot only on exact match. Map failures to bounded reason enums such as `ORDER_IDENTITY_MISMATCH`, `RECORD_IDENTITY_MISMATCH`, `BENEFIT_IDENTITY_MISMATCH`, and `REWARD_FINGERPRINT_MISMATCH`.

- [x] **Step 4: Implement idempotent dispatch**

- Legacy null snapshot: call existing `BenefitFulfillmentService.fulfill(benefitId,eventId)`.
- `COUPON`, `POINTS`, `MEMBERSHIP`: create `LOTTERY-BENEFIT-{benefitId}` with the matching Phase 4 typed payload, store the fulfillment number, then write consume record.
- `PRODUCT`: conditional `PENDING -> CLAIM_PENDING`, no logistics task, then consume record.
- `DRAW_CHANCE`: `drawChanceService.credit(userId,"LOTTERY-BENEFIT-{benefitId}",quantity)`, conditional `PENDING -> AVAILABLE`, then consume record.
- Validation failure: insert quarantine and consume records in one transaction and return normally.

- [x] **Step 5: Run GREEN and legacy fulfillment regressions**

Run focused tests plus `'-Dtest=BenefitFulfillmentServiceTests,RedisStreamMessagingTests,LotteryEndToEndTests' test`.

- [x] **Step 6: Document and commit**

Show a forged user ID event and prove it creates no provider/local effect. Commit `feat: dispatch validated lottery rewards`.

### Task 7: Project successful fulfillment into usable local assets

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/benefit/service/LotteryRewardProjectionService.java`
- Create: `src/main/java/com/dongqh/luckyhub/benefit/service/impl/LotteryRewardProjectionServiceImpl.java`
- Create: `src/main/java/com/dongqh/luckyhub/benefit/scheduler/LotteryRewardProjectionScheduler.java`
- Modify: `src/main/resources/application.yaml`
- Test: `src/test/java/com/dongqh/luckyhub/benefit/LotteryRewardProjectionTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/benefit/LotteryRewardProjectionConcurrencyTests.java`
- Document: `docs/progress/阶段5-任务7-真实资产投影完成介绍.md`

**Interfaces:**

```java
public interface LotteryRewardProjectionService {
    int projectBatch(int limit);
    void project(long benefitId);
}
```

- [x] **Step 1: Write failing projection tests**

For a `SUCCEEDED` task, assert coupon quantity N yields N deterministic user coupons/issue records, points yields one `LOTTERY_REWARD` ledger, membership quantity N yields N deterministic grant records and accumulated duration, and benefit becomes `AVAILABLE`. Prove repeated/concurrent projection has one set of effects. Prove provider quarantine/termination maps to safe `GRANT_FAILED`, later admin retry success recovers to `AVAILABLE`, and a local projection exception is retryable.

- [x] **Step 2: Run RED**

Run `'-Dtest=LotteryRewardProjectionTests,LotteryRewardProjectionConcurrencyTests' test`. Expected: no local assets are created.

- [x] **Step 3: Implement one-benefit transactional projection**

Lock the benefit, load its fulfillment task, deserialize its immutable reward payload by `rewardType`, then:

```java
case COUPON -> IntStream.rangeClosed(1, quantity).forEach(index ->
    couponService.issue(new IssueCouponCommand(
        fulfillmentNo + "-C-" + index,
        "LR-C-" + benefitId + "-" + index,
        templateId, userId)));
case POINTS -> pointsService.credit(new PointsMutationCommand(
    userId, PointsBusinessType.LOTTERY_REWARD,
    fulfillmentNo, points, "抽奖奖励"));
case MEMBERSHIP -> IntStream.rangeClosed(1, quantity).forEach(index ->
    membershipService.purchase(new PurchaseMembershipCommand(
        fulfillmentNo + "-M-" + index, membershipProductId, userId)));
```

All effect rows and the `AVAILABLE` transition must share one transaction. Bound quantity to 100 so generated IDs stay within schema limits and batches remain bounded.

- [x] **Step 4: Implement bounded scheduling and safe failures**

Scan only `LOTTERY_BENEFIT` tasks joined to benefits in deterministic ID order, maximum configured batch 100. Catch per-benefit failures outside that benefit's transaction; store only `本地资产投影失败` and retry in a later poll.

- [x] **Step 5: Run GREEN and commerce regressions**

Run focused tests plus `'-Dtest=CouponServiceTests,PointsAccountServiceTests,MembershipServiceTests,FulfillmentEndToEndTests' test`.

- [x] **Step 6: Document and commit**

Use a two-coupon reward example showing deterministic business IDs and no duplicates. Commit `feat: project fulfilled lottery assets`.

### Task 8: Query compatibility and five-reward end-to-end acceptance

**Files:**
- Modify: `src/main/java/com/dongqh/luckyhub/benefit/vo/BenefitView.java`
- Modify: `src/main/java/com/dongqh/luckyhub/benefit/service/BenefitQueryServiceImpl.java`
- Modify: `src/test/java/com/dongqh/luckyhub/lottery/LotteryEndToEndTests.java`
- Create: `src/test/java/com/dongqh/luckyhub/lottery/LotteryFiveRewardEndToEndTests.java`
- Create: `src/test/java/com/dongqh/luckyhub/lottery/LotteryRewardConcurrencyTests.java`
- Modify: `src/test/java/com/dongqh/luckyhub/benefit/controller/BenefitControllerTests.java`
- Create: `docs/lottery-reward-fulfillment-api.md`
- Modify: `README.md`
- Document: `docs/progress/阶段5-任务8-五类奖励端到端完成介绍.md`

**Interfaces:**
- `BenefitView` appends nullable `rewardDefinitionId`, `rewardType`, `rewardQuantity`, `fulfillmentNo`, and `fulfillmentStatus`; old fields and meanings remain unchanged.

- [ ] **Step 1: Write the five real end-to-end scenarios**

For each reward, build an enabled activity with stock/weight forcing that win, execute the real draw, relay/consume the Outbox event, run Worker/projector where applicable, and assert:

```text
COUPON      -> sim_coupon_record + coupon_issue_record + user_coupon + AVAILABLE
POINTS      -> sim_points_record + points_ledger + balance + AVAILABLE
MEMBERSHIP  -> sim_membership_record + membership_grant_record + user_membership + AVAILABLE
PRODUCT     -> no sim_logistics_record + CLAIM_PENDING
DRAW_CHANCE -> draw_chance_ledger + account balance + AVAILABLE
```

Repeat event delivery and projection; counts must stay unchanged.

- [ ] **Step 2: Run RED**

Run `'-Dtest=LotteryFiveRewardEndToEndTests,LotteryRewardConcurrencyTests,BenefitControllerTests' test`. Expected: incomplete query fields and/or missing end-to-end effects.

- [ ] **Step 3: Add compatible query projection and API guide**

Left join fulfillment task by `fulfillment_no`; return null new fields for legacy benefits. Document reward configuration, state timelines, legacy behavior, identity quarantine, draw-chance use, PowerShell examples, and the explicit Phase 6 logistics boundary.

- [ ] **Step 4: Run Phase 5 focused acceptance**

Run this exact command and record its test count in the task document:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 '-Dtest=RewardSnapshotServiceTests,PrizeRewardBindingTests,DrawRewardSnapshotTransactionTests,DrawChanceServiceTests,DrawChanceConcurrencyTests,RewardedDrawQuotaIntegrationTests,RewardedDrawRecoveryTests,LotteryRewardIdentityTests,LotteryRewardDispatchTests,LotteryRewardSafetyTests,LotteryRewardProjectionTests,LotteryRewardProjectionConcurrencyTests,LotteryFiveRewardEndToEndTests,LotteryRewardConcurrencyTests,LotteryEndToEndTests,FulfillmentEndToEndTests,CouponServiceTests,PointsAccountServiceTests,MembershipServiceTests' test
```

- [ ] **Step 5: Document and commit**

Explain all five timelines with examples. Commit `test: verify five lottery reward flows`.

### Task 9: Empty-schema migration, complete regression and handoff

**Files:**
- Create: `scripts/Verify-Phase5FreshMigration.ps1`
- Modify: `src/test/java/com/dongqh/luckyhub/infrastructure/DatabaseSchemaMigrationTests.java`
- Modify: `src/test/java/com/dongqh/luckyhub/lottery/LotteryMigrationGuardTests.java`
- Modify: `docs/LuckyHub-迷你商城下一阶段执行总路线.md`
- Modify: `docs/LuckyHub-开发进度交接总结.md`
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-08-09-lottery-unified-reward-fulfillment.md`
- Document: `docs/progress/阶段5-任务9-阶段交付完成介绍.md`

**Interfaces:**
- Produces a runnable V1-V16 project and makes Phase 6 address/shipping workflow the only next mainline.

- [ ] **Step 1: Update global migration guards and create a safe fresh-schema script**

Assert V16 and exactly 43 business tables. The PowerShell script must generate a random `luckyhub_phase5_verify_<guid>` name, create it separately from granting, track whether creation succeeded, run schema tests, verify `16|43`, and in `finally` revoke only that grant and drop only that created schema; cleanup failure must fail the script.

- [ ] **Step 2: Run fresh migration and focused concurrency**

Run:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Verify-Phase5FreshMigration.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 '-Dtest=LotteryFiveRewardEndToEndTests,LotteryRewardConcurrencyTests,DrawChanceConcurrencyTests,LotteryRewardProjectionConcurrencyTests' test
```

Record exact successes and verify no `luckyhub_phase5_verify_%` schema remains.

- [ ] **Step 3: Run full regression and package**

Run fresh commands, not cached claims:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 test
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 package '-DskipTests'
```

Require zero failures/errors/skips unless a test explicitly documents an intentional skip. Record exact test count and JAR byte size.

- [ ] **Step 4: Run executable JAR OpenAPI smoke and static delivery checks**

Start `target/luckyhub-0.0.1-SNAPSHOT.jar` on a free local port with the configured MySQL/Redis, wait for `/v3/api-docs`, assert lottery/reward/benefit endpoints exist, then stop only that process. Run `git diff --check`, strict UTF-8 decoding, Markdown local-link validation, tracked secret-shape scan, actual Phase 5 checkbox scan, and confirm all 9 task documents exist.

- [ ] **Step 5: Perform final requirement/code review**

Review the full diff from `bc5ccd0` for Critical/Important issues in identity validation, transaction boundaries, provider idempotency, projection idempotency, draw-chance accounting, secret leakage and Phase 6 scope. Reproduce every found defect with a failing test before fixing it, then rerun affected and full verification.

- [ ] **Step 6: Update handoff, check all boxes, document and commit**

Mark Phase 5 complete, record V16/43 tables, exact tests/JAR/OpenAPI results and known boundaries, and make Phase 6 the next route. Commit `docs: hand off phase five lottery rewards`.

## Completion Boundary

Phase 5 is complete only when every box is checked, all 9 Chinese task documents exist, five rewards pass real end-to-end tests, mismatched events have zero provider/local effects and one quarantine record, rewarded chances survive concurrency and compensation, V1-V16 migrate from an empty schema, the full suite and executable JAR pass, tracked worktree is clean, and no Critical/Important finding remains. Addresses and logistics execution remain unimplemented for Phase 6.
