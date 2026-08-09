# LuckyHub Phase 6 Physical Claim and Shipping Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver one privacy-safe address, claim, shipping, waybill and tracking workflow for lottery products, paid physical orders and points-redemption physical orders.

**Architecture:** A dedicated `shipping` module owns encrypted address-book data, immutable address snapshots and one source-keyed shipping order. The three source domains create snapshots at their approved boundary and converge on a stable `LOGISTICS-{shippingOrderId}` fulfillment task; the existing worker invokes a replaceable Gateway outside database transactions, while signed idempotent callbacks advance immutable tracking and source projections.

**Tech Stack:** Java 17, Spring Boot 4.1, MyBatis-Plus, MySQL 8/Flyway V17, Java Cryptography Architecture AES-256-GCM/HMAC-SHA256, Redis, Jackson, JUnit 5, AssertJ, Spring Boot integration tests, PowerShell 7.

## Global Constraints

- Treat `docs/superpowers/specs/2026-08-09-physical-claim-and-shipping-design.md` as the approved source of truth.
- Never modify V1-V16; add V17 only.
- Cover `LOTTERY_BENEFIT`, `CASH_ORDER` and `POINTS_REDEMPTION` through one shipping aggregate.
- Cash and points physical orders select and snapshot an address at order creation; lottery products select it during claim.
- Never create a `LOGISTICS` task when a product is won; create it only after claim or a qualifying physical transaction succeeds.
- Store no plaintext receiver, phone, address, encryption key, callback secret or raw provider response in logs, events, fulfillment payloads/attempts, simulator tables or safe errors.
- Keep remote Gateway calls outside database transactions and preserve Phase 4 retry/reconciliation/quarantine semantics.
- Preserve Phase 5 reward snapshots, cross-identity validation and every old response field; new fields are nullable for legacy data.
- Use PowerShell 7 and `scripts/Invoke-Maven.ps1`; read and write Chinese as UTF-8.
- Every task creates one `docs/progress/阶段6-任务N-*.md` with rationale, implementation, a concrete example, tests, boundaries and next step.
- Preserve user-owned untracked `.codex-progress/` and `.superpowers/` directories.

---

### Task 1: V17 shipping persistence and domain skeleton

**Files:**
- Create: `src/main/resources/db/migration/V17__add_shipping_and_physical_claim.sql`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/enums/ShippingSourceType.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/enums/ShippingStatus.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/enums/TrackingEventType.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/enums/AddressStatus.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/enums/ShippingErrorCode.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/entity/UserShippingAddress.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/entity/ShippingAddressSnapshot.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/entity/ShippingOrder.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/entity/ShippingTrackingEvent.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/entity/ShippingCallbackReceipt.java`
- Create: corresponding five mapper interfaces under `src/main/java/com/dongqh/luckyhub/shipping/mapper/`
- Modify: `src/main/java/com/dongqh/luckyhub/benefit/entity/UserBenefit.java`
- Modify: `src/main/java/com/dongqh/luckyhub/benefit/enums/BenefitStatus.java`
- Modify: `src/main/java/com/dongqh/luckyhub/order/entity/MallOrder.java`
- Modify: `src/main/java/com/dongqh/luckyhub/points/entity/PointsRedemptionOrder.java`
- Test: `src/test/java/com/dongqh/luckyhub/shipping/ShippingSchemaContractTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/shipping/ShippingDomainContractTests.java`
- Document: `docs/progress/阶段6-任务1-V17与物流领域骨架完成介绍.md`

**Interfaces:**
- Produces `ShippingSourceType { LOTTERY_BENEFIT, CASH_ORDER, POINTS_REDEMPTION }`.
- Produces `ShippingStatus { READY, FULFILLING, SHIPPED, IN_TRANSIT, DELIVERED, FAILED, TERMINATED }`.
- Adds nullable `addressSnapshotId` and `shippingOrderId` to cash and points orders.
- Adds nullable `claimDeadline`, `claimedAt`, `shippingOrderId` to benefits and extends `BenefitStatus` with the approved physical states.

- [ ] **Step 1: Write failing schema and reflection contracts**

Assert V17, the five new tables, encrypted columns as `TEXT`, masked columns as bounded `VARCHAR`, exact checks, foreign identity columns, unique source/idempotency keys, permission rows and nullable legacy columns.

```java
assertThat(uniqueIndexes()).contains(
    "shipping_address_snapshot:snapshot_no",
    "shipping_address_snapshot:source_type,source_id",
    "shipping_order:source_type,source_id",
    "shipping_order:shipping_no",
    "shipping_order:fulfillment_no",
    "shipping_order:claim_request_id",
    "shipping_tracking_event:provider_event_id",
    "shipping_callback_receipt:callback_id",
    "shipping_callback_receipt:nonce_digest");
