# Unified Fulfillment and Simulated Gateways Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a production-shaped asynchronous fulfillment engine with stable idempotency, transaction-free Gateway calls, retry/reconciliation/quarantine, four replaceable Gateways, four independent local simulators, and protected operations APIs.

**Architecture:** `fulfillment` owns task state, leasing, attempts, quarantine, scheduling and admin operations. `integration.gateway` contains four typed ports; `integration.simulator` implements them against independent MySQL simulator tables and injectable failure rules. A Worker claims in a short transaction, calls a Gateway with no active transaction, and records the result in a second short transaction.

**Tech Stack:** Java 17, Spring Boot 4.1, MyBatis-Plus, MySQL 8.4, Flyway V14/V15, Spring scheduling, Jackson, JUnit 5, AssertJ, MockMvc.

## Global Constraints

- Do not modify published migrations V1-V13; only add V14 and V15.
- Do not migrate lottery benefits to fulfillment, create addresses/shipments, or implement real external providers.
- All external idempotency uses stable `fulfillmentNo`; database uniqueness remains the final defense.
- Never keep a transaction or row lock open while calling a Gateway.
- Persist only masked logistics input and bounded safe error summaries; never persist secrets or raw stack traces.
- Each task ends with a Chinese document containing: why it exists, what it does, concrete implementation, example, and test evidence.
- Use PowerShell and `scripts/Invoke-Maven.ps1`; preserve untracked `.codex-progress/` and `.superpowers/`.

## File Structure

| Area | Responsibility |
|---|---|
| `fulfillment/entity,mapper,enums` | Durable task, attempt and quarantine state |
| `fulfillment/dto,model,vo` | Typed application commands, claims and responses |
| `fulfillment/service` | Creation/query/manual operations and transactional transitions |
| `fulfillment/worker,scheduler` | Transaction-free Gateway execution and bounded polling |
| `integration/gateway` | Four provider-independent ports and typed request/result contracts |
| `integration/simulator` | Four local adapters, provider-owned records and fault injection |
| `fulfillment/controller` | Permission-protected admin/test operations |

---

### Task 1: Fulfillment persistence and domain contracts

**Files:**
- Create: `src/main/resources/db/migration/V14__add_fulfillment_engine.sql`
- Create: `src/main/resources/db/migration/V15__add_fulfillment_simulators.sql`
- Create: `src/main/java/com/dongqh/luckyhub/fulfillment/{entity,enums,mapper}/...`
- Modify: `src/main/java/com/dongqh/luckyhub/config/MybatisPlusConfig.java`
- Test: `src/test/java/com/dongqh/luckyhub/fulfillment/FulfillmentSchemaContractTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/fulfillment/FulfillmentDomainContractTests.java`
- Document: `docs/progress/阶段4-任务1-履约数据库与领域骨架完成介绍.md`

**Interfaces:**
- Produces `FulfillmentTaskMapper`, `FulfillmentAttemptMapper`, `FulfillmentQuarantineMapper` and enums `FulfillmentType`, `FulfillmentStatus`, `AttemptOperation`, `GatewayOutcome`, `FailureCategory`.

- [x] Write schema/domain tests requiring V14/V15, 8 tables, unique fulfillment numbers, task due/lease indexes, four permissions and stable error codes 52001-52010.
- [x] Run `'-Dtest=FulfillmentSchemaContractTests,FulfillmentDomainContractTests' test`; verify RED because migrations/types are missing.
- [x] Add V14 task/attempt/quarantine tables and permissions; add V15 four provider record tables and `sim_failure_rule`; add entities, enums, errors and mappers.
- [x] Add fulfillment/simulator mapper scans; rerun the two tests GREEN.
- [x] Write the Chinese explanation, check Task 1 boxes, commit `feat: add fulfillment persistence`.

Required state values:

