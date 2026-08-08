# Catalog, Reward, and Channel Inventory Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the product/SKU catalog, versioned reward definitions, and idempotent channel inventory foundation without changing the behavior of the existing lottery flow.

**Architecture:** Add new catalog and reward tables beside the existing `marketing_prize` model, link them through a nullable compatibility field, and keep current lottery reads untouched. Add a separate channel inventory aggregate with short MySQL transactions, conditional updates, reservations, and immutable ledgers so later order and lottery plans can consume stable interfaces.

**Tech Stack:** Java 17, Spring Boot 4.1.0, MyBatis-Plus 3.5.17, MySQL 8.4, Flyway, JUnit 5, AssertJ, Spring MVC test, PowerShell 7.

## Global Constraints

- The approved design is `docs/superpowers/specs/2026-08-08-lottery-mini-mall-design.md`.
- Keep the project as a modular Spring Boot monolith; do not create deployable microservices.
- Do not modify published migrations V1-V7; create V8 and V9 only in this plan.
- Existing lottery, prize, activity, Outbox, Redis Stream, and benefit behavior must remain unchanged.
- Money uses integer cents (`BIGINT UNSIGNED` / `long`); points use non-negative integers (`BIGINT UNSIGNED` / `long`).
- Mall sale, points redemption, and lottery stock do not borrow from each other automatically.
- MySQL is the final source of truth; Redis is not used by this phase.
- Every stock mutation has a stable business number and an immutable ledger row.
- Use PowerShell 7 and `scripts/Invoke-Maven.ps1`; do not invoke a system Maven command directly.
- Use UTF-8 for Chinese text and `apply_patch` for edits.
- Do not add `.codex-progress/`, `.superpowers/`, `.env`, or credential CSV files to Git.

---

## File Structure

New catalog files live under `catalog`; reward definitions live under `reward`; the existing `inventory` package gains channel-stock classes while retaining `ActivityPrizeInventoryService` unchanged.

```text
src/main/java/com/dongqh/luckyhub/catalog/
  controller/CatalogController.java
  controller/CatalogAdminController.java
  dto/CreateProductCommand.java
  dto/ProductQuery.java
  entity/Product.java
  entity/ProductSku.java
  enums/ProductErrorCode.java
  enums/ProductType.java
  mapper/ProductMapper.java
  mapper/ProductSkuMapper.java
  service/CatalogService.java
  service/CatalogServiceImpl.java
  vo/ProductView.java
  vo/SkuView.java

src/main/java/com/dongqh/luckyhub/reward/
  controller/RewardDefinitionController.java
  dto/CreateRewardDefinitionCommand.java
  entity/RewardDefinition.java
  enums/RewardErrorCode.java
  enums/RewardType.java
  mapper/RewardDefinitionMapper.java
  service/RewardDefinitionService.java
  service/RewardDefinitionServiceImpl.java
  vo/RewardDefinitionView.java

src/main/java/com/dongqh/luckyhub/inventory/channel/
  controller/ChannelInventoryController.java
  dto/AllocateChannelStockCommand.java
  dto/InitializeSkuStockCommand.java
  dto/ReserveChannelStockCommand.java
  entity/ChannelInventory.java
  entity/InventoryLedger.java
  entity/InventoryReservation.java
  entity/SkuInventory.java
  enums/InventoryOperation.java
  enums/InventoryReservationStatus.java
  enums/InventoryErrorCode.java
  mapper/ChannelInventoryMapper.java
  mapper/InventoryLedgerMapper.java
  mapper/InventoryReservationMapper.java
  mapper/SkuInventoryMapper.java
  service/ChannelInventoryService.java
  service/ChannelInventoryServiceImpl.java
  vo/ChannelInventoryView.java

src/main/resources/db/migration/
  V8__add_catalog_and_reward_foundation.sql
  V9__add_channel_inventory.sql
```

## Task 1: Establish a clean baseline and add V8 schema contracts

**Files:**
- Create: `src/test/java/com/dongqh/luckyhub/catalog/CatalogRewardSchemaContractTests.java`
- Create: `src/main/resources/db/migration/V8__add_catalog_and_reward_foundation.sql`
- Modify: `src/main/java/com/dongqh/luckyhub/rbac/constant/PermissionCodes.java`

**Interfaces:**
- Consumes: Existing Flyway V1-V7 schema and `ADMIN`/`USER` roles.
- Produces: `product`, `product_sku`, `reward_definition`, nullable `marketing_prize.reward_definition_id`, and permission codes `catalog:read`, `catalog:manage`, `reward:manage`, `inventory:manage`.

- [x] **Step 1: Verify the pre-change baseline**

Run:

```powershell
docker compose up -d
docker compose ps
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 test
```

Expected: MySQL and Redis report healthy; Maven reports all existing tests passed. Record the test count in the commit notes. Stop if the baseline is red.

- [x] **Step 2: Write the failing V8 schema contract test**

Create `CatalogRewardSchemaContractTests.java` with these exact assertions:

```java
package com.dongqh.luckyhub.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CatalogRewardSchemaContractTests {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void createsCatalogRewardTablesAndCompatibilityLink() {
        assertThat(tableExists("product")).isTrue();
        assertThat(tableExists("product_sku")).isTrue();
        assertThat(tableExists("reward_definition")).isTrue();
        assertThat(columnExists("marketing_prize", "reward_definition_id")).isTrue();
    }

    @Test
    void seedsCatalogRewardAndInventoryPermissions() {
        List<String> permissions = jdbcTemplate.queryForList("""
                SELECT permission_code FROM sys_permission
                WHERE permission_code IN (
                    'catalog:read', 'catalog:manage', 'reward:manage', 'inventory:manage'
                )
                """, String.class);
        assertThat(permissions).containsExactlyInAnyOrder(
                "catalog:read", "catalog:manage", "reward:manage", "inventory:manage");
    }

    @Test
    void grantsReadToUserAndAllNewPermissionsToAdmin() {
        assertThat(rolePermissions("USER")).contains("catalog:read");
        assertThat(rolePermissions("ADMIN")).contains(
                "catalog:read", "catalog:manage", "reward:manage", "inventory:manage");
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = ?
                """, Integer.class, tableName);
        return count != null && count == 1;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """, Integer.class, tableName, columnName);
        return count != null && count == 1;
    }

    private List<String> rolePermissions(String roleCode) {
        return jdbcTemplate.queryForList("""
                SELECT permission.permission_code
                FROM sys_role_permission relation
                JOIN sys_role role_record ON role_record.id = relation.role_id
                JOIN sys_permission permission ON permission.id = relation.permission_id
                WHERE role_record.role_code = ?
                """, String.class, roleCode);
    }
}
```

- [x] **Step 3: Run the schema contract and verify it fails**