assertThat(ShippingStatus.values()).containsExactly(
    READY, FULFILLING, SHIPPED, IN_TRANSIT, DELIVERED, FAILED, TERMINATED);
```

- [ ] **Step 2: Run RED**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 '-Dtest=ShippingSchemaContractTests,ShippingDomainContractTests' test
```

Expected: compilation/schema failures because V17 and `shipping` types do not exist.

- [ ] **Step 3: Add V17 with exact state and privacy constraints**

The migration creates the five approved tables, uses `UNIQUE KEY uk_shipping_order_source (source_type,source_id)` and nullable `UNIQUE KEY uk_shipping_order_claim_request (claim_request_id)`, extends `chk_inventory_ledger_operation` with `CLAIM_RETURN`, gives existing `CLAIM_PENDING` rows `DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 7 DAY)`, adds `shipping:address:manage`, `shipping:read`, `shipping:operate`, grants user permissions to USER/ADMIN and management permissions to ADMIN, and extends existing benefit fields without rewriting old rewards.

```sql
ALTER TABLE user_benefit
  ADD COLUMN claim_deadline DATETIME(3) NULL AFTER expire_at,
  ADD COLUMN claimed_at DATETIME(3) NULL AFTER claim_deadline,
  ADD COLUMN shipping_order_id BIGINT UNSIGNED NULL AFTER claimed_at,
  ADD KEY idx_user_benefit_claim_expiry (status, claim_deadline, id);

UPDATE user_benefit
SET claim_deadline = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 7 DAY)
WHERE status = 'CLAIM_PENDING' AND claim_deadline IS NULL;
```

- [ ] **Step 4: Add minimal enums, entities and mappers**

Keep each entity a plain MyBatis-Plus mapping. Mapper lock methods use explicit `FOR UPDATE`, for example:

```java
@Select("SELECT * FROM shipping_order WHERE source_type=#{type} AND source_id=#{sourceId} FOR UPDATE")
ShippingOrder lockBySource(ShippingSourceType type, String sourceId);
```

- [ ] **Step 5: Run GREEN and migration regressions**

Run the focused tests plus `'-Dtest=DatabaseSchemaMigrationTests,LotteryMigrationGuardTests,FulfillmentSchemaContractTests' test`. Expected: all selected tests pass and Flyway reports V17.

- [ ] **Step 6: Document and commit**

Explain why `source_type + source_id` prevents three domains from producing duplicates. Commit `feat: add physical shipping persistence`.