```java
enum FulfillmentStatus { PENDING, PROCESSING, RETRY_WAITING, RECONCILING, SUCCEEDED, QUARANTINED, TERMINATED }
enum GatewayOutcome { SUCCEEDED, RETRYABLE_FAILURE, PERMANENT_FAILURE, UNKNOWN, NOT_FOUND }
enum AttemptOperation { EXECUTE, QUERY }
enum FulfillmentType { COUPON, POINTS, MEMBERSHIP, LOGISTICS }
```

### Task 2: Typed Gateway ports and safe result model

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/integration/gateway/...`
- Test: `src/test/java/com/dongqh/luckyhub/integration/GatewayContractTests.java`
- Document: `docs/progress/阶段4-任务2-Gateway契约完成介绍.md`

**Interfaces:**
- Produces `CouponGateway`, `PointsGateway`, `MembershipGateway`, `LogisticsGateway`, typed request records and `GatewayResult`.
- Each port exposes `execute(request)` and `query(fulfillmentNo)`.

- [x] Write contract tests that construct all four typed requests, require masked logistics validation, and verify immutable normalized results.
- [x] Run `'-Dtest=GatewayContractTests' test`; verify RED for missing Gateway types.
- [x] Implement four narrow interfaces, `GatewayResult`, `GatewayOutcome`, request validation and strongly typed request conversion without a generic unvalidated Map.
- [x] Run contract tests GREEN; document a future real-provider replacement example.
- [x] Check boxes and commit `feat: define fulfillment gateways`.

Required signatures:

```java
interface CouponGateway { GatewayResult execute(CouponGrantRequest request); GatewayResult query(String fulfillmentNo); }
interface PointsGateway { GatewayResult execute(PointsGrantRequest request); GatewayResult query(String fulfillmentNo); }
interface MembershipGateway { GatewayResult execute(MembershipGrantRequest request); GatewayResult query(String fulfillmentNo); }
interface LogisticsGateway { GatewayResult execute(LogisticsCreateRequest request); GatewayResult query(String fulfillmentNo); }
record GatewayResult(GatewayOutcome outcome, String externalReference, String errorCode, String safeMessage) {}
```

### Task 3: Four idempotent simulated providers and failure rules

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/integration/simulator/...`
- Test: `src/test/java/com/dongqh/luckyhub/integration/SimulatorGatewayTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/integration/SimulatorConcurrencyTests.java`
- Document: `docs/progress/阶段4-任务3-四套模拟供应方完成介绍.md`

**Interfaces:**
- Consumes the four Gateway ports and V15 tables.
- Produces `SimulatorFailureRuleService.configure(type, mode, count)` and four `@Service` Gateway adapters.

- [ ] Write failing integration tests for each provider success/query, duplicate execute idempotency, parameter conflict, 20-thread duplicate calls, retryable/permanent/unknown-before/unknown-after-success modes.
- [ ] Run `'-Dtest=SimulatorGatewayTests,SimulatorConcurrencyTests' test`; verify RED.
- [ ] Implement provider-owned persistence with SHA-256 request fingerprints, unique `fulfillment_no`, deterministic external references, atomic fault-rule consumption and safe messages.
- [ ] Prove `UNKNOWN_AFTER_SUCCESS` persists one provider record before returning UNKNOWN, then query returns SUCCEEDED.
- [ ] Run simulator tests GREEN, document coupon/points/member/logistics examples, check boxes and commit `feat: simulate fulfillment providers`.

Failure modes:

```java
enum SimulatorFailureMode { SUCCESS, RETRYABLE, PERMANENT, UNKNOWN_BEFORE, UNKNOWN_AFTER_SUCCESS }
```

