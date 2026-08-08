# LuckyHub Coupon, Membership, and Cash Order Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build coupon assets, renewable memberships, single-SKU cash orders, deterministic pricing snapshots, and idempotent simulated payment lifecycle on the existing MALL inventory.

**Architecture:** Keep the modular Spring Boot monolith. `coupon` owns templates and user-coupon state; `membership` owns products and user validity; `order` owns pricing snapshots and the transaction coordinating MALL inventory and coupons; `payment` owns signed simulated callbacks and timeout cancellation. MySQL remains the final asset ledger and all user reads are self-scoped.

**Tech Stack:** Java 17, Spring Boot 4.1.0, MyBatis-Plus, MySQL 8.4, Flyway, Jakarta Validation, JUnit 5, AssertJ, MockMvc, Maven via `scripts/Invoke-Maven.ps1`.

## Global Constraints

- Work directly on current `master`, as previously approved; preserve `.codex-progress/` and `.superpowers/`.
- Do not modify V1-V10. Create V11, V12, and V13.
- Money is signed Java `long`, non-negative MySQL `BIGINT`, unit cents; arithmetic uses exact operations.
- Single enabled cash SKU, quantity 1-100, MALL channel only; no cart, refund, address, logistics, real payment, points mixing, or points reward.
- Price order is original amount -> membership discount -> coupon -> payable, minimum payable zero.
- Coupon states: AVAILABLE -> LOCKED -> USED; LOCKED -> AVAILABLE on cancel/timeout; AVAILABLE -> EXPIRED.
- Order states in this phase: PENDING_PAYMENT -> PAID or CANCELLED. Later fulfillment states remain out of scope.
- Same-level membership extends from `max(current expiry, now)` and history is additive.
- Every task creates `docs/progress/阶段3-任务N-*.md` with a concrete example and ends in one commit.

---

## Task 1: Add Phase 3 schema and domain contracts

**Files:** create V11/V12/V13 migrations; create coupon/membership/order/payment enums, entities, DTOs, views and mappers; create schema/domain contract tests and completion introduction.

- [x] Write schema/domain tests for tables, unique keys, CHECK constraints, permissions, DTO bounds and stable error codes.
- [x] Run `'-Dtest=Phase3SchemaContractTests,Phase3DomainContractTests' test` and verify RED because V11-V13/types are absent.
- [x] Add V11 coupon tables, V12 membership tables, V13 cash order/payment tables and permission seeds.
- [x] Add focused domain types and mapper interfaces; run the tests GREEN.
- [x] Document a fixed-amount coupon, yearly membership and cash-order snapshot example; commit `feat: add phase three commerce schema`.

## Task 2: Implement coupon assets and state machine

**Interfaces:** `CouponService.createTemplate`, `issue`, `pageMine`, `lockForOrder`, `useForOrder`, `releaseForOrder`, `expireAvailable`.

- [x] Write failing service and concurrency tests for template validation, per-user issue limit, product scope, validity, lock/use/release/expire, idempotency and two orders racing for one coupon.
- [x] Run `'-Dtest=CouponServiceTests,CouponConcurrencyTests' test` and verify RED.
- [x] Implement conditional mapper updates and transactional service; never edit used/expired history.
- [x] Run coupon tests GREEN and related schema regression.
- [x] Document a `满100减20` coupon locked by order A while order B is rejected; commit `feat: manage coupon assets`.

## Task 3: Implement renewable membership

**Interfaces:** `MembershipService.createProduct`, `purchase`, `getMine`, `activeBenefit`.

- [x] Write failing tests for MONTH/QUARTER/YEAR products, idempotent purchase, same-level extension, inactive membership and concurrent duplicate purchase.
- [x] Run `'-Dtest=MembershipServiceTests,MembershipConcurrencyTests' test` and verify RED.
- [x] Implement row locking, `max(expiresAt, now) + durationDays`, immutable grant history and benefit snapshots.
- [x] Run membership tests GREEN.
- [x] Document a yearly card renewed 30 days early without losing the remaining 30 days; commit `feat: renew member benefits`.

## Task 4: Implement deterministic pricing and atomic cash-order creation

**Interfaces:** add `CatalogService.findPurchasableSku`; create `OrderPricingService.quote`; `CashOrderService.create/get/page`.