### Task 2: AES-GCM address book and user API

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/shipping/config/ShippingProperties.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/config/ShippingConfiguration.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/crypto/AddressCipher.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/crypto/AesGcmAddressCipher.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/support/AddressMasker.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/dto/CreateShippingAddressCommand.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/dto/UpdateShippingAddressCommand.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/vo/ShippingAddressView.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/service/ShippingAddressService.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/service/impl/ShippingAddressServiceImpl.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/controller/ShippingAddressController.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/test/resources/application.properties`
- Modify: `src/main/java/com/dongqh/luckyhub/config/AuthenticationFilterConfig.java`
- Modify: `src/main/java/com/dongqh/luckyhub/config/PermissionInterceptorConfig.java`
- Modify: `src/main/java/com/dongqh/luckyhub/rbac/constant/PermissionCodes.java`
- Test: `src/test/java/com/dongqh/luckyhub/shipping/AddressCipherTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/shipping/ShippingAddressServiceTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/shipping/ShippingAddressControllerTests.java`
- Document: `docs/progress/阶段6-任务2-地址加密与地址簿完成介绍.md`

**Interfaces:**

```java
public interface AddressCipher {
    String encrypt(String plaintext);
    String decrypt(String envelope);
}
public interface ShippingAddressService {
    ShippingAddressView create(long userId, CreateShippingAddressCommand command);
    List<ShippingAddressView> list(long userId);
    ShippingAddressView update(long userId, long addressId, UpdateShippingAddressCommand command);
    void delete(long userId, long addressId);
    ShippingAddressView makeDefault(long userId, long addressId);
    UserShippingAddress requireOwnedActive(long userId, long addressId);
}
```

- [ ] **Step 1: Write failing crypto, masking, ownership and controller tests**

Prove random ciphertext for equal plaintext, correct decryption, tamper rejection, key-version envelope, `张*` and `138****5678`, one default address under concurrency, soft deletion, no cross-user reads/writes, and authentication/permission enforcement on `/api/shipping/**`.

- [ ] **Step 2: Run RED**

Run `'-Dtest=AddressCipherTests,ShippingAddressServiceTests,ShippingAddressControllerTests' test`. Expected: missing types/endpoints.

- [ ] **Step 3: Implement the exact crypto envelope and configuration**

`ShippingProperties` binds `luckyhub.shipping.address-key`, `address-key-version`, `claim-period`, callback secret/window and scheduler sizes. Decode a 32-byte Base64 key, use a random 12-byte nonce and 128-bit GCM tag, and return `v1.<base64-nonce>.<base64-ciphertext-and-tag>`. Never include plaintext in exception messages.

```yaml
luckyhub:
  shipping:
    address-key: ${SHIPPING_ADDRESS_KEY}
    address-key-version: v1
    claim-period: 7d
    callback-secret: ${SHIPPING_CALLBACK_SECRET}
    callback-window: 5m
    expiry-interval: 1m
    expiry-initial-delay: 60s
    batch-size: 50
```

- [ ] **Step 4: Implement transactional address operations and API**

Validate Mainland China 11-digit mobile numbers, nonblank bounded address parts, ownership and active status. Encrypt every sensitive field before insert/update; views contain only masked fields and never decrypt. `makeDefault` locks the user's active rows, clears the old default and sets the selected row in one transaction.

- [ ] **Step 5: Run GREEN plus security regressions**

Run focused tests plus `'-Dtest=LotterySecurityChainIntegrationTests,PointsSecurityChainIntegrationTests,FulfillmentSecurityChainIntegrationTests' test`.

- [ ] **Step 6: Document and commit**

Show that two encryptions of the same phone differ while both display `138****5678`. Commit `feat: protect user shipping addresses`.

### Task 3: Immutable snapshots and physical cash/points order integration

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/shipping/model/AddressSnapshotOwner.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/vo/ShippingAddressSnapshotView.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/service/ShippingAddressSnapshotService.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/service/impl/ShippingAddressSnapshotServiceImpl.java`
- Modify: `src/main/java/com/dongqh/luckyhub/order/dto/CreateCashOrderCommand.java`
- Modify: `src/main/java/com/dongqh/luckyhub/order/entity/MallOrder.java`
- Modify: `src/main/java/com/dongqh/luckyhub/order/service/impl/CashOrderServiceImpl.java`
- Modify: `src/main/java/com/dongqh/luckyhub/order/vo/CashOrderView.java`
- Modify: `src/main/java/com/dongqh/luckyhub/points/dto/CreatePointsRedemptionCommand.java`
- Modify: `src/main/java/com/dongqh/luckyhub/points/entity/PointsRedemptionOrder.java`
- Modify: `src/main/java/com/dongqh/luckyhub/points/service/impl/PointsRedemptionServiceImpl.java`
- Modify: `src/main/java/com/dongqh/luckyhub/points/vo/PointsRedemptionView.java`
- Test: `src/test/java/com/dongqh/luckyhub/shipping/ShippingAddressSnapshotTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/shipping/PhysicalOrderAddressIntegrationTests.java`
- Modify tests: cash order, points redemption and controller constructor/request tests
- Document: `docs/progress/阶段6-任务3-不可变快照与实物下单完成介绍.md`

**Interfaces:**

```java
public interface ShippingAddressSnapshotService {
    ShippingAddressSnapshot create(long userId, long addressId,
        ShippingSourceType sourceType, String sourceId);
    ShippingAddressSnapshot require(long snapshotId);
}
```

Both create commands append nullable `@Positive Long addressId`. Identity validation for retries includes `addressId` through the stored snapshot origin, so the same order number with a different address is an idempotency conflict.

- [ ] **Step 1: Write failing physical/virtual and immutability tests**

Assert physical orders require an owned active address, virtual orders reject `addressId`, both create a snapshot inside the order transaction, changing/deleting the address does not change the snapshot, and order rollback leaves no orphan snapshot.

- [ ] **Step 2: Run RED**

Run `'-Dtest=ShippingAddressSnapshotTests,PhysicalOrderAddressIntegrationTests,CashOrderServiceTests,PointsRedemptionServiceTests' test`.

- [ ] **Step 3: Implement immutable snapshot creation**

Load the owned address once, copy existing ciphertext and masked fields, insert with source identity, and return the existing equal snapshot on duplicate source. Different address origin or contents for the same source throws `SHIPPING_IDEMPOTENCY_CONFLICT`.

- [ ] **Step 4: Integrate cash and points transactions**

After the business order row obtains its ID, create the snapshot and conditionally update its `address_snapshot_id`. For physical rows require the ID; for virtual rows require null. Keep the old payment and points asset semantics unchanged.

- [ ] **Step 5: Run GREEN and Phase 2/3 regressions**

Run focused tests plus `'-Dtest=Phase3EndToEndTests,PointsRedemptionConcurrencyTests,OrderCancellationTests' test`.

- [ ] **Step 6: Document and commit**

Use the “杭州下单、地址簿改成上海、包裹仍发杭州” example. Commit `feat: snapshot physical order addresses`.

### Task 4: Lottery product claim, deadline and inventory compensation

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/shipping/dto/ClaimPhysicalBenefitCommand.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/service/PhysicalClaimService.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/service/impl/PhysicalClaimServiceImpl.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/service/PhysicalClaimExpiryService.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/service/impl/PhysicalClaimExpiryServiceImpl.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/scheduler/PhysicalClaimExpiryScheduler.java`
- Modify: `src/main/java/com/dongqh/luckyhub/benefit/controller/BenefitController.java`
- Modify: `src/main/java/com/dongqh/luckyhub/benefit/mapper/UserBenefitMapper.java`
- Modify: `src/main/java/com/dongqh/luckyhub/lottery/service/impl/LotteryRewardDispatchServiceImpl.java`
- Modify: `src/main/java/com/dongqh/luckyhub/inventory/service/ActivityPrizeInventoryService.java`
- Modify: `src/main/java/com/dongqh/luckyhub/inventory/service/ActivityPrizeInventoryServiceImpl.java`
- Modify: `src/main/java/com/dongqh/luckyhub/inventory/mapper/ActivityPrizeInventoryMapper.java`
- Modify: `src/main/java/com/dongqh/luckyhub/inventory/channel/enums/InventoryOperation.java`
- Test: `src/test/java/com/dongqh/luckyhub/shipping/PhysicalBenefitClaimTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/shipping/PhysicalClaimConcurrencyTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/shipping/PhysicalClaimExpiryTests.java`
- Document: `docs/progress/阶段6-任务4-抽奖实物领取与超时完成介绍.md`

**Interfaces:**

```java
public interface PhysicalClaimService {
    ShippingOrderView claim(long userId, long benefitId, ClaimPhysicalBenefitCommand command);
}
public interface PhysicalClaimExpiryService {
    int expireDue(int limit, LocalDateTime now);
}
public record ClaimPhysicalBenefitCommand(
    @NotBlank @Pattern(regexp="[0-9a-fA-F-]{36}") String requestId,
    @NotNull @Positive Long addressId) {}
```

- [ ] **Step 1: Write failing ownership, deadline, idempotency and race tests**

Cover eligible claim, wrong user/type/status, deadline equality, duplicate request, different-address retry, 20 concurrent claims, claim-versus-expiry, old migrated grace period and one inventory return.

- [ ] **Step 2: Run RED**

Run `'-Dtest=PhysicalBenefitClaimTests,PhysicalClaimConcurrencyTests,PhysicalClaimExpiryTests' test`.

- [ ] **Step 3: Stamp deadlines when products become claimable**

In the Phase 5 dispatcher, set `claim_deadline = now + properties.claimPeriod()` in the same conditional update that sets `CLAIM_PENDING`. Do not create a shipping or fulfillment row at win time.

- [ ] **Step 4: Implement locked claim and expiry transactions**

Claim locks the benefit, cross-checks PRODUCT snapshot/SKU quantity and user, stores the unique request ID on the shipping order, creates the snapshot and unified shipping order, then updates benefit. Expiry locks due rows, claims existing `inventory_ledger.business_no=CLAIM-EXPIRE-{benefitId}` with the new `CLAIM_RETURN` operation, and atomically increments `activity_prize_inventory.remaining_stock` by one using `draw_record.activity_prize_id`; transition only from `CLAIM_PENDING`. The benefit status and inventory ledger together prevent repeated return, while `shipping_order.claim_request_id` detects request conflicts.

- [ ] **Step 5: Run GREEN plus Phase 5 product regressions**

Run focused tests plus `'-Dtest=LotteryFiveRewardEndToEndTests,LotteryRewardDispatchTests,ActivityPrizeInventoryTests' test`.

- [ ] **Step 6: Document and commit**

Explain a user claiming at the same instant as the expiry scheduler. Commit `feat: claim and expire lottery products`.

### Task 5: Unified shipping order, fulfillment task and protected Gateway assembly

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/shipping/model/CreateShippingOrderCommand.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/vo/ShippingOrderView.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/service/ShippingOrderService.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/service/impl/ShippingOrderServiceImpl.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/integration/LogisticsRequestAssembler.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/integration/LogisticsRequestAssemblerImpl.java`
- Modify: `src/main/java/com/dongqh/luckyhub/payment/service/impl/PaymentServiceImpl.java`
- Modify: `src/main/java/com/dongqh/luckyhub/points/service/impl/PointsRedemptionServiceImpl.java`
- Modify: `src/main/java/com/dongqh/luckyhub/fulfillment/model/LogisticsFulfillmentPayload.java`
- Modify: `src/main/java/com/dongqh/luckyhub/fulfillment/worker/FulfillmentWorker.java`
- Replace: `src/main/java/com/dongqh/luckyhub/integration/gateway/LogisticsCreateRequest.java`
- Modify: `src/main/java/com/dongqh/luckyhub/integration/simulator/SimulatedLogisticsGateway.java`
- Test: `src/test/java/com/dongqh/luckyhub/shipping/ShippingOrderServiceTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/shipping/PhysicalSourceShippingTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/shipping/LogisticsPrivacyBoundaryTests.java`
- Modify tests: fulfillment worker/task and simulator logistics contracts
- Document: `docs/progress/阶段6-任务5-统一发货与物流任务完成介绍.md`

**Interfaces:**

```java
public interface ShippingOrderService {
    ShippingOrderView create(CreateShippingOrderCommand command);
    ShippingOrderView getForUser(long userId, String shippingNo);
    void projectFulfillmentState(String fulfillmentNo);
}
public interface LogisticsRequestAssembler {
    LogisticsCreateRequest assemble(FulfillmentClaim claim,
        LogisticsFulfillmentPayload payload);
}
public record LogisticsFulfillmentPayload(
    long shippingOrderId, String skuCode, int quantity,
    String receiverMasked, String phoneMasked, String regionMasked)
    implements FulfillmentPayload {}
```

- [ ] **Step 1: Write failing three-source idempotency and privacy tests**

Prove one shipping order/task per source, stable `LOGISTICS-{id}`, payment retry and points retry do not duplicate, a completed physical points redemption cannot be reversed after shipping creation, logistics payload is masked, assembler decrypts only in memory, request `toString()` is redacted, and `sim_logistics_record.request_payload` contains no plaintext.

- [ ] **Step 2: Run RED**

Run `'-Dtest=ShippingOrderServiceTests,PhysicalSourceShippingTests,LogisticsPrivacyBoundaryTests,FulfillmentWorkerTests,SimulatorGatewayTests' test`.

- [ ] **Step 3: Implement idempotent order/task creation**

Create `shipping_order` and a `FulfillmentTaskService.create` command in the same local transaction using `sourceType/sourceId` equality checks. Payment success calls it only for PHYSICAL after `markPaid`; points success calls it only for PHYSICAL after completion; lottery claim calls it after the snapshot exists.

- [ ] **Step 4: Replace logistics execution with protected assembly**

Make `LogisticsCreateRequest` a final class with explicit accessors and a fixed redacted `toString()`. `FulfillmentWorker` reads the masked payload, delegates to the assembler, then calls Gateway outside the transaction. The simulator overrides serialization with a safe DTO containing only masked fields; its idempotency fingerprint is computed from that safe DTO.

- [ ] **Step 5: Project fulfillment success/failure**

`SUCCEEDED` writes carrier/waybill and `SHIPPED`; `QUARANTINED` or `TERMINATED` writes bounded safe failure and source `FULFILLMENT_FAILED/TERMINATED` where applicable; retry moves the shipping aggregate back to `FULFILLING` without changing its source identity.

- [ ] **Step 6: Run GREEN and Phase 4 regressions**

Run focused tests plus `'-Dtest=FulfillmentEndToEndTests,FulfillmentRecoveryTests,FulfillmentSafetyTests,GatewayContractTests' test`.

- [ ] **Step 7: Document and commit**

Show that the task table sees `张*` while the adapter receives the full contact only in memory. Commit `feat: dispatch unified physical shipments`.

### Task 6: Simulated waybill events and signed idempotent callbacks

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/shipping/dto/LogisticsCallbackCommand.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/dto/SimulateTrackingEventCommand.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/crypto/LogisticsCallbackSigner.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/service/LogisticsCallbackService.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/service/impl/LogisticsCallbackServiceImpl.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/controller/LogisticsCallbackController.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/controller/ShippingQueryController.java`
- Modify: `src/main/java/com/dongqh/luckyhub/integration/simulator/controller/SimulatorAdminController.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/vo/ShippingTrackingView.java`
- Test: `src/test/java/com/dongqh/luckyhub/shipping/LogisticsCallbackTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/shipping/LogisticsCallbackConcurrencyTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/shipping/ShippingQueryControllerTests.java`
- Document: `docs/progress/阶段6-任务6-运单轨迹与回调完成介绍.md`

**Interfaces:**

```java
public interface LogisticsCallbackService {
    void handle(LogisticsCallbackCommand command);
}
public record LogisticsCallbackCommand(
    String callbackId, String nonce, long timestampEpochSecond,
    String waybillNo, TrackingEventType eventType,
    LocalDateTime eventTime, String locationSummary,
    String description, String signature) {}
```

- [ ] **Step 1: Write failing signature, replay, duplicate and order tests**

Cover valid HMAC, tamper, five-minute expiry, duplicate callback, nonce replay under a different callback ID, 20 concurrent duplicates, unknown waybill, allowed status ranks and `DELIVERED` followed by delayed `IN_TRANSIT` without regression.

- [ ] **Step 2: Run RED**

Run `'-Dtest=LogisticsCallbackTests,LogisticsCallbackConcurrencyTests,ShippingQueryControllerTests' test`.

- [ ] **Step 3: Implement canonical HMAC and transactional callback**

Sign the UTF-8 string `callbackId\nnonce\ntimestamp\nwaybillNo\neventType\neventTime`; compare decoded signatures with `MessageDigest.isEqual`. Claim callback and nonce unique rows before locking the shipping order. Insert a unique provider event and apply monotonic status rank in one transaction.

- [ ] **Step 4: Route simulator events through the same callback path**

The admin simulator endpoint creates a UUID callback/nonce, signs with the configured local secret and invokes `LogisticsCallbackService`; it must not update shipping tables directly.

- [ ] **Step 5: Add masked user queries and run GREEN**

Return only own shipping order, masked snapshot and ordered tracking events. Run focused tests plus existing authentication/interceptor tests.

- [ ] **Step 6: Document and commit**

Use the delayed `IN_TRANSIT` after `DELIVERED` example. Commit `feat: track signed logistics callbacks`.

### Task 7: Admin operations, source projection and safe failure recovery

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/shipping/dto/ShippingOrderQuery.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/dto/ShippingOperationCommand.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/controller/ShippingAdminController.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/service/ShippingAdminService.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/service/impl/ShippingAdminServiceImpl.java`
- Create: `src/main/java/com/dongqh/luckyhub/shipping/scheduler/ShippingProjectionScheduler.java`
- Modify: `src/main/java/com/dongqh/luckyhub/benefit/service/BenefitQueryServiceImpl.java`
- Modify: `src/main/java/com/dongqh/luckyhub/benefit/vo/BenefitView.java`
- Modify: cash and points query views to append nullable shipping fields
- Test: `src/test/java/com/dongqh/luckyhub/shipping/ShippingAdminServiceTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/shipping/ShippingAdminControllerTests.java`
- Test: `src/test/java/com/dongqh/luckyhub/shipping/ShippingFailureProjectionTests.java`
- Document: `docs/progress/阶段6-任务7-物流管理与失败恢复完成介绍.md`

**Interfaces:**
- Admin page filters by status, source type/id, user and waybill.
- `retry(shippingNo, operatorId, note)` delegates to the linked fulfillment retry and reprojects the shipping state.
- `terminate(shippingNo, operatorId, note)` delegates to fulfillment termination and stores only a bounded safe note.

- [ ] **Step 1: Write failing admin, projection and authorization tests**

Prove default masking, exact permission codes, retry from failed/quarantined, forbidden retry/terminate states, safe note truncation, recovery to shipped, and benefit progression `CLAIMED -> FULFILLING -> SHIPPED -> DELIVERED`.

- [ ] **Step 2: Run RED**

Run `'-Dtest=ShippingAdminServiceTests,ShippingAdminControllerTests,ShippingFailureProjectionTests' test`.

- [ ] **Step 3: Implement bounded projector and admin service**

Scan linked tasks in ID order, maximum 100. Lock one shipping row per transaction, map only approved fulfillment states, never overwrite delivered/terminated, and use the existing fulfillment service for state transitions.

- [ ] **Step 4: Append compatible source queries**

Append nullable `shippingNo` and `shippingStatus` to benefit, cash and points views. Old rows and virtual products return null. No response exposes ciphertext or full contact fields.

- [ ] **Step 5: Run GREEN and query/security regressions**

Run focused tests plus `'-Dtest=BenefitControllerTests,Phase3ControllerTests,PointsRedemptionControllerTests,FulfillmentControllerTests' test`.

- [ ] **Step 6: Document and commit**

Explain a quarantined courier task that succeeds after an admin retry. Commit `feat: operate failed physical shipments`.

### Task 8: Three-source end-to-end, concurrency and privacy acceptance

**Files:**
- Create: `src/test/java/com/dongqh/luckyhub/shipping/PhysicalShippingEndToEndTests.java`
- Create: `src/test/java/com/dongqh/luckyhub/shipping/PhysicalShippingConcurrencyTests.java`
- Create: `src/test/java/com/dongqh/luckyhub/shipping/PhysicalShippingSafetyTests.java`
- Create: `src/test/java/com/dongqh/luckyhub/shipping/ShippingTestFixture.java`
- Create: `docs/physical-shipping-api.md`
- Modify: `README.md`
- Document: `docs/progress/阶段6-任务8-三来源端到端完成介绍.md`

**Interfaces:**
- Produces executable examples for addresses, physical cash purchase, points redemption, lottery claim, worker execution, simulated tracking and user/admin queries.

- [ ] **Step 1: Write three real end-to-end scenarios**

Each scenario creates real catalog/reward/activity/account data, uses the public service/controller path, runs the fulfillment worker/projector, emits signed simulator callbacks and asserts one snapshot, one shipping order, one task, one simulator record, ordered tracks and terminal delivery.

- [ ] **Step 2: Add concurrency and privacy scans**

Repeat payment/claim/redemption/task/callback concurrently. Query all shipping, fulfillment, attempt, callback and simulator JSON/text columns and capture relevant application logs; assert absence of the fixture's full receiver, phone and detailed address.

- [ ] **Step 3: Run RED, implement only exposed gaps, then GREEN**

Any new defect first gets a focused failing assertion. Run:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 '-Dtest=PhysicalShippingEndToEndTests,PhysicalShippingConcurrencyTests,PhysicalShippingSafetyTests' test
```

Expected final result: all selected tests pass with no skips.

- [ ] **Step 4: Run Phase 6 focused acceptance**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 '-Dtest=ShippingSchemaContractTests,ShippingDomainContractTests,AddressCipherTests,ShippingAddressServiceTests,ShippingAddressControllerTests,ShippingAddressSnapshotTests,PhysicalOrderAddressIntegrationTests,PhysicalBenefitClaimTests,PhysicalClaimConcurrencyTests,PhysicalClaimExpiryTests,ShippingOrderServiceTests,PhysicalSourceShippingTests,LogisticsPrivacyBoundaryTests,LogisticsCallbackTests,LogisticsCallbackConcurrencyTests,ShippingQueryControllerTests,ShippingAdminServiceTests,ShippingAdminControllerTests,ShippingFailureProjectionTests,PhysicalShippingEndToEndTests,PhysicalShippingConcurrencyTests,PhysicalShippingSafetyTests' test
```

- [ ] **Step 5: Document and commit**

Explain all three timelines and why they converge after source validation. Commit `test: verify three physical shipping flows`.

### Task 9: Fresh migration, full regression, executable JAR and handoff

**Files:**
- Create: `scripts/Verify-Phase6FreshMigration.ps1`
- Modify: `src/test/java/com/dongqh/luckyhub/infrastructure/DatabaseSchemaMigrationTests.java`
- Modify: `src/test/java/com/dongqh/luckyhub/lottery/LotteryMigrationGuardTests.java`
- Modify: `docs/LuckyHub-迷你商城下一阶段执行总路线.md`
- Modify: `docs/LuckyHub-开发进度交接总结.md`
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-08-09-physical-claim-and-shipping.md`
- Document: `docs/progress/阶段6-任务9-阶段交付完成介绍.md`

**Interfaces:**
- Produces a runnable V1-V17 project, exact new test/table/JAR evidence, and a next-route decision without claiming refunds or real courier integration.

- [ ] **Step 1: Create safe V17 fresh-schema verification**

Clone the Phase 5 script safety pattern with a random `luckyhub_phase6_verify_<guid>` schema, separate creation/grant tracking, `finally` revoke/drop, cleanup failure propagation, exact Flyway V17 assertion and dynamically verified business-table count.

- [ ] **Step 2: Run fresh migration and critical concurrency**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Verify-Phase6FreshMigration.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 '-Dtest=PhysicalClaimConcurrencyTests,LogisticsCallbackConcurrencyTests,PhysicalShippingConcurrencyTests' test
```

Confirm no `luckyhub_phase6_verify_%` schema remains.

- [ ] **Step 3: Run full suite and package from fresh commands**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 test
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 package '-DskipTests'
```

Record exact test count, failures/errors/skips and JAR byte size. Do not reuse Phase 5 numbers.

- [ ] **Step 4: Run executable JAR and OpenAPI smoke**

Start `target/luckyhub-0.0.1-SNAPSHOT.jar` on a free local port with MySQL, Redis, `SHIPPING_ADDRESS_KEY` and `SHIPPING_CALLBACK_SECRET`; wait for `/v3/api-docs`; assert address, claim, shipping query, callback and admin routes; stop only the launched process.

- [ ] **Step 5: Run static delivery and requirement checks**

Run `git diff --check`, strict UTF-8 decode, Markdown local-link validation, secret/PII fixture scan, plan checkbox scan and task-document count. Review the Phase 6 diff for Critical/Important issues in encryption, idempotency, transaction boundaries, source ownership, state monotonicity, replay prevention, inventory compensation and privacy. Reproduce every defect with a RED test before fixing it.

- [ ] **Step 6: Update handoff, check all boxes and commit**

Mark Phase 6 complete, record exact evidence and known boundaries, and identify the next optional phase without inventing Phase 7 scope. Commit `docs: hand off phase six physical shipping`.

## Completion Boundary

Phase 6 is complete only when every checkbox is checked, all nine Chinese progress documents exist, all three physical sources reach delivery through one shipping aggregate, duplicate and concurrent calls remain idempotent, claims race safely with expiry, callbacks are signed/replay-safe/monotonic, no plaintext PII persists outside encrypted address storage, V1-V17 migrate from an empty schema, the full suite and executable JAR pass, tracked worktree is clean, and no Critical/Important finding remains.