### Task 4: Task creation, snapshots, idempotency and queries

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/fulfillment/dto/...`
- Create: `src/main/java/com/dongqh/luckyhub/fulfillment/model/FulfillmentPayload.java`
- Create: `src/main/java/com/dongqh/luckyhub/fulfillment/service/FulfillmentTaskService.java`
- Create: `src/main/java/com/dongqh/luckyhub/fulfillment/service/impl/FulfillmentTaskServiceImpl.java`
- Create: `src/main/java/com/dongqh/luckyhub/fulfillment/vo/...`
- Test: `src/test/java/com/dongqh/luckyhub/fulfillment/FulfillmentTaskServiceTests.java`
- Document: `docs/progress/阶段4-任务4-履约任务与幂等完成介绍.md`

**Interfaces:**
- Produces `create`, `get`, `page`, `retryQuarantined`, `terminate`.

- [ ] Write failing tests for four payload validations, canonical request fingerprint, same-command idempotency, conflicting reuse, snapshots, pagination and source/target identity.
- [ ] Run `'-Dtest=FulfillmentTaskServiceTests' test`; verify RED.
- [ ] Implement JSON snapshot/fingerprint creation, unique-key race handling, views and stable errors; creation performs no Gateway call.
- [ ] Run tests including concurrent duplicate creation GREEN.
- [ ] Document why creation and execution are separated, check boxes and commit `feat: create fulfillment tasks`.

Required command core:

```java
record CreateFulfillmentTaskCommand(
  String fulfillmentNo, String sourceType, String sourceId,
  FulfillmentType fulfillmentType, Long targetUserId,
  FulfillmentPayload payload, Integer maxAttempts) {}
```

### Task 5: Lease claiming and transaction-free Worker execution

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/fulfillment/service/FulfillmentStateService.java`
- Create: `src/main/java/com/dongqh/luckyhub/fulfillment/service/impl/FulfillmentStateServiceImpl.java`
- Create: `src/main/java/com/dongqh/luckyhub/fulfillment/worker/FulfillmentWorker.java`
- Test: `src/test/java/com/dongqh/luckyhub/fulfillment/FulfillmentWorkerTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/fulfillment/FulfillmentLeaseConcurrencyTests.java`
- Document: `docs/progress/阶段4-任务5-租约与事务外调用完成介绍.md`

**Interfaces:**
- `claimDue(limit, leaseDuration)` returns immutable `FulfillmentClaim` values.
- `recordResult(claim, operation, result, duration)` validates the lease token and appends one attempt.
- `FulfillmentWorker.runBatch()` calls Gateways outside a transaction.

- [ ] Write failing tests proving only one of 20 claimers wins, stale lease tokens cannot complete, each success creates one attempt, and the Gateway observes `TransactionSynchronizationManager.isActualTransactionActive() == false`.
- [ ] Run `'-Dtest=FulfillmentWorkerTests,FulfillmentLeaseConcurrencyTests' test`; verify RED.
- [ ] Implement conditional SQL claims, random lease tokens, bounded batch processing, typed routing and short transactional state transitions.
- [ ] Run worker/lease tests GREEN and relevant simulator regressions.
- [ ] Document a two-Worker example, check boxes and commit `feat: execute fulfillment outside transactions`.

