# LuckyHub Points Account and Redemption Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a database-backed points account, immutable points ledger, and single-SKU points redemption flow that atomically coordinates points and the existing `POINTS` channel inventory.

**Architecture:** Keep the modular Spring Boot monolith. The `points` package owns accounts, ledgers, redemption orders, user/admin APIs, and transaction orchestration. It consumes a read-only redeemable SKU snapshot from `catalog` and the existing `ChannelInventoryService`; MySQL is the final asset ledger, while Redis is not used for point balances. A completed redemption can only be compensated by a new reversal ledger plus a confirmed-inventory reversal—never by editing history.

**Tech Stack:** Java 17, Spring Boot 4.1.0, MyBatis-Plus, MySQL 8.4, Flyway, Jakarta Validation, JUnit 5, AssertJ, Spring MockMvc, Maven through `scripts/Invoke-Maven.ps1`.

## Global Constraints

- Work on the current `master`; the user already approved direct development without a worktree.
- Use Windows PowerShell 7 and UTF-8. Run Maven only through `scripts/Invoke-Maven.ps1`.
- Do not modify V1-V9. All schema changes in this phase go into `V10__add_points_account_and_redemption.sql`.
- Points are signed Java `long` values stored as signed MySQL `BIGINT`; balances and mutation amounts must stay in `[0, Long.MAX_VALUE]`.
- Points are not cash, cannot be withdrawn, and are never mixed with cash payment.
- A redemption uses integer points only, does not apply coupons or membership discounts, and does not award consumption points.
- Support one SKU per redemption request. Do not build a cart.
- Use channel code `POINTS` exactly. Never borrow stock from `MALL` or `LOTTERY:{activityId}`.
- Every points mutation uses the unique identity `business_type + business_id` and an immutable ledger row.
- Debit uses a conditional SQL update and must never produce a negative balance.
- Reversal adds a new `REVERSAL` ledger row and links it to the original debit. Never update or delete the original ledger.
- Redemption creation and compensation are local MySQL transactions. Do not introduce HTTP calls, Gateway implementations, Outbox events, coupons, memberships, payment, addresses, or logistics.
- Existing lottery, activity prize inventory, benefit handlers, and V1-V9 behavior must remain unchanged.
- After every completed task, create `docs/progress/阶段2-任务N-<主题>完成介绍.md`, include examples, link it from this plan, and commit it with that task.
- Before every completion claim or commit, run the task verification and `git diff --check`.

---

## Planning Decisions

### Why use a points-specific redemption order

Three approaches were considered:

1. **Points-specific redemption order (selected):** closes the Phase 2 loop without prematurely implementing Phase 3 cash orders, pricing, coupons, membership, or payment.
2. **Implement the future generic order model now:** would mix Phase 3 scope into Phase 2 and force pricing/payment decisions too early.
3. **Use only points and inventory ledgers with no order:** would not provide a stable user-visible redemption number, snapshot, status, query API, or compensation anchor.

`points_redemption_order` is owned by the `points` module and is intentionally smaller than the later general order model. Phase 3 must not silently reuse it as a cash order.

### Redemption lifecycle

```text
PROCESSING -> COMPLETED -> REVERSED
```

- `PROCESSING` exists only inside the creation transaction.
- A successful API response always returns `COMPLETED`.
- If reserve, debit, confirm, or order transition fails, the whole creation transaction rolls back.
- A later business failure uses the admin reversal operation and changes `COMPLETED -> REVERSED` atomically with points and inventory compensation.
- Repeating the same creation or reversal number is idempotent. Reusing it with different identity is a conflict.

### Points mutation identity

```text
LOTTERY_REWARD     future Phase 5 credit
ORDER_REWARD       future Phase 3 credit
MEMBERSHIP_BONUS   future Phase 3 credit
REDEMPTION         Phase 2 debit
REVERSAL           Phase 2 compensation credit
MANUAL_ADJUSTMENT  Phase 2 admin credit/debit
```

The schema supports all approved business types, but Phase 2 exposes only manual adjustment, redemption debit, and redemption reversal.

---

## File Structure

### Migration and configuration

```text
src/main/resources/db/migration/V10__add_points_account_and_redemption.sql
src/main/java/com/dongqh/luckyhub/config/MybatisPlusConfig.java
src/main/java/com/dongqh/luckyhub/rbac/constant/PermissionCodes.java
```

### Points domain

```text
src/main/java/com/dongqh/luckyhub/points/
├─ controller/
│  ├─ PointsController.java
│  ├─ PointsRedemptionController.java
│  └─ PointsAdminController.java
├─ dto/
│  ├─ AdminPointsAdjustmentCommand.java
│  ├─ CreatePointsRedemptionCommand.java
│  ├─ PointsLedgerQuery.java
│  ├─ PointsMutationCommand.java
│  ├─ PointsRedemptionQuery.java
│  ├─ PointsReversalCommand.java
│  └─ ReversePointsRedemptionCommand.java
├─ entity/
│  ├─ PointsAccount.java
│  ├─ PointsLedger.java
│  └─ PointsRedemptionOrder.java
├─ enums/
│  ├─ PointsBusinessType.java
│  ├─ PointsDirection.java
│  ├─ PointsErrorCode.java
│  └─ PointsRedemptionStatus.java
├─ mapper/
│  ├─ PointsAccountMapper.java
│  ├─ PointsLedgerMapper.java
│  └─ PointsRedemptionOrderMapper.java
├─ service/
│  ├─ PointsAccountService.java
│  ├─ PointsQueryService.java
│  ├─ PointsRedemptionService.java
│  └─ impl/
│     ├─ PointsAccountServiceImpl.java
│     ├─ PointsQueryServiceImpl.java
│     └─ PointsRedemptionServiceImpl.java
└─ vo/
   ├─ PointsAccountView.java
   ├─ PointsLedgerView.java
   └─ PointsRedemptionView.java
```