- [x] Write failing pricing/order tests for cash-enabled SKU, exact multiplication, membership basis points, coupon threshold/scope/stacking, minimum zero, snapshots, idempotency and rollback.
- [x] Run `'-Dtest=OrderPricingServiceTests,CashOrderServiceTests' test` and verify RED.
- [x] Implement price calculation and claim-first PENDING_PAYMENT order creation: snapshot -> MALL reserve -> coupon lock -> persisted snapshot in one transaction.
- [x] Run order, coupon and inventory regressions GREEN.
- [x] Document `100元 -> 9折 -> 满80减20 -> 应付70元`; commit `feat: create priced cash orders`.

## Task 5: Implement simulated payment, callback, cancellation and timeout

**Interfaces:** `PaymentService.create`, `callback`; `CashOrderService.cancel`, `cancelExpired`; signed callback supports PROCESSING/SUCCESS/FAILURE.

- [x] Write failing tests for stable payment numbers, amount identity, signature rejection, duplicate success callback, failed/processing results, success confirmation, cancellation and timeout release.
- [x] Run `'-Dtest=PaymentServiceTests,OrderCancellationTests,PaymentConcurrencyTests' test` and verify RED.
- [x] Implement payment records, SHA-256 simulation signature verification, conditional transitions, inventory confirm/coupon use on success, and release on cancel/timeout.
- [x] Add bounded timeout scheduler and run payment/order regressions GREEN.
- [x] Document duplicate callback and a 30-minute timeout releasing both coupon and MALL stock; commit `feat: simulate cash order payments`.

## Task 6: Expose Phase 3 APIs and security

**Endpoints:** admin coupon/member product creation and issue; user coupon/member reads and membership purchase; cash-order create/read/page/cancel; payment create and signed callback.

- [x] Write failing controller and real filter/interceptor tests covering self scope, 201/200, validation, 401/403 and stable business errors.
- [x] Run `'-Dtest=Phase3ControllerTests,Phase3SecurityChainIntegrationTests' test` and verify RED.
- [x] Add thin controllers, permission constants and `/api/coupons/*`, `/api/memberships/*`, `/api/orders/*`, `/api/payments/*` security mappings; callback remains signature-authenticated.
- [x] Run API/security and lottery security regression GREEN.
- [x] Document PowerShell flows from admin setup through paid/cancelled order; commit `feat: expose phase three commerce API`.

## Task 7: Prove concurrency and end-to-end safety

- [x] Add an end-to-end test: issue coupon -> buy membership -> create discounted order -> create payment -> signed success callback -> PAID, coupon USED, MALL stock CONFIRMED.
- [x] Add concurrent duplicate order/payment/cancel/callback tests and cross-user denial tests; verify RED for any uncovered gap before fixing it.
- [x] Run all Phase 3 focused tests repeatedly and old points/inventory/lottery regressions.
- [x] Review negative money, coupon double-use, membership over-extension, stock/coupon half-success, callback replay, snapshots and raw-error leakage; fix every Critical/Important issue through RED-GREEN.
- [x] Document the full `199元商品 + 年卡9折 + 20元券` lifecycle; commit `test: prove phase three commerce safety`.

## Task 8: Document, migrate, verify and hand off Phase 3

- [ ] Create `docs/coupon-membership-order-api.md`; update README, master route and handoff with exact endpoints, permissions, states and boundaries.
- [ ] Verify an empty temporary schema migrates V1->V13, then revoke its grant and drop only that schema in `finally`.
- [ ] Run the Phase 3 focused suite, full `test`, `package '-DskipTests'`, `git diff --check`, link/UTF-8 checks and JAR OpenAPI smoke test.
- [ ] Record exact counts, artifact size, asset review and known boundaries; mark every plan checkbox complete.
- [ ] Document a start-to-finish local run example; commit `docs: hand off phase three commerce`.

## Completion Boundary

Phase 3 is complete only when all tasks are checked, V1-V13 migrate from empty schema, focused/full tests and package pass, JAR smoke succeeds, every task has a Chinese explanation, and no Critical/Important asset finding remains. Do not implement Phase 4 Gateways, reward fulfillment migration, addresses or logistics.