### Task 6: Retry, reconciliation, lease recovery and quarantine

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/fulfillment/config/FulfillmentProperties.java`
- Create: `src/main/java/com/dongqh/luckyhub/fulfillment/scheduler/FulfillmentScheduler.java`
- Modify: `src/main/resources/application.yaml`
- Test: `src/test/java/com/dongqh/luckyhub/fulfillment/FulfillmentRecoveryTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/fulfillment/FulfillmentEndToEndTests.java`
- Document: `docs/progress/阶段4-任务6-重试对账与隔离完成介绍.md`

**Interfaces:**
- Adds exponential backoff, `recoverExpiredLeases(limit)`, reconciliation query routing and bounded scheduled polling.

- [ ] Write failing tests for retry delay, permanent quarantine, max-attempt quarantine, UNKNOWN query success, UNKNOWN query not-found then execute, and expired PROCESSING lease becoming RECONCILING.
- [ ] Run `'-Dtest=FulfillmentRecoveryTests,FulfillmentEndToEndTests' test`; verify RED.
- [ ] Implement configured base/max delay, attempt limits, quarantine upsert, lease recovery and scheduler with default batch 50.
- [ ] Prove UNKNOWN_AFTER_SUCCESS creates one simulated record and ends SUCCEEDED through QUERY, never a second EXECUTE effect.
- [ ] Run recovery/end-to-end tests GREEN, document the lost-response example, check boxes and commit `feat: reconcile fulfillment outcomes`.

### Task 7: Admin APIs, RBAC and safe operations

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/fulfillment/controller/FulfillmentAdminController.java`
- Create: `src/main/java/com/dongqh/luckyhub/integration/simulator/controller/SimulatorAdminController.java`
- Modify: `src/main/java/com/dongqh/luckyhub/rbac/constant/PermissionCodes.java`
- Test: `src/test/java/com/dongqh/luckyhub/fulfillment/FulfillmentControllerTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/fulfillment/FulfillmentSecurityChainIntegrationTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/fulfillment/FulfillmentSafetyTests.java`
- Document: `docs/progress/阶段4-任务7-履约管理API与安全完成介绍.md`

**Interfaces:**
- Exposes the six approved `/api/admin/fulfillment/**` and `/api/admin/simulators/**` endpoints.

- [ ] Write failing MockMvc tests for 201/200, validation, pagination, retry/terminate, fault control, 401/403 and exact permissions.
- [ ] Add safety tests that reject unmasked phone/receiver, bound errors, and scan task/attempt/provider rows for secret or full-address leakage.
- [ ] Run `'-Dtest=FulfillmentControllerTests,FulfillmentSecurityChainIntegrationTests,FulfillmentSafetyTests' test`; verify RED.
- [ ] Add thin controllers, permission constants and service operations; existing `/api/admin/*` filter/interceptor mappings already protect the paths.
- [ ] Run API/security and lottery security regressions GREEN, document PowerShell operations, check boxes and commit `feat: operate fulfillment tasks`.

### Task 8: Concurrency, documentation and complete handoff

**Files:**
- Create: `docs/fulfillment-gateway-api.md`
- Modify: `README.md`
- Modify: `docs/LuckyHub-迷你商城下一阶段执行总路线.md`
- Modify: `docs/LuckyHub-开发进度交接总结.md`
- Modify: `src/test/java/com/dongqh/luckyhub/infrastructure/DatabaseSchemaMigrationTests.java`
- Modify: `src/test/java/com/dongqh/luckyhub/lottery/LotteryMigrationGuardTests.java`
- Document: `docs/progress/阶段4-任务8-阶段交付完成介绍.md`

**Interfaces:**
- Produces a runnable V1-V15 project and sets Phase 5 as the next planning stage.

- [ ] Add/repeat full concurrency and four-provider end-to-end scenarios; run the Phase 4 focused suite until GREEN.
- [ ] Migrate an empty temporary schema V1->V15, revoke its grant and drop only that schema in `finally`.
- [ ] Run full `test`, `package '-DskipTests'`, `git diff --check`, UTF-8/link/secret checks and JAR OpenAPI smoke.
- [ ] Record exact test counts, artifact size, Critical/Important review and known boundaries in all handoff documents.
- [ ] Verify every plan checkbox and every task explanation document, commit `docs: hand off phase four fulfillment`.

## Completion Boundary

Phase 4 is complete only when all boxes are checked, every task has its required Chinese explanation, V1-V15 migrate from an empty schema, focused and full tests pass, the executable JAR exposes fulfillment OpenAPI paths, tracked worktree is clean, and no Critical/Important idempotency, transaction-boundary, retry, secret-leak or poison-task finding remains. Phase 5 lottery integration and Phase 6 address/shipping workflow must remain unimplemented.