Run:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=CatalogRewardSchemaContractTests' test
```

Expected: FAIL because V8 tables and permissions do not exist.

- [x] **Step 4: Add the V8 migration**

Create `V8__add_catalog_and_reward_foundation.sql` with:

```sql
CREATE TABLE product (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    product_type VARCHAR(20) NOT NULL,
    image_url VARCHAR(500) NULL,
    description VARCHAR(1000) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_code (product_code),
    KEY idx_product_status_created (status, created_at),
    CONSTRAINT chk_product_type CHECK (product_type IN ('PHYSICAL', 'VIRTUAL')),
    CONSTRAINT chk_product_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品';

CREATE TABLE product_sku (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id BIGINT UNSIGNED NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    sku_name VARCHAR(100) NOT NULL,
    cash_price_cent BIGINT UNSIGNED NULL,
    points_price BIGINT UNSIGNED NULL,
    cash_enabled TINYINT NOT NULL DEFAULT 0,
    points_enabled TINYINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_sku_code (sku_code),
    KEY idx_product_sku_product (product_id, status),
    CONSTRAINT chk_product_sku_cash CHECK (
        (cash_enabled = 0) OR (cash_enabled = 1 AND cash_price_cent IS NOT NULL)
    ),
    CONSTRAINT chk_product_sku_points CHECK (
        (points_enabled = 0) OR (points_enabled = 1 AND points_price IS NOT NULL)
    ),
    CONSTRAINT chk_product_sku_enabled CHECK (cash_enabled IN (0, 1) AND points_enabled IN (0, 1)),
    CONSTRAINT chk_product_sku_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品SKU';

CREATE TABLE reward_definition (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reward_code VARCHAR(64) NOT NULL,
    reward_name VARCHAR(100) NOT NULL,
    reward_type VARCHAR(30) NOT NULL,
    target_id BIGINT UNSIGNED NULL,
    quantity BIGINT UNSIGNED NOT NULL,
    config_snapshot JSON NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_reward_definition_code (reward_code),
    KEY idx_reward_definition_type_status (reward_type, status),
    CONSTRAINT chk_reward_definition_type CHECK (
        reward_type IN ('PRODUCT', 'COUPON', 'POINTS', 'MEMBERSHIP', 'DRAW_CHANCE')
    ),
    CONSTRAINT chk_reward_definition_quantity CHECK (quantity > 0),
    CONSTRAINT chk_reward_definition_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一奖励定义';

ALTER TABLE marketing_prize
    ADD COLUMN reward_definition_id BIGINT UNSIGNED NULL AFTER prize_type,
    ADD KEY idx_marketing_prize_reward_definition (reward_definition_id);

INSERT INTO sys_permission (permission_code, permission_name)
SELECT seed.permission_code, seed.permission_name
FROM (
    SELECT 'catalog:read' AS permission_code, '查询商城商品' AS permission_name
    UNION ALL SELECT 'catalog:manage', '管理商城商品'
    UNION ALL SELECT 'reward:manage', '管理统一奖励定义'
    UNION ALL SELECT 'inventory:manage', '管理渠道库存'
) seed
LEFT JOIN sys_permission existing_permission
    ON existing_permission.permission_code = seed.permission_code
WHERE existing_permission.id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role_record.id, permission.id
FROM sys_role role_record
JOIN sys_permission permission ON permission.permission_code = 'catalog:read'
LEFT JOIN sys_role_permission existing_relation
    ON existing_relation.role_id = role_record.id
    AND existing_relation.permission_id = permission.id
WHERE role_record.role_code IN ('USER', 'ADMIN')
  AND existing_relation.role_id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT admin_role.id, permission.id
FROM sys_role admin_role
JOIN sys_permission permission ON permission.permission_code IN (
    'catalog:manage', 'reward:manage', 'inventory:manage'
)
LEFT JOIN sys_role_permission existing_relation
    ON existing_relation.role_id = admin_role.id
    AND existing_relation.permission_id = permission.id
WHERE admin_role.role_code = 'ADMIN'
  AND existing_relation.role_id IS NULL;
```

- [x] **Step 5: Add permission constants**

Add these constants before the private constructor in `PermissionCodes.java`:

```java
public static final String CATALOG_READ = "catalog:read";
public static final String CATALOG_MANAGE = "catalog:manage";
public static final String REWARD_MANAGE = "reward:manage";
public static final String INVENTORY_MANAGE = "inventory:manage";
```

- [x] **Step 6: Run V8 contracts and the existing migration tests**

Run:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=CatalogRewardSchemaContractTests,DatabaseSchemaMigrationTests,LotteryMigrationGuardTests,LotterySchemaContractTests' test
```

Expected: PASS. Flyway current version is 8; old V5 guard behavior remains covered.

- [x] **Step 7: Commit V8**

```powershell
git add -- src/main/resources/db/migration/V8__add_catalog_and_reward_foundation.sql `
  src/main/java/com/dongqh/luckyhub/rbac/constant/PermissionCodes.java `
  src/test/java/com/dongqh/luckyhub/catalog/CatalogRewardSchemaContractTests.java
git commit -m "feat: add catalog and reward schema"
```

## Task 2: Add catalog and reward domain contracts

**Files:**
- Create: all `catalog/entity`, `catalog/enums`, `catalog/mapper`, `reward/entity`, `reward/enums`, and `reward/mapper` files listed in File Structure.
- Create: `src/test/java/com/dongqh/luckyhub/catalog/CatalogDomainContractTests.java`
- Create: `src/test/java/com/dongqh/luckyhub/reward/RewardDomainContractTests.java`
- Modify: `src/main/java/com/dongqh/luckyhub/prize/entity/MarketingPrize.java`

**Interfaces:**
- Consumes: V8 tables.
- Produces: `ProductType`, `RewardType`, MyBatis entities/mappers, and nullable `MarketingPrize.rewardDefinitionId`.

- [x] **Step 1: Write failing enum and entity contract tests**

Use these exact enum expectations:

```java
assertThat(ProductType.values()).containsExactly(ProductType.PHYSICAL, ProductType.VIRTUAL);
assertThat(RewardType.values()).containsExactly(
        RewardType.PRODUCT, RewardType.COUPON, RewardType.POINTS,
        RewardType.MEMBERSHIP, RewardType.DRAW_CHANCE);
```

In `CatalogDomainContractTests`, insert a `Product`, insert its `ProductSku`, reload both mappers, and assert every persisted field including integer-cent and points prices. In `RewardDomainContractTests`, insert one `POINTS` definition with `targetId == null`, quantity `500`, and JSON `{"source":"test"}`, then assert the reload is byte-for-byte equivalent for business fields.

- [x] **Step 2: Run tests and verify compilation fails**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=CatalogDomainContractTests,RewardDomainContractTests' test
```

Expected: FAIL because the domain classes do not exist.

- [x] **Step 3: Add exact enums and mapper interfaces**

```java
package com.dongqh.luckyhub.catalog.enums;
public enum ProductType { PHYSICAL, VIRTUAL }
```

```java
package com.dongqh.luckyhub.reward.enums;
public enum RewardType { PRODUCT, COUPON, POINTS, MEMBERSHIP, DRAW_CHANCE }
```

Each mapper is intentionally minimal:

```java
public interface ProductMapper extends BaseMapper<Product> {}
public interface ProductSkuMapper extends BaseMapper<ProductSku> {}
public interface RewardDefinitionMapper extends BaseMapper<RewardDefinition> {}
```

Use the project entity conventions: `@TableName`, `@TableId(type = IdType.AUTO)`, Lombok getters/setters, `FieldFill.INSERT`, and `FieldFill.INSERT_UPDATE`. Map every V8 column with Java types `Long`, `String`, `Boolean`, `Integer`, `LocalDateTime`, `ProductType`, and `RewardType`. Store `configSnapshot` as `String`; do not add a JSON library abstraction in this phase.

- [x] **Step 4: Add the compatibility field without changing old behavior**

Add to `MarketingPrize.java` immediately after `prizeType`:

```java
private Long rewardDefinitionId;
```

Do not change `CreatePrizeCommand`, `PrizeServiceImpl`, activity prize services, draw snapshots, or lottery events in this phase.

- [x] **Step 5: Run domain contracts and legacy prize/lottery contracts**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=CatalogDomainContractTests,RewardDomainContractTests,PrizeDomainModelTests,PrizeContractTests,LotteryPersistenceContractTests' test
```

Expected: PASS; old prize persistence accepts a null reward definition.

- [x] **Step 6: Commit domain contracts**

```powershell
git add -- src/main/java/com/dongqh/luckyhub/catalog `
  src/main/java/com/dongqh/luckyhub/reward `
  src/main/java/com/dongqh/luckyhub/prize/entity/MarketingPrize.java `
  src/test/java/com/dongqh/luckyhub/catalog/CatalogDomainContractTests.java `
  src/test/java/com/dongqh/luckyhub/reward/RewardDomainContractTests.java
git commit -m "feat: define catalog and reward domains"
```

## Task 3: Implement catalog management and user reads

**Files:**
- Create: catalog DTO, service, controller, and VO files from File Structure.
- Create: `src/test/java/com/dongqh/luckyhub/catalog/CatalogServiceTests.java`
- Create: `src/test/java/com/dongqh/luckyhub/catalog/CatalogControllerTests.java`

**Interfaces:**
- Consumes: `ProductMapper`, `ProductSkuMapper`, `PermissionCodes.CATALOG_READ`, and `PermissionCodes.CATALOG_MANAGE`.
- Produces: `CatalogService.create(CreateProductCommand)`, `CatalogService.get(long)`, `CatalogService.page(ProductQuery)`, `POST /api/admin/products`, `GET /api/products`, and `GET /api/products/{id}`.

- [x] **Step 1: Define and test the create command contract**

Create this record exactly:

```java
public record CreateProductCommand(
        @NotBlank @Size(max = 64) String productCode,
        @NotBlank @Size(max = 100) String productName,
        @NotNull ProductType productType,
        @Size(max = 500) String imageUrl,
        @Size(max = 1000) String description,
        @NotBlank @Size(max = 64) String skuCode,
        @NotBlank @Size(max = 100) String skuName,
        @PositiveOrZero Long cashPriceCent,
        @PositiveOrZero Long pointsPrice,
        @NotNull Boolean cashEnabled,
        @NotNull Boolean pointsEnabled) {}
```

Add class-level validation in the compact constructor:

```java
if (Boolean.TRUE.equals(cashEnabled) && cashPriceCent == null) {
    throw new IllegalArgumentException("cashPriceCent is required when cashEnabled is true");
}
if (Boolean.TRUE.equals(pointsEnabled) && pointsPrice == null) {
    throw new IllegalArgumentException("pointsPrice is required when pointsEnabled is true");
}
if (!Boolean.TRUE.equals(cashEnabled) && !Boolean.TRUE.equals(pointsEnabled)) {
    throw new IllegalArgumentException("at least one purchase mode must be enabled");
}
```

Create the query model exactly:

```java
@Getter
@Setter
public class ProductQuery {
    @Min(1)
    private long page = 1;

    @Min(1)
    @Max(100)
    private long size = 20;

    @Size(max = 100)
    private String name;

    private ProductType type;

    @Min(0)
    @Max(1)
    private Integer status;
}
```

Test valid cash-only, points-only, and both-enabled commands; test all three invalid combinations.

- [x] **Step 2: Write failing transactional service tests**

Cover these exact behaviors in `CatalogServiceTests`:

- creating a product inserts exactly one product and one default SKU in one transaction;
- duplicate `productCode` maps to `ProductErrorCode.PRODUCT_CODE_DUPLICATE`;
- duplicate `skuCode` maps to `ProductErrorCode.SKU_CODE_DUPLICATE`;
- a disabled product is not returned by the user list;
- `get(id)` returns product and SKU snapshots together;
- if SKU insertion fails, the product insertion rolls back.

Define stable errors:

```java
PRODUCT_NOT_FOUND(44001, "商品不存在", HttpStatus.NOT_FOUND),
PRODUCT_CODE_DUPLICATE(44002, "商品编码已存在", HttpStatus.CONFLICT),
SKU_CODE_DUPLICATE(44003, "SKU编码已存在", HttpStatus.CONFLICT),
PRODUCT_CONFIG_INVALID(44004, "商品配置不合法", HttpStatus.BAD_REQUEST)
```

- [x] **Step 3: Implement the service interfaces**

Use these signatures:

```java
public interface CatalogService {
    ProductView create(CreateProductCommand command);
    ProductView get(long productId);
    PageResponse<ProductView> page(ProductQuery query);
}
```

`create` is one `@Transactional` method. Normalize all user strings with trim semantics matching `PrizeServiceImpl`. Catch `DuplicateKeyException`, inspect whether the normalized code already exists, and translate to the stable error code; never return raw SQL messages.

`ProductView` contains:

```java
public record ProductView(
        Long id, String productCode, String productName, ProductType productType,
        String imageUrl, String description, Integer status,
        List<SkuView> skus, LocalDateTime createdAt, LocalDateTime updatedAt) {}
```

`SkuView` contains all price flags and the SKU status/version. Return immutable lists using `List.copyOf`.

- [x] **Step 4: Run service tests**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=CatalogServiceTests' test
```

Expected: PASS.

- [x] **Step 5: Write failing controller security and JSON tests**

Verify:

- `POST /api/admin/products` returns 201 and requires `catalog:manage`;
- `GET /api/products` and `GET /api/products/{id}` require `catalog:read`;
- invalid commands return the existing validation envelope;
- user JSON exposes prices and purchase flags but not inventory counts;
- the real authentication filter and permission interceptor return 401/403 on missing identity/permission.

- [x] **Step 6: Implement controllers**

Use separate controllers so user reads never inherit admin routing. `CatalogController` has class mapping `/api/products`, a `GET` method receiving `@Valid @ModelAttribute ProductQuery`, and a `GET /{id}` method receiving `@Positive long id`; both use `@RequirePermission(PermissionCodes.CATALOG_READ)`. `CatalogAdminController` has class mapping `/api/admin/products` and a `POST` method receiving `@Valid @RequestBody CreateProductCommand`, returning `ApiResponse<ProductView>` with `@ResponseStatus(HttpStatus.CREATED)` and `@RequirePermission(PermissionCodes.CATALOG_MANAGE)`.

Follow the response conventions from `PrizeController`: `ApiResponse`, `PageResponse`, `@Valid`, `@ResponseStatus(HttpStatus.CREATED)`, and `@RequirePermission`.

- [x] **Step 7: Run controller and security tests**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=CatalogControllerTests,LotterySecurityChainIntegrationTests' test
```

Expected: PASS.

- [x] **Step 8: Commit catalog APIs**

```powershell
git add -- src/main/java/com/dongqh/luckyhub/catalog `
  src/test/java/com/dongqh/luckyhub/catalog
git commit -m "feat: manage catalog products"
```

## Task 4: Implement reward definition management

**Files:**
- Create: reward DTO, service, controller, and VO files from File Structure.
- Create: `src/test/java/com/dongqh/luckyhub/reward/RewardDefinitionServiceTests.java`
- Create: `src/test/java/com/dongqh/luckyhub/reward/RewardDefinitionControllerTests.java`

**Interfaces:**
- Consumes: `RewardDefinitionMapper`, `ProductSkuMapper`, and `PermissionCodes.REWARD_MANAGE`.
- Produces: `RewardDefinitionService.create`, `RewardDefinitionService.get`, `POST /api/admin/reward-definitions`, and `GET /api/admin/reward-definitions/{id}`.

- [ ] **Step 1: Define the command and invariant tests**

Create:

```java
public record CreateRewardDefinitionCommand(
        @NotBlank @Size(max = 64) String rewardCode,
        @NotBlank @Size(max = 100) String rewardName,
        @NotNull RewardType rewardType,
        @Positive Long targetId,
        @NotNull @Positive Long quantity,
        @Size(max = 2000) String configSnapshot) {}
```

Service invariants:

- `PRODUCT`, `COUPON`, and `MEMBERSHIP` require `targetId`;
- `POINTS` and `DRAW_CHANCE` require `targetId == null`;
- `PRODUCT` target must reference an enabled SKU;
- only syntactically valid JSON is stored in `configSnapshot`; blank becomes null;
- reward code is immutable and unique.

Use `ObjectMapper.readTree` only for validation; store the normalized JSON string returned by `writeValueAsString(readTree(value))`.

- [ ] **Step 2: Write failing service tests**

Cover one successful definition for all five reward types, invalid target combinations, missing SKU, disabled SKU, duplicate code, blank JSON normalization, and malformed JSON.

Stable errors:

```java
REWARD_NOT_FOUND(45001, "奖励定义不存在", HttpStatus.NOT_FOUND),
REWARD_CODE_DUPLICATE(45002, "奖励编码已存在", HttpStatus.CONFLICT),
REWARD_TARGET_INVALID(45003, "奖励目标不合法", HttpStatus.BAD_REQUEST),
REWARD_CONFIG_INVALID(45004, "奖励配置不合法", HttpStatus.BAD_REQUEST)
```

- [ ] **Step 3: Implement service and view**

Use:

```java
public interface RewardDefinitionService {
    RewardDefinitionView create(CreateRewardDefinitionCommand command);
    RewardDefinitionView get(long id);
}
```

The returned view includes `id`, code, name, type, target ID, quantity, normalized config JSON, status, and timestamps. No method in this task attaches rewards to activities.

- [ ] **Step 4: Run reward service tests**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=RewardDefinitionServiceTests' test
```

Expected: PASS.

- [ ] **Step 5: Add and test admin endpoints**

Create `POST /api/admin/reward-definitions` and `GET /api/admin/reward-definitions/{id}`, both protected by `reward:manage`. Test 201, get-by-id, validation error, 401, and 403 behavior.

- [ ] **Step 6: Run reward API tests and commit**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=RewardDefinitionControllerTests,RewardDefinitionServiceTests' test
git add -- src/main/java/com/dongqh/luckyhub/reward `
  src/test/java/com/dongqh/luckyhub/reward
git commit -m "feat: manage reward definitions"
```

Expected: tests PASS before the commit is created.

## Task 5: Add V9 channel inventory schema and contracts

**Files:**
- Create: `src/main/resources/db/migration/V9__add_channel_inventory.sql`
- Create: `src/test/java/com/dongqh/luckyhub/inventory/ChannelInventorySchemaContractTests.java`

**Interfaces:**
- Consumes: `product_sku` from V8.
- Produces: SKU totals, channel allocation, reservations, and immutable ledger tables.

- [ ] **Step 1: Write the failing V9 contract**

Assert that these tables exist: `sku_inventory`, `inventory_channel_stock`, `inventory_reservation`, `inventory_ledger`. Assert unique indexes exist on `sku_inventory.sku_id`, `(sku_id, channel_code)`, `inventory_reservation.reservation_no`, and `inventory_ledger.business_no` by querying `information_schema.statistics`.

- [ ] **Step 2: Run and verify failure**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=ChannelInventorySchemaContractTests' test
```

Expected: FAIL because V9 does not exist.

- [ ] **Step 3: Create the exact V9 migration**

```sql
CREATE TABLE sku_inventory (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sku_id BIGINT UNSIGNED NOT NULL,
    total_stock INT UNSIGNED NOT NULL,
    allocated_stock INT UNSIGNED NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_inventory_sku (sku_id),
    CONSTRAINT chk_sku_inventory_allocation CHECK (allocated_stock <= total_stock)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SKU总库存';

CREATE TABLE inventory_channel_stock (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sku_id BIGINT UNSIGNED NOT NULL,
    channel_code VARCHAR(100) NOT NULL,
    allocated_stock INT UNSIGNED NOT NULL,
    available_stock INT UNSIGNED NOT NULL,
    reserved_stock INT UNSIGNED NOT NULL DEFAULT 0,
    consumed_stock INT UNSIGNED NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_stock_sku_channel (sku_id, channel_code),
    KEY idx_channel_stock_channel (channel_code),
    CONSTRAINT chk_channel_stock_balance CHECK (
        allocated_stock = available_stock + reserved_stock + consumed_stock
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SKU渠道库存';

CREATE TABLE inventory_reservation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reservation_no VARCHAR(64) NOT NULL,
    sku_id BIGINT UNSIGNED NOT NULL,
    channel_code VARCHAR(100) NOT NULL,
    quantity INT UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_reservation_no (reservation_no),
    KEY idx_inventory_reservation_sku_channel (sku_id, channel_code),
    CONSTRAINT chk_inventory_reservation_quantity CHECK (quantity > 0),
    CONSTRAINT chk_inventory_reservation_status CHECK (
        status IN ('RESERVED', 'CONFIRMED', 'RELEASED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道库存预占';

CREATE TABLE inventory_ledger (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    business_no VARCHAR(100) NOT NULL,
    sku_id BIGINT UNSIGNED NOT NULL,
    channel_code VARCHAR(100) NULL,
    operation VARCHAR(20) NOT NULL,
    quantity INT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_ledger_business_no (business_no),
    KEY idx_inventory_ledger_sku_created (sku_id, created_at),
    CONSTRAINT chk_inventory_ledger_quantity CHECK (quantity > 0),
    CONSTRAINT chk_inventory_ledger_operation CHECK (
        operation IN ('INITIALIZE', 'ALLOCATE', 'RESERVE', 'CONFIRM', 'RELEASE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变库存流水';
```

- [ ] **Step 4: Run V9 and all migration contracts**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=ChannelInventorySchemaContractTests,CatalogRewardSchemaContractTests,DatabaseSchemaMigrationTests,LotteryMigrationGuardTests' test
```

Expected: PASS; Flyway current version is 9.

- [ ] **Step 5: Commit V9**

```powershell
git add -- src/main/resources/db/migration/V9__add_channel_inventory.sql `
  src/test/java/com/dongqh/luckyhub/inventory/ChannelInventorySchemaContractTests.java
git commit -m "feat: add channel inventory schema"
```

## Task 6: Implement idempotent channel inventory operations

**Files:**
- Create: all `inventory/channel` entity, enum, mapper, service, DTO, and VO files from File Structure except the controller.
- Create: `src/test/java/com/dongqh/luckyhub/inventory/ChannelInventoryServiceTests.java`
- Create: `src/test/java/com/dongqh/luckyhub/inventory/ChannelInventoryConcurrencyTests.java`

**Interfaces:**
- Consumes: V9 tables and enabled SKU records.
- Produces: total stock initialization, allocation, reserve, confirm, and release operations.

- [ ] **Step 1: Define exact commands and service interface**

```java
public record InitializeSkuStockCommand(
        @NotNull @Positive Long skuId,
        @NotNull @Positive Integer totalStock,
        @NotBlank @Size(max = 100) String businessNo) {}
```

```java
public record AllocateChannelStockCommand(
        @NotNull @Positive Long skuId,
        @NotBlank @Size(max = 100) String channelCode,
        @NotNull @Positive Integer quantity,
        @NotBlank @Size(max = 100) String businessNo) {}
```

```java
public record ReserveChannelStockCommand(
        @NotNull @Positive Long skuId,
        @NotBlank @Size(max = 100) String channelCode,
        @NotNull @Positive Integer quantity,
        @NotBlank @Size(max = 64) String reservationNo) {}
```

```java
public interface ChannelInventoryService {
    ChannelInventoryView initialize(InitializeSkuStockCommand command);
    ChannelInventoryView allocate(AllocateChannelStockCommand command);
    ChannelInventoryView reserve(ReserveChannelStockCommand command);
    ChannelInventoryView confirm(String reservationNo);
    ChannelInventoryView release(String reservationNo);
    ChannelInventoryView get(long skuId, String channelCode);
}
```

Normalize channel codes to uppercase trimmed values. Reserve business ledger number is `RESERVE:{reservationNo}`, confirm is `CONFIRM:{reservationNo}`, and release is `RELEASE:{reservationNo}`.

- [ ] **Step 2: Write failing idempotency and state tests**

Cover:

- initialize creates one total row and one `INITIALIZE` ledger;
- repeating the same business number returns current state without a second ledger;
- reusing a business number with different SKU/quantity is `INVENTORY_IDEMPOTENCY_CONFLICT`;
- allocation cannot exceed unallocated total stock;
- reserve atomically moves available to reserved;
- confirm moves reserved to consumed exactly once;
- release moves reserved back to available exactly once;
- confirmed cannot be released and released cannot be confirmed;
- missing SKU/channel/reservation has a stable not-found error;
- old `ActivityPrizeInventoryService` remains independent.

Stable errors:

```java
INVENTORY_NOT_FOUND(46001, "库存不存在", HttpStatus.NOT_FOUND),
INVENTORY_INSUFFICIENT(46002, "可用库存不足", HttpStatus.CONFLICT),
INVENTORY_STATE_CONFLICT(46003, "库存状态冲突", HttpStatus.CONFLICT),
INVENTORY_IDEMPOTENCY_CONFLICT(46004, "库存幂等参数冲突", HttpStatus.CONFLICT),
INVENTORY_SKU_UNAVAILABLE(46005, "SKU不可用于库存配置", HttpStatus.BAD_REQUEST)
```

- [ ] **Step 3: Implement mapper conditional updates**

`SkuInventoryMapper.allocateIfAvailable`:

```java
@Update("""
        UPDATE sku_inventory
        SET allocated_stock = allocated_stock + #{quantity}, version = version + 1
        WHERE sku_id = #{skuId}
          AND total_stock - allocated_stock >= #{quantity}
        """)
int allocateIfAvailable(long skuId, int quantity);
```

`ChannelInventoryMapper.reserveIfAvailable`:

```java
@Update("""
        UPDATE inventory_channel_stock
        SET available_stock = available_stock - #{quantity},
            reserved_stock = reserved_stock + #{quantity},
            version = version + 1
        WHERE sku_id = #{skuId} AND channel_code = #{channelCode}
          AND available_stock >= #{quantity}
        """)
int reserveIfAvailable(long skuId, String channelCode, int quantity);
```

Also add conditional `confirmReserved` and `releaseReserved` updates that require `reserved_stock >= quantity`. Mapper methods return affected row count; the service translates zero rows to stable business errors.

- [ ] **Step 4: Implement short transactional service methods**

Every mutation is one local `@Transactional` method. Check the ledger or reservation first, validate every identity field on duplicate requests, perform the conditional update, write the reservation transition, then insert one immutable ledger row. Do not catch and ignore `DuplicateKeyException`; on a duplicate race, reload and validate the winning row before returning.

`ChannelInventoryView` contains:

```java
public record ChannelInventoryView(
        Long skuId, String channelCode, Integer totalStock,
        Integer allocatedStock, Integer availableStock,
        Integer reservedStock, Integer consumedStock,
        String reservationNo, InventoryReservationStatus reservationStatus) {}
```

- [ ] **Step 5: Run service tests**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=ChannelInventoryServiceTests' test
```

Expected: PASS.

- [ ] **Step 6: Write and run concurrency tests**

Create two tests:

1. Initialize total 20, allocate 10 to `MALL`, launch 100 simultaneous one-unit reservations with unique reservation numbers, assert exactly 10 succeed and no stock column becomes negative.
2. Deliver the same reservation number concurrently 20 times, assert one reservation row, one reserve ledger, and one unit reserved.

Run:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=ChannelInventoryConcurrencyTests' test
```

Expected: PASS within 30 seconds per test.

- [ ] **Step 7: Run legacy inventory and lottery concurrency tests**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=ActivityPrizeInventoryTests,LotteryConcurrencyTests,ChannelInventoryServiceTests,ChannelInventoryConcurrencyTests' test
```

Expected: PASS; legacy lottery activity stock is unaffected.

- [ ] **Step 8: Commit inventory services**

```powershell
git add -- src/main/java/com/dongqh/luckyhub/inventory/channel `
  src/test/java/com/dongqh/luckyhub/inventory/ChannelInventoryServiceTests.java `
  src/test/java/com/dongqh/luckyhub/inventory/ChannelInventoryConcurrencyTests.java
git commit -m "feat: manage idempotent channel inventory"
```

## Task 7: Expose channel inventory management API

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/inventory/channel/controller/ChannelInventoryController.java`
- Create: `src/test/java/com/dongqh/luckyhub/inventory/ChannelInventoryControllerTests.java`

**Interfaces:**
- Consumes: `ChannelInventoryService` and `PermissionCodes.INVENTORY_MANAGE`.
- Produces: inventory initialization, allocation, reservation lifecycle, and query endpoints for controlled development and later order integration.

- [ ] **Step 1: Write failing endpoint contract tests**

Test these exact endpoints:

```text
POST /api/admin/inventory/skus/initialize
POST /api/admin/inventory/channels/allocate
POST /api/admin/inventory/reservations
POST /api/admin/inventory/reservations/{reservationNo}/confirm
POST /api/admin/inventory/reservations/{reservationNo}/release
GET  /api/admin/inventory/skus/{skuId}/channels/{channelCode}
```

All require `inventory:manage`. Verify 201 for initialize/reserve, 200 for other successful calls, validation envelope, 401, 403, and stable business errors.

- [ ] **Step 2: Implement the controller**

Follow `PrizeController` conventions. Use `@Positive` for SKU IDs, `@Size(max = 100)` for path channel values after URL decoding, and `@Size(max = 64)` for reservation numbers. Controller methods only validate transport input and delegate; they contain no stock arithmetic.

- [ ] **Step 3: Run API tests**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=ChannelInventoryControllerTests,LotterySecurityChainIntegrationTests' test
```

Expected: PASS.

- [ ] **Step 4: Commit inventory API**

```powershell
git add -- src/main/java/com/dongqh/luckyhub/inventory/channel/controller `
  src/test/java/com/dongqh/luckyhub/inventory/ChannelInventoryControllerTests.java
git commit -m "feat: expose channel inventory API"
```

## Task 8: Document, verify, review, and hand off Phase 1

**Files:**
- Create: `docs/catalog-reward-inventory-api.md`
- Modify: `README.md`
- Modify: `docs/LuckyHub-迷你商城下一阶段执行总路线.md`
- Modify: `docs/LuckyHub-开发进度交接总结.md`

**Interfaces:**
- Consumes: all Phase 1 APIs, migrations, and test evidence.
- Produces: a reproducible handoff and the decision boundary for Phase 2.

- [ ] **Step 1: Write API and data-model documentation**

Document every Phase 1 endpoint with permission, request, response, error code, and PowerShell `Invoke-RestMethod` example. Document channel codes exactly:

```text
MALL
POINTS
LOTTERY:{activityId}
```

State that only `MALL` and `POINTS` are configured in Phase 1 and `LOTTERY:{activityId}` is reserved for Phase 5. Explain that the new reward definition is not yet used by current draws.

- [ ] **Step 2: Update README and handoff**

Add links to the approved design, master execution route, Phase 1 plan, and API document. In the handoff, list V8/V9, new permissions, packages, endpoints, exact test result, known boundaries, and the next phase.

- [ ] **Step 3: Run focused Phase 1 verification**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=CatalogRewardSchemaContractTests,CatalogDomainContractTests,CatalogServiceTests,CatalogControllerTests,RewardDomainContractTests,RewardDefinitionServiceTests,RewardDefinitionControllerTests,ChannelInventorySchemaContractTests,ChannelInventoryServiceTests,ChannelInventoryConcurrencyTests,ChannelInventoryControllerTests' test
```

Expected: all listed tests PASS with zero failures and zero errors.

- [ ] **Step 4: Run full regression and package verification**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 test
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 package '-DskipTests'
git diff --check
git status --short
```

Expected: full test suite PASS; package reports `BUILD SUCCESS`; `git diff --check` has no output; status contains only intended documentation changes plus the pre-existing untracked helper directories.

- [ ] **Step 5: Request code review**

Review against these explicit questions:

- Can any stock column become negative under concurrency?
- Can the same business/reservation number mutate a different SKU or quantity?
- Can allocated channel stock exceed SKU total stock?
- Does any new path modify current lottery stock or event behavior?
- Do user product responses leak inventory?
- Do raw SQL or duplicate-key messages escape through APIs?
- Are V1-V7 unchanged?

Fix every Critical or Important finding and rerun Steps 3-4.

- [ ] **Step 6: Mark Phase 1 complete in the master route**

Only after verification and review, change `docs/LuckyHub-迷你商城下一阶段执行总路线.md` so “当前阶段” points to Phase 2 planning. Record the final commit hash and fresh test count. Do not write Phase 2 implementation code in this commit.

- [ ] **Step 7: Commit the Phase 1 handoff**

```powershell
git add -- README.md docs/catalog-reward-inventory-api.md `
  docs/LuckyHub-迷你商城下一阶段执行总路线.md `
  docs/LuckyHub-开发进度交接总结.md
git commit -m "docs: hand off catalog inventory foundation"
git status --short --branch
```

Expected: tracked workspace is clean; only known untracked helper directories remain.

---

## Phase 1 Completion Boundary

Phase 1 is complete only when all eight tasks are checked, V8/V9 are validated on a fresh schema, focused and full tests pass, package succeeds, review has no unresolved Critical/Important findings, and the master route records the evidence.

Do not implement points accounts, redemption orders, coupons, memberships, payments, fulfillment workers, addresses, or logistics in this plan. After Phase 1 completion, create a separate Phase 2 implementation plan from the actual catalog and inventory interfaces delivered here.