### Existing packages extended

```text
src/main/java/com/dongqh/luckyhub/catalog/model/RedeemableSkuSnapshot.java
src/main/java/com/dongqh/luckyhub/catalog/service/CatalogService.java
src/main/java/com/dongqh/luckyhub/catalog/service/impl/CatalogServiceImpl.java
src/main/java/com/dongqh/luckyhub/inventory/channel/enums/InventoryOperation.java
src/main/java/com/dongqh/luckyhub/inventory/channel/enums/InventoryReservationStatus.java
src/main/java/com/dongqh/luckyhub/inventory/channel/mapper/ChannelInventoryMapper.java
src/main/java/com/dongqh/luckyhub/inventory/channel/service/ChannelInventoryService.java
src/main/java/com/dongqh/luckyhub/inventory/channel/service/impl/ChannelInventoryServiceImpl.java
```

---

## Task 1: Add V10 points and redemption schema

**Files:**
- Create: `src/main/resources/db/migration/V10__add_points_account_and_redemption.sql`
- Create: `src/test/java/com/dongqh/luckyhub/points/PointsSchemaContractTests.java`
- Modify: `src/test/java/com/dongqh/luckyhub/infrastructure/DatabaseSchemaMigrationTests.java`
- Create: `docs/progress/阶段2-任务1-V10积分与兑换数据库完成介绍.md`

**Interfaces:**
- Consumes: V1-V9, `sys_user`, `product_sku`, and V9 inventory tables.
- Produces: `points_account`, `points_ledger`, `points_redemption_order`, three permissions, and inventory reversal-compatible CHECK constraints.

- [ ] **Step 1: Write the failing V10 schema contract**

Create tests that assert:

```java
assertThat(tableExists("points_account")).isTrue();
assertThat(tableExists("points_ledger")).isTrue();
assertThat(tableExists("points_redemption_order")).isTrue();
assertThat(uniqueIndexes()).contains(
        "points_account:user_id",
        "points_ledger:business_type,business_id",
        "points_ledger:reversal_of_ledger_id",
        "points_redemption_order:redemption_no",
        "points_redemption_order:reversal_no"
);
assertThat(rolePermissions("USER")).contains("points:read", "points:redeem");
assertThat(rolePermissions("ADMIN")).contains("points:read", "points:redeem", "points:adjust");
assertThat(checkClause("inventory_reservation", "chk_inventory_reservation_status"))
        .contains("REVERSED");
assertThat(checkClause("inventory_ledger", "chk_inventory_ledger_operation"))
        .contains("RETURN");
```

- [ ] **Step 2: Run the contract and verify RED**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=PointsSchemaContractTests' test
```

Expected: FAIL because V10 tables and permissions do not exist.

- [ ] **Step 3: Create the exact V10 migration**

Use this schema. Do not add foreign keys; the existing project uses application validation and indexes without FK coupling.

```sql
CREATE TABLE points_account (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    balance BIGINT NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_points_account_user (user_id),
    CONSTRAINT chk_points_account_balance CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户积分账户';

CREATE TABLE points_ledger (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    business_type VARCHAR(30) NOT NULL,
    business_id VARCHAR(100) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    reversal_of_ledger_id BIGINT UNSIGNED NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_points_ledger_business (business_type, business_id),
    UNIQUE KEY uk_points_ledger_reversal (reversal_of_ledger_id),
    KEY idx_points_ledger_user_created (user_id, created_at, id),
    CONSTRAINT chk_points_ledger_business_type CHECK (
        business_type IN ('LOTTERY_REWARD', 'ORDER_REWARD', 'MEMBERSHIP_BONUS',
                          'REDEMPTION', 'REVERSAL', 'MANUAL_ADJUSTMENT')
    ),
    CONSTRAINT chk_points_ledger_direction CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_points_ledger_amount CHECK (amount > 0),
    CONSTRAINT chk_points_ledger_balance CHECK (balance_after >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变积分流水';

CREATE TABLE points_redemption_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    redemption_no VARCHAR(64) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    sku_id BIGINT UNSIGNED NOT NULL,
    quantity INT UNSIGNED NOT NULL,
    unit_points BIGINT NOT NULL,
    total_points BIGINT NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    sku_name VARCHAR(100) NOT NULL,
    product_type VARCHAR(20) NOT NULL,
    image_url VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL,
    reversal_no VARCHAR(64) NULL,
    failure_reason VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_points_redemption_no (redemption_no),
    UNIQUE KEY uk_points_redemption_reversal (reversal_no),
    KEY idx_points_redemption_user_created (user_id, created_at, id),
    CONSTRAINT chk_points_redemption_quantity CHECK (quantity > 0),
    CONSTRAINT chk_points_redemption_price CHECK (unit_points > 0 AND total_points > 0),
    CONSTRAINT chk_points_redemption_type CHECK (product_type IN ('PHYSICAL', 'VIRTUAL')),
    CONSTRAINT chk_points_redemption_status CHECK (
        status IN ('PROCESSING', 'COMPLETED', 'REVERSED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='积分兑换单';

ALTER TABLE inventory_reservation
    DROP CHECK chk_inventory_reservation_status,
    ADD CONSTRAINT chk_inventory_reservation_status CHECK (
        status IN ('RESERVED', 'CONFIRMED', 'RELEASED', 'REVERSED')
    );

ALTER TABLE inventory_ledger
    DROP CHECK chk_inventory_ledger_operation,
    ADD CONSTRAINT chk_inventory_ledger_operation CHECK (
        operation IN ('INITIALIZE', 'ALLOCATE', 'RESERVE', 'CONFIRM', 'RELEASE', 'RETURN')
    );

INSERT INTO sys_permission (permission_code, permission_name)
SELECT seed.permission_code, seed.permission_name
FROM (
    SELECT 'points:read' AS permission_code, '查询本人积分与兑换记录' AS permission_name
    UNION ALL SELECT 'points:redeem', '创建积分兑换'
    UNION ALL SELECT 'points:adjust', '管理积分调整与兑换冲正'
) seed
LEFT JOIN sys_permission existing_permission
    ON existing_permission.permission_code = seed.permission_code
WHERE existing_permission.id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role_record.id, permission.id
FROM sys_role role_record
JOIN sys_permission permission ON permission.permission_code IN ('points:read', 'points:redeem')
LEFT JOIN sys_role_permission existing_relation
    ON existing_relation.role_id = role_record.id
    AND existing_relation.permission_id = permission.id
WHERE role_record.role_code IN ('USER', 'ADMIN')
  AND existing_relation.role_id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT admin_role.id, permission.id
FROM sys_role admin_role
JOIN sys_permission permission ON permission.permission_code = 'points:adjust'
LEFT JOIN sys_role_permission existing_relation
    ON existing_relation.role_id = admin_role.id
    AND existing_relation.permission_id = permission.id
WHERE admin_role.role_code = 'ADMIN'
  AND existing_relation.role_id IS NULL;
```

- [ ] **Step 4: Run V10 contracts and migration regression**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=PointsSchemaContractTests,ChannelInventorySchemaContractTests,DatabaseSchemaMigrationTests,LotteryMigrationGuardTests' test
```

Expected: PASS with V1-V9 checksum guards unchanged and one successful V10 migration.

- [ ] **Step 5: Verify V1-V9 are untouched**

```powershell
git diff 230ea93 -- src/main/resources/db/migration/V1__create_luckyhub_schema.sql `
  src/main/resources/db/migration/V2__initialize_admin_role_and_base_permissions.sql `
  src/main/resources/db/migration/V3__add_prize_management_permissions.sql `
  src/main/resources/db/migration/V4__add_activity_management_permissions.sql `
  src/main/resources/db/migration/V5__add_lottery_core.sql `
  src/main/resources/db/migration/V6__add_outbox_delivery_error.sql `
  src/main/resources/db/migration/V7__lease_outbox_delivery.sql `
  src/main/resources/db/migration/V8__add_catalog_and_reward_foundation.sql `
  src/main/resources/db/migration/V9__add_channel_inventory.sql
```

Expected: no output.

- [ ] **Step 6: Write the task introduction and commit**

The introduction must explain account/ledger/order tables, unique business identity, why reversal is additive history, V10 inventory CHECK changes, permissions, and a concrete balance example.

```powershell
git add -- src/main/resources/db/migration/V10__add_points_account_and_redemption.sql `
  src/test/java/com/dongqh/luckyhub/points/PointsSchemaContractTests.java `
  src/test/java/com/dongqh/luckyhub/infrastructure/DatabaseSchemaMigrationTests.java `
  docs/progress/阶段2-任务1-V10积分与兑换数据库完成介绍.md `
  docs/superpowers/plans/2026-08-08-points-account-redemption.md
git commit -m "feat: add points and redemption schema"
```

---

## Task 2: Define points domain contracts and persistence mappings

**Files:**
- Create: every `points/entity`, `points/enums`, `points/mapper`, internal DTO, and VO file listed in File Structure.
- Modify: `src/main/java/com/dongqh/luckyhub/config/MybatisPlusConfig.java`
- Modify: `src/main/java/com/dongqh/luckyhub/rbac/constant/PermissionCodes.java`
- Create: `src/test/java/com/dongqh/luckyhub/points/PointsDomainContractTests.java`
- Create: `docs/progress/阶段2-任务2-积分领域契约完成介绍.md`

**Interfaces:**
- Consumes: V10 columns and project MyBatis-Plus conventions.
- Produces: stable Java types used by Tasks 3-7.

- [ ] **Step 1: Write the domain contract test**

Assert exact enum values, table annotations, mapper scan, permission constants, immutable VO records, and these command signatures:

```java
public record PointsMutationCommand(
        Long userId,
        PointsBusinessType businessType,
        String businessId,
        Long amount,
        String remark
) {}

public record PointsReversalCommand(
        Long userId,
        PointsBusinessType originalBusinessType,
        String originalBusinessId,
        String reversalBusinessId,
        String remark
) {}

public record AdminPointsAdjustmentCommand(
        @NotNull @Positive Long userId,
        @NotNull Long delta,
        @NotBlank @Size(max = 100) String businessId,
        @NotBlank @Size(max = 500) String reason
) {}
```

`AdminPointsAdjustmentCommand` compact constructor must reject `delta == 0` and `delta == Long.MIN_VALUE`.

- [ ] **Step 2: Run the test and verify RED**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=PointsDomainContractTests' test
```

Expected: FAIL because the `points` domain does not exist.

- [ ] **Step 3: Add enums and stable errors**

```java
public enum PointsBusinessType {
    LOTTERY_REWARD, ORDER_REWARD, MEMBERSHIP_BONUS,
    REDEMPTION, REVERSAL, MANUAL_ADJUSTMENT
}

public enum PointsDirection { CREDIT, DEBIT }

public enum PointsRedemptionStatus { PROCESSING, COMPLETED, REVERSED }

public enum PointsErrorCode implements ErrorCode {
    POINTS_INSUFFICIENT(47001, "积分余额不足", HttpStatus.CONFLICT),
    POINTS_IDEMPOTENCY_CONFLICT(47002, "积分幂等参数冲突", HttpStatus.CONFLICT),
    POINTS_LEDGER_NOT_FOUND(47003, "积分流水不存在", HttpStatus.NOT_FOUND),
    POINTS_REVERSAL_CONFLICT(47004, "积分冲正状态冲突", HttpStatus.CONFLICT),
    POINTS_AMOUNT_INVALID(47005, "积分数量不合法", HttpStatus.BAD_REQUEST),
    REDEMPTION_NOT_FOUND(47006, "积分兑换单不存在", HttpStatus.NOT_FOUND),
    REDEMPTION_SKU_UNAVAILABLE(47007, "SKU不可用于积分兑换", HttpStatus.BAD_REQUEST),
    REDEMPTION_STATE_CONFLICT(47008, "积分兑换单状态冲突", HttpStatus.CONFLICT),
    POINTS_USER_UNAVAILABLE(47009, "积分账户用户不存在或已禁用", HttpStatus.BAD_REQUEST);
}
```

- [ ] **Step 4: Add entities, mappers, DTOs, VOs, permissions, and mapper scan**

Use `@TableName`, `@TableId(type = IdType.AUTO)`, enum fields, `LocalDateTime`, and project field-fill conventions. Add mapper scanning for `com.dongqh.luckyhub.points.mapper`.

Add exact permissions:

```java
public static final String POINTS_READ = "points:read";
public static final String POINTS_REDEEM = "points:redeem";
public static final String POINTS_ADJUST = "points:adjust";
```

Define views:

```java
public record PointsAccountView(Long userId, Long balance, LocalDateTime updatedAt) {}

public record PointsLedgerView(
        Long id, Long userId, PointsBusinessType businessType, String businessId,
        PointsDirection direction, Long amount, Long balanceAfter,
        Long reversalOfLedgerId, String remark, LocalDateTime createdAt
) {}

public record PointsRedemptionView(
        Long id, String redemptionNo, Long userId, Long skuId, Integer quantity,
        Long unitPoints, Long totalPoints, String productCode, String productName,
        String skuCode, String skuName, ProductType productType, String imageUrl,
        PointsRedemptionStatus status, String reversalNo, String failureReason,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {}
```

- [ ] **Step 5: Run domain contracts**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=PointsDomainContractTests,PointsSchemaContractTests' test
```

Expected: PASS.

- [ ] **Step 6: Write the task introduction and commit**

Explain each enum, error range `47001-47009`, why amounts are positive plus a direction, and why `balanceAfter` is a ledger snapshot.

```powershell
git add -- src/main/java/com/dongqh/luckyhub/points `
  src/main/java/com/dongqh/luckyhub/config/MybatisPlusConfig.java `
  src/main/java/com/dongqh/luckyhub/rbac/constant/PermissionCodes.java `
  src/test/java/com/dongqh/luckyhub/points/PointsDomainContractTests.java `
  docs/progress/阶段2-任务2-积分领域契约完成介绍.md `
  docs/superpowers/plans/2026-08-08-points-account-redemption.md
git commit -m "feat: define points domain"
```

---

## Task 3: Implement idempotent points account operations

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/points/service/PointsAccountService.java`
- Create: `src/main/java/com/dongqh/luckyhub/points/service/impl/PointsAccountServiceImpl.java`
- Modify: `src/main/java/com/dongqh/luckyhub/points/mapper/PointsAccountMapper.java`
- Modify: `src/main/java/com/dongqh/luckyhub/points/mapper/PointsLedgerMapper.java`
- Create: `src/test/java/com/dongqh/luckyhub/points/PointsAccountServiceTests.java`
- Create: `docs/progress/阶段2-任务3-幂等积分账户服务完成介绍.md`

**Interfaces:**
- Consumes: V10 account/ledger tables and Task 2 commands.
- Produces: transactional credit, debit, reversal, and adjustment operations for redemption and future reward flows.

- [ ] **Step 1: Define the service interface and mapper conditionals**

```java
public interface PointsAccountService {
    PointsLedgerView credit(PointsMutationCommand command);
    PointsLedgerView debit(PointsMutationCommand command);
    PointsLedgerView reverseDebit(PointsReversalCommand command);
    PointsLedgerView adjust(AdminPointsAdjustmentCommand command);
    PointsAccountView get(long userId);
}
```

Required atomic mapper SQL:

```sql
INSERT IGNORE INTO points_account(user_id, balance, version, created_at, updated_at)
VALUES (#{userId}, 0, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

UPDATE points_account
SET balance = balance - #{amount}, version = version + 1
WHERE user_id = #{userId} AND balance >= #{amount};

UPDATE points_account
SET balance = balance + #{amount}, version = version + 1
WHERE user_id = #{userId} AND balance <= 9223372036854775807 - #{amount};

INSERT IGNORE INTO points_ledger(...);
```

- [ ] **Step 2: Write failing account and idempotency tests**

Cover:

```text
missing account query -> balance 0
manual +1000 -> balance 1000 and one MANUAL_ADJUSTMENT credit ledger
manual -300 -> balance 700 and one MANUAL_ADJUSTMENT debit ledger
debit 500 -> balance 200
debit 201 -> 47001 and no ledger
same business type/id/same identity -> same result, no second mutation
same business type/id/different user, direction, or amount -> 47002
reverse original REDEMPTION debit -> new REVERSAL credit and original unchanged
same reversal id -> same result
different reversal id for already reversed debit -> 47004
reverse missing/non-debit ledger -> 47003 or 47004
mutation for a missing or disabled user -> 47009 and no account/ledger
zero, Long.MIN_VALUE, and addition overflow -> 47005
```

- [ ] **Step 3: Run tests and verify RED**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=PointsAccountServiceTests' test
```

Expected: FAIL because service behavior is not implemented.

- [ ] **Step 4: Implement short READ_COMMITTED transactions**

Implementation order for every mutation:

```text
validate command and normalize businessId/remark
-> require an existing enabled sys_user
-> ensure account row with INSERT IGNORE
-> find existing ledger by business_type + business_id
-> if existing, validate the complete identity and return it
-> atomically update account balance
-> read the new balance in the same transaction
-> INSERT IGNORE ledger
-> if ledger claim loses, validate winner and let transaction roll back its balance change
```

Prefer claiming the ledger before the balance update when possible. If claim-first is used, a failed balance update must throw so the claimed row rolls back. Use `Isolation.READ_COMMITTED` so losing duplicate requests can read the committed winner without old-snapshot problems.

`adjust` maps positive delta to CREDIT and negative delta to DEBIT with `Math.negateExact(delta)` for magnitude. Only `REDEMPTION` debits are accepted by `reverseDebit` in Phase 2.

Inject `SysUserMapper` and reject missing/disabled target users with `POINTS_USER_UNAVAILABLE`; never create an account row for an arbitrary numeric ID. `credit` accepts only `LOTTERY_REWARD`, `ORDER_REWARD`, and `MEMBERSHIP_BONUS`; `debit` accepts only `REDEMPTION`; `MANUAL_ADJUSTMENT` is reachable only through `adjust`, and `REVERSAL` only through `reverseDebit`.

- [ ] **Step 5: Run service and legacy inventory tests**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=PointsAccountServiceTests,ChannelInventoryServiceTests' test
```

Expected: PASS.

- [ ] **Step 6: Write the task introduction and commit**

Include examples for credit, debit, insufficient balance, duplicate request, and reversal. Explain why the original debit row never changes.

```powershell
git add -- src/main/java/com/dongqh/luckyhub/points/mapper `
  src/main/java/com/dongqh/luckyhub/points/service `
  src/test/java/com/dongqh/luckyhub/points/PointsAccountServiceTests.java `
  docs/progress/阶段2-任务3-幂等积分账户服务完成介绍.md `
  docs/superpowers/plans/2026-08-08-points-account-redemption.md
git commit -m "feat: manage idempotent points balances"
```

---

## Task 4: Prove points concurrency safety

**Files:**
- Create: `src/test/java/com/dongqh/luckyhub/points/PointsAccountConcurrencyTests.java`
- Modify implementation only if the failing concurrency test identifies a real defect.
- Create: `docs/progress/阶段2-任务4-积分并发安全完成介绍.md`

**Interfaces:**
- Consumes: `PointsAccountService` from Task 3.
- Produces: executable proof that balances and idempotency remain correct under contention.

- [ ] **Step 1: Write two concurrency tests**

Test A:

```text
initial balance = 17
40 simultaneous unique debits of 1 point
exactly 17 succeed
exactly 23 fail with POINTS_INSUFFICIENT
final balance = 0
17 debit ledger rows
no negative balance or balanceAfter
```

Test B:

```text
initial balance = 100
20 simultaneous requests with the same REDEMPTION business id and amount 10
all callers receive the same ledger identity
final balance = 90
exactly one debit ledger row
```

Use `CountDownLatch` to release threads together and collect results with a bounded executor.

- [ ] **Step 2: Run and verify the tests fail if concurrency handling is incomplete**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=PointsAccountConcurrencyTests' test
```

Expected: PASS if Task 3 is correct; if FAIL, preserve the test and debug the root cause before changing code.

- [ ] **Step 3: Run the concurrency tests repeatedly**

```powershell
1..5 | ForEach-Object {
  pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
    '-Dtest=PointsAccountConcurrencyTests' test
  if ($LASTEXITCODE -ne 0) { throw "Concurrency run $_ failed" }
}
```

Expected: all five runs PASS.

- [ ] **Step 4: Write the task introduction and commit**

Explain conditional debit using a “40 people spend 17 points” example and duplicate-request convergence.

```powershell
git add -- src/test/java/com/dongqh/luckyhub/points/PointsAccountConcurrencyTests.java `
  src/main/java/com/dongqh/luckyhub/points `
  docs/progress/阶段2-任务4-积分并发安全完成介绍.md `
  docs/superpowers/plans/2026-08-08-points-account-redemption.md
git commit -m "test: prove points concurrency safety"
```

---

## Task 5: Add confirmed inventory reversal

**Files:**
- Modify: `src/main/java/com/dongqh/luckyhub/inventory/channel/enums/InventoryOperation.java`
- Modify: `src/main/java/com/dongqh/luckyhub/inventory/channel/enums/InventoryReservationStatus.java`
- Modify: `src/main/java/com/dongqh/luckyhub/inventory/channel/mapper/ChannelInventoryMapper.java`
- Modify: `src/main/java/com/dongqh/luckyhub/inventory/channel/service/ChannelInventoryService.java`
- Modify: `src/main/java/com/dongqh/luckyhub/inventory/channel/service/impl/ChannelInventoryServiceImpl.java`
- Modify: `src/test/java/com/dongqh/luckyhub/inventory/ChannelInventoryServiceTests.java`
- Modify: `src/test/java/com/dongqh/luckyhub/inventory/ChannelInventoryConcurrencyTests.java`
- Create: `docs/progress/阶段2-任务5-已确认库存反向恢复完成介绍.md`

**Interfaces:**
- Consumes: V10 CHECK constraints.
- Produces: `ChannelInventoryService.reverseConfirmed(String reservationNo)` for atomic redemption compensation.

- [ ] **Step 1: Write failing reversal tests**

Required behavior:

```text
CONFIRMED -> REVERSED
consumedStock decreases by quantity
availableStock increases by quantity
reservedStock remains zero
ledger business number = RETURN:{reservationNo}
same reversal repeats idempotently
RESERVED -> REVERSED is rejected with 46003
RELEASED -> REVERSED is rejected with 46003
REVERSED -> confirm/release is rejected with 46003
20 concurrent reverse calls create one RETURN ledger and restore once
```

- [ ] **Step 2: Run tests and verify RED**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=ChannelInventoryServiceTests,ChannelInventoryConcurrencyTests' test
```

Expected: FAIL because `REVERSED`, `RETURN`, and `reverseConfirmed` do not exist.

- [ ] **Step 3: Add the atomic mapper update**

```java
@Update("""
        UPDATE inventory_channel_stock
        SET consumed_stock = consumed_stock - #{quantity},
            available_stock = available_stock + #{quantity},
            version = version + 1
        WHERE sku_id = #{skuId} AND channel_code = #{channelCode}
          AND consumed_stock >= #{quantity}
        """)
int reverseConsumed(long skuId, String channelCode, int quantity);
```

- [ ] **Step 4: Extend the state transition without weakening old rules**

Add `RETURN` and `REVERSED`. Implement:

```java
ChannelInventoryView reverseConfirmed(String reservationNo);
```

The method must require `CONFIRMED`, claim `RETURN:{reservationNo}`, conditionally transition the reservation, call `reverseConsumed`, and return the new view in one `READ_COMMITTED` transaction. Existing `confirm` and `release` continue accepting only `RESERVED`.

- [ ] **Step 5: Run new and old inventory/lottery regression**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=ChannelInventoryServiceTests,ChannelInventoryConcurrencyTests,ChannelInventoryControllerTests,ActivityPrizeInventoryTests,LotteryConcurrencyTests' test
```

Expected: PASS. Existing HTTP inventory API does not expose reversal; only the points orchestration consumes it in this phase.

- [ ] **Step 6: Write the task introduction and commit**

Use an example where a completed 3000-point redemption later fails and one consumed item returns to `POINTS` availability. Contrast `RELEASED` with `REVERSED`.

```powershell
git add -- src/main/java/com/dongqh/luckyhub/inventory/channel `
  src/test/java/com/dongqh/luckyhub/inventory/ChannelInventoryServiceTests.java `
  src/test/java/com/dongqh/luckyhub/inventory/ChannelInventoryConcurrencyTests.java `
  docs/progress/阶段2-任务5-已确认库存反向恢复完成介绍.md `
  docs/superpowers/plans/2026-08-08-points-account-redemption.md
git commit -m "feat: reverse confirmed channel inventory"
```

---

## Task 6: Implement atomic points redemption orchestration

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/catalog/model/RedeemableSkuSnapshot.java`
- Modify: `src/main/java/com/dongqh/luckyhub/catalog/service/CatalogService.java`
- Modify: `src/main/java/com/dongqh/luckyhub/catalog/service/impl/CatalogServiceImpl.java`
- Create: `src/main/java/com/dongqh/luckyhub/points/service/PointsRedemptionService.java`
- Create: `src/main/java/com/dongqh/luckyhub/points/service/impl/PointsRedemptionServiceImpl.java`
- Modify: `src/main/java/com/dongqh/luckyhub/points/mapper/PointsRedemptionOrderMapper.java`
- Create: `src/test/java/com/dongqh/luckyhub/points/PointsRedemptionServiceTests.java`
- Create: `src/test/java/com/dongqh/luckyhub/points/PointsRedemptionConcurrencyTests.java`
- Create: `docs/progress/阶段2-任务6-积分兑换事务完成介绍.md`

**Interfaces:**
- Consumes: `CatalogService.findRedeemableSku`, `PointsAccountService`, and `ChannelInventoryService`.
- Produces: user-owned idempotent redemption creation/query and admin compensation.

- [ ] **Step 1: Define the catalog snapshot and service contract**

```java
public record RedeemableSkuSnapshot(
        Long skuId,
        String productCode,
        String productName,
        String skuCode,
        String skuName,
        ProductType productType,
        String imageUrl,
        Long pointsPrice
) {}

Optional<RedeemableSkuSnapshot> findRedeemableSku(long skuId);
```

The catalog implementation returns empty unless the product and SKU are enabled, `pointsEnabled == true`, and `pointsPrice > 0`. It exposes no inventory and does not depend on the `points` package. `PointsRedemptionServiceImpl` converts empty to `REDEMPTION_SKU_UNAVAILABLE`.

- [ ] **Step 2: Define redemption commands and service interface**

```java
public record CreatePointsRedemptionCommand(
        @NotBlank @Size(max = 64) String redemptionNo,
        @NotNull @Positive Long skuId,
        @NotNull @Min(1) @Max(100) Integer quantity
) {}

public record ReversePointsRedemptionCommand(
        @NotBlank @Size(max = 64) String reversalNo,
        @NotBlank @Size(max = 500) String reason
) {}

public interface PointsRedemptionService {
    PointsRedemptionView create(long userId, CreatePointsRedemptionCommand command);
    PointsRedemptionView get(long userId, String redemptionNo);
    PageResponse<PointsRedemptionView> page(long userId, PointsRedemptionQuery query);
    PointsRedemptionView reverse(String redemptionNo, ReversePointsRedemptionCommand command);
}
```

- [ ] **Step 3: Write failing redemption service tests**

Cover:

```text
snapshot uses price/name/image at creation time
quantity multiplication uses Math.multiplyExact
uses channel POINTS and reservationNo equal to redemptionNo
successful creation: account debit + inventory confirm + COMPLETED order
same redemptionNo/user/SKU/quantity returns same result with no second debit or stock mutation
same redemptionNo with another user/SKU/quantity -> 47002
disabled/non-points/zero-price SKU -> 47007
insufficient points -> no order, no inventory mutation, no debit ledger
insufficient inventory -> no order, no points mutation
failure after debit -> entire create transaction rolls back
user cannot query another user's redemption -> 20001
reverse COMPLETED: REVERSAL credit + inventory REVERSED + order REVERSED
same reversalNo repeats idempotently
different reversalNo or non-COMPLETED order -> 47008
```

- [ ] **Step 4: Run tests and verify RED**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=PointsRedemptionServiceTests' test
```

Expected: FAIL because orchestration does not exist.

- [ ] **Step 5: Implement claim-first creation in one transaction**

Use `@Transactional(isolation = Isolation.READ_COMMITTED)` and this exact order:

```text
normalize redemptionNo
-> if existing: validate userId + skuId + quantity and return snapshot
-> load RedeemableSkuSnapshot
-> totalPoints = Math.multiplyExact(pointsPrice, quantity)
-> INSERT IGNORE PROCESSING redemption snapshot
-> if claim lost: reload, validate identity, return winner
-> inventory.reserve(skuId, "POINTS", quantity, redemptionNo)
-> points.debit(userId, REDEMPTION, redemptionNo, totalPoints)
-> inventory.confirm(redemptionNo)
-> conditional PROCESSING -> COMPLETED
-> return persisted snapshot
```

Any exception must roll back the order, points ledger/account, inventory reservation/ledger, and channel quantities together.

- [ ] **Step 6: Implement atomic compensation**

In one transaction:

```text
lock redemption by redemptionNo
-> if REVERSED: reversalNo must match, then return
-> require COMPLETED
-> points.reverseDebit(userId, REDEMPTION, redemptionNo, reversalNo)
-> inventory.reverseConfirmed(redemptionNo)
-> conditional COMPLETED -> REVERSED with reversalNo and safe reason
```

Never delete the redemption debit, reservation, or original order.

- [ ] **Step 7: Add concurrent duplicate redemption tests**

Twenty simultaneous calls with one redemption number must result in:

```text
one redemption order
one REDEMPTION debit ledger
one inventory reservation
one RESERVE ledger
one CONFIRM ledger
one unit consumed
all callers observe the same order id and COMPLETED status
```

- [ ] **Step 8: Run redemption, account, and inventory tests**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=PointsRedemptionServiceTests,PointsRedemptionConcurrencyTests,PointsAccountServiceTests,PointsAccountConcurrencyTests,ChannelInventoryServiceTests,ChannelInventoryConcurrencyTests' test
```

Expected: PASS.

- [ ] **Step 9: Write the task introduction and commit**

Explain a 3000-point single-SKU exchange, atomic rollback on insufficient stock, duplicate request behavior, snapshots, and later reversal.

```powershell
git add -- src/main/java/com/dongqh/luckyhub/catalog `
  src/main/java/com/dongqh/luckyhub/points `
  src/test/java/com/dongqh/luckyhub/points/PointsRedemptionServiceTests.java `
  src/test/java/com/dongqh/luckyhub/points/PointsRedemptionConcurrencyTests.java `
  docs/progress/阶段2-任务6-积分兑换事务完成介绍.md `
  docs/superpowers/plans/2026-08-08-points-account-redemption.md
git commit -m "feat: redeem products with points"
```

---

## Task 7: Expose points and redemption APIs

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/points/service/PointsQueryService.java`
- Create: `src/main/java/com/dongqh/luckyhub/points/service/impl/PointsQueryServiceImpl.java`
- Create: `src/main/java/com/dongqh/luckyhub/points/controller/PointsController.java`
- Create: `src/main/java/com/dongqh/luckyhub/points/controller/PointsRedemptionController.java`
- Create: `src/main/java/com/dongqh/luckyhub/points/controller/PointsAdminController.java`
- Create: `src/test/java/com/dongqh/luckyhub/points/PointsControllerTests.java`
- Create: `src/test/java/com/dongqh/luckyhub/points/PointsRedemptionControllerTests.java`
- Create: `src/test/java/com/dongqh/luckyhub/points/PointsSecurityChainIntegrationTests.java`
- Create: `docs/progress/阶段2-任务7-积分与兑换API完成介绍.md`

**Interfaces:**
- Consumes: login context, permissions, account service, query service, and redemption service.
- Produces: five user endpoints and two admin endpoints.

- [ ] **Step 1: Define exact endpoints**

```text
GET  /api/points/account
GET  /api/points/ledgers
POST /api/points/redemptions
GET  /api/points/redemptions
GET  /api/points/redemptions/{redemptionNo}

POST /api/admin/points/adjustments
POST /api/admin/points/redemptions/{redemptionNo}/reverse
```

Permissions:

```text
account/ledgers/redemption reads -> points:read
create redemption               -> points:redeem
adjust/reverse                   -> points:adjust
```

Define the read service exactly:

```java
public interface PointsQueryService {
    PointsAccountView getAccount(long userId);
    PageResponse<PointsLedgerView> pageLedgers(long userId, PointsLedgerQuery query);
}
```

`getAccount` delegates to the account service and returns zero for a valid user with no account row. `pageLedgers` always adds `user_id = current user` to the MyBatis query, orders by `created_at DESC, id DESC`, and never accepts a caller-supplied user id.

- [ ] **Step 2: Write failing controller and real-security tests**

Assert:

```text
user id always comes from LoginContext, never request JSON/query
create redemption -> 201
all GET/adjust/reverse success -> 200
ledger and redemption paging use PageResponse
query validation -> 30000
missing token -> 401/20004
missing permission -> 403/20001
stable 47001-47009 errors preserve HTTP and code
normal user cannot read another user's redemption because no userId input exists
normal user cannot call adjustments or reversals
```

- [ ] **Step 3: Implement thin controllers**

Controllers only obtain `LoginContext.require().userId()`, validate transport input, delegate, and wrap `ApiResponse`. No balance arithmetic, price calculation, stock transition, or data-scope replacement belongs in controllers.

Use these query bounds:

```java
@Min(1) long page = 1;
@Min(1) @Max(100) long size = 20;
@Size(max = 100) String businessId;
PointsBusinessType businessType;
PointsDirection direction;
PointsRedemptionStatus status;
```

- [ ] **Step 4: Run API and security tests**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=PointsControllerTests,PointsRedemptionControllerTests,PointsSecurityChainIntegrationTests,LotterySecurityChainIntegrationTests' test
```

Expected: PASS.

- [ ] **Step 5: Write the task introduction and commit**

Include PowerShell examples for balance query, admin +500 adjustment, 3000-point redemption, ledger query, and admin reversal. Explain 401/403 and self-scope.

```powershell
git add -- src/main/java/com/dongqh/luckyhub/points `
  src/test/java/com/dongqh/luckyhub/points `
  docs/progress/阶段2-任务7-积分与兑换API完成介绍.md `
  docs/superpowers/plans/2026-08-08-points-account-redemption.md
git commit -m "feat: expose points redemption API"
```

---

## Task 8: Document, verify, review, and hand off Phase 2

**Files:**
- Create: `docs/points-redemption-api.md`
- Modify: `README.md`
- Modify: `docs/LuckyHub-迷你商城下一阶段执行总路线.md`
- Modify: `docs/LuckyHub-开发进度交接总结.md`
- Create: `docs/progress/阶段2-任务8-阶段交付完成介绍.md`

**Interfaces:**
- Consumes: every Phase 2 API, migration, transaction, and test result.
- Produces: reproducible Phase 2 handoff and the decision boundary for Phase 3 planning.

- [ ] **Step 1: Write complete API and state documentation**

For every endpoint document permission, request, response, errors, and PowerShell example. Explain:

```text
account balance vs immutable ledger
business_type + business_id idempotency
REDEMPTION debit and REVERSAL credit linkage
PROCESSING -> COMPLETED -> REVERSED
POINTS channel only
single SKU and quantity 1-100
product/SKU/price/image snapshots
no coupons, membership, cash, payment, address, logistics, or points reward
```

- [ ] **Step 2: Update README, route, and handoff**

Add V10, permissions, packages, seven endpoints, exact focused/full test results, migration evidence, known boundaries, and Phase 3 as planning-only. Do not write Phase 3 code.

- [ ] **Step 3: Verify V10 on a temporary empty schema**

Create `luckyhub_phase2_verify`, grant only the existing app user temporary access, set `MYSQL_DATABASE` for the Maven process, run `DatabaseSchemaMigrationTests,PointsSchemaContractTests,ChannelInventorySchemaContractTests`, then revoke access and drop only the temporary schema in `finally`.

Expected Flyway path: `Empty Schema -> V1 -> ... -> V10`, all migration contracts PASS.

- [ ] **Step 4: Run focused Phase 2 verification**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=PointsSchemaContractTests,PointsDomainContractTests,PointsAccountServiceTests,PointsAccountConcurrencyTests,PointsRedemptionServiceTests,PointsRedemptionConcurrencyTests,PointsControllerTests,PointsRedemptionControllerTests,PointsSecurityChainIntegrationTests,ChannelInventoryServiceTests,ChannelInventoryConcurrencyTests' test
```

Expected: zero failures and zero errors.

- [ ] **Step 5: Run full regression and package**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 test
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 package '-DskipTests'
git diff --check
git status --short
```

Expected: full suite and package `BUILD SUCCESS`; tracked changes contain only intended Phase 2 handoff files.

- [ ] **Step 6: Review explicit asset-safety questions**

Answer with code/test evidence:

```text
Can any points balance or balanceAfter become negative?
Can a duplicate business id mutate a different user, direction, or amount?
Can the same debit be reversed twice under different reversal numbers?
Can redemption consume points without confirming stock, or stock without points?
Can concurrent duplicate redemptions create multiple orders/ledgers/reservations?
Can a user query another user's account, ledger, or redemption?
Can a points redemption use MALL or LOTTERY inventory?
Do product price changes rewrite redemption snapshots?
Do raw SQL, duplicate-key, or arithmetic overflow messages escape APIs?
Are V1-V9 unchanged and are lottery/outbox/benefit behaviors unchanged?
```

Fix every Critical/Important finding, then rerun Steps 3-5.

- [ ] **Step 7: Mark Phase 2 complete and commit handoff**

Only after all evidence is fresh, switch the master route to Phase 3 planning and record the final Phase 2 feature commit plus counts.

```powershell
git add -- README.md docs/points-redemption-api.md `
  docs/LuckyHub-迷你商城下一阶段执行总路线.md `
  docs/LuckyHub-开发进度交接总结.md `
  docs/progress/阶段2-任务8-阶段交付完成介绍.md `
  docs/superpowers/plans/2026-08-08-points-account-redemption.md
git commit -m "docs: hand off points redemption phase"
git status --short --branch
```

Expected: tracked workspace clean; only known `.codex-progress/` and `.superpowers/` helper directories remain.

---

## Phase 2 Completion Boundary

Phase 2 is complete only when all eight tasks are checked, V10 migrates from an empty schema, focused and full tests pass, package succeeds, review has no unresolved Critical/Important finding, every task has a Chinese completion introduction, and the master route records the evidence.

Do not implement coupons, membership, cash orders, payment, reward-to-points fulfillment, addresses, logistics, external Gateways, Outbox events, DLQ, or Phase 3 code in this plan.
