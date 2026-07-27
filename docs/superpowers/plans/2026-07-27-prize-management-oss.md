# Prize Management and Alibaba Cloud OSS Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Keep the user's existing prize skeletons and use `git commit --only` so unrelated staged work is never swept into a commit.

**Goal:** Deliver administrator prize creation, detail, filtered pagination, modification, idempotent disable, and secure image upload to a public Alibaba Cloud OSS bucket, with the public image URL stored in MySQL.

**Architecture:** The `prize` package owns HTTP DTOs, domain persistence, application services, and permission-protected controllers. Image validation and object-key generation live in an image service behind an `ObjectStorageGateway`; the Alibaba OSS SDK v2 adapter is enabled only when complete runtime configuration is present, while an unavailable adapter lets the application and all non-upload features start safely without credentials.

**Tech Stack:** Java 17, Spring Boot 4.1, Spring MVC, Jakarta Validation, MyBatis-Plus 3.5.17, Flyway/MySQL, Alibaba Cloud OSS Java SDK v2 0.5.0, JUnit 5, AssertJ, Mockito, MockMvc.

**Global Constraints:**

- Preserve all unrelated local and staged changes.
- Treat “delete” as idempotent disable (`status = 0`); never execute a physical delete.
- Store the stable public URL in `marketing_prize.image_url`.
- Accept only JPEG, PNG, and WebP images no larger than 5 MiB, validating both content and magic bytes.
- Generate object keys as `prizes/{yyyy}/{MM}/{uuid}.{ext}`.
- Never commit OSS credentials. OSS must default to disabled.
- Do not delete replaced or orphaned OSS objects in this phase.
- Every implementation step starts with a failing test and ends with focused and regression verification.

## File Responsibility Map

| Path | Responsibility |
|---|---|
| `prize/entity/MarketingPrize.java` | MyBatis-Plus persistence model |
| `prize/dto/*PrizeCommand.java` | Validated create/update request bodies |
| `prize/dto/PrizeQuery.java` | Validated page/filter query |
| `prize/vo/PrizeView.java` | Prize response projection |
| `common/result/PageResponse.java` | Stable pagination envelope |
| `prize/enums/PrizeErrorCode.java` | Prize and image business errors |
| `prize/service/PrizeService.java` | Prize use-case contract |
| `prize/service/impl/PrizeServiceImpl.java` | Create/read/page/update/disable logic |
| `prize/controller/PrizeController.java` | Permission-protected prize HTTP API |
| `prize/image/PrizeImageValidator.java` | Size, media type, and magic-byte validation |
| `prize/image/PrizeImageService.java` | Object key creation, upload orchestration, URL response |
| `prize/storage/ObjectStorageGateway.java` | Storage abstraction |
| `prize/storage/AliyunOssObjectStorageGateway.java` | Alibaba OSS SDK adapter |
| `prize/storage/UnavailableObjectStorageGateway.java` | Explicit disabled/missing-config failure |
| `prize/config/OssProperties.java` | `luckyhub.oss` configuration binding |
| `prize/config/OssConfiguration.java` | Conditional OSS client/gateway beans |
| `prize/controller/PrizeImageController.java` | Permission-protected multipart upload API |
| `db/migration/V3__add_prize_permissions.sql` | Prize permission seed and ADMIN grants |

---

### Task 1: Lock Down the Existing Prize Domain Skeleton

**Files:**

- Modify: `src/main/java/com/dongqh/luckyhub/prize/entity/MarketingPrize.java`
- Modify: `src/main/java/com/dongqh/luckyhub/prize/dto/CreatePrizeCommand.java`
- Modify: `src/main/java/com/dongqh/luckyhub/prize/dto/UpdatePrizeCommand.java`
- Modify: `src/main/java/com/dongqh/luckyhub/prize/vo/PrizeView.java`
- Modify: `src/main/java/com/dongqh/luckyhub/config/MybatisPlusConfig.java`
- Test: `src/test/java/com/dongqh/luckyhub/prize/PrizeDomainModelTests.java`

**Step 1: Write the failing model contract test**

Test Jakarta validation for blank names, required enum/stackable fields, the 100/500-character limits, and assert audit fields expose getters/setters. Assert enum persistence values remain `COUPON`, `POINTS`, `MEMBERSHIP`, `PHYSICAL` and `FIRST`, `SECOND`, `THIRD`, `CONSOLATION`.

**Step 2: Run the test to verify failure**

Run:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 '-Dtest=PrizeDomainModelTests' test
```

Expected: FAIL because the audit fields are not public through the model contract or mapper scanning is incomplete.

**Step 3: Apply the minimal domain corrections**

- Make `createdAt` and `updatedAt` private.
- Keep `@TableName("marketing_prize")`, `@TableId(type = IdType.AUTO)`, and audit fill annotations.
- Keep image URL optional but limited to 500 characters.
- Add `@Size(max = 500)` to descriptions and do not add a client-controlled status.
- Ensure both RBAC and prize mapper packages are scanned.

**Step 4: Run the focused test**

Expected: PASS.

**Step 5: Commit only these paths**

```powershell
git add src/main/java/com/dongqh/luckyhub/prize src/main/java/com/dongqh/luckyhub/config/MybatisPlusConfig.java src/test/java/com/dongqh/luckyhub/prize/PrizeDomainModelTests.java
git commit --only -m "feat: define prize domain contracts" -- src/main/java/com/dongqh/luckyhub/prize src/main/java/com/dongqh/luckyhub/config/MybatisPlusConfig.java src/test/java/com/dongqh/luckyhub/prize/PrizeDomainModelTests.java
```

### Task 2: Add Error and Pagination Contracts

**Files:**

- Create: `src/main/java/com/dongqh/luckyhub/prize/enums/PrizeErrorCode.java`
- Create: `src/main/java/com/dongqh/luckyhub/prize/dto/PrizeQuery.java`
- Create: `src/main/java/com/dongqh/luckyhub/common/result/PageResponse.java`
- Create: `src/test/java/com/dongqh/luckyhub/prize/PrizeContractTests.java`

**Step 1: Write the failing contract test**

Assert these exact error contracts:

```java
PRIZE_NOT_FOUND(41001, HttpStatus.NOT_FOUND)
IMAGE_EMPTY(41002, HttpStatus.BAD_REQUEST)
IMAGE_TYPE_UNSUPPORTED(41003, HttpStatus.BAD_REQUEST)
IMAGE_TOO_LARGE(41004, HttpStatus.PAYLOAD_TOO_LARGE)
OSS_UPLOAD_FAILED(51001, HttpStatus.BAD_GATEWAY)
OSS_CONFIG_UNAVAILABLE(51002, HttpStatus.SERVICE_UNAVAILABLE)
```

Validate page defaults `page=1`, `size=20`; enforce `page >= 1`, `1 <= size <= 100`; and expose `records`, `total`, `page`, `size`, `pages`.

**Step 2: Run the focused test and observe compilation failure**

**Step 3: Implement the contracts**

`PrizeQuery` is a class with defaults so Spring query binding works:

```java
private long page = 1;
private long size = 20;
private String name;
private PrizeType type;
private Integer status;
```

`PageResponse<T>` is a record with a factory accepting MyBatis-Plus `IPage<T>` and mapped records.

**Step 4: Run the focused test and commit**

Commit message: `feat: add prize API contracts`.

### Task 3: Implement Create and Detail Use Cases

**Files:**

- Create: `src/main/java/com/dongqh/luckyhub/prize/service/PrizeService.java`
- Create: `src/main/java/com/dongqh/luckyhub/prize/service/impl/PrizeServiceImpl.java`
- Create: `src/test/java/com/dongqh/luckyhub/prize/service/PrizeServiceTests.java`

**Step 1: Write failing Mockito tests**

- `create` maps every request field, forces `status=1`, inserts once, and returns a populated `PrizeView`.
- `getById` maps an existing entity.
- `getById` throws `BusinessException(PRIZE_NOT_FOUND)` for a missing row.

**Step 2: Run the focused test and verify failure**

**Step 3: Implement minimal code**

Use constructor injection. Keep mapping in private methods:

```java
public PrizeView create(CreatePrizeCommand command)
public PrizeView getById(long id)
private MarketingPrize requirePrize(long id)
private PrizeView toView(MarketingPrize prize)
```

Normalize optional strings by trimming them and converting blank image URLs/descriptions to `null`.

**Step 4: Run the focused test and commit**

Commit message: `feat: create and read prizes`.

### Task 4: Implement Update, Idempotent Disable, and Filtered Pagination

**Files:**

- Modify: `src/main/java/com/dongqh/luckyhub/prize/service/PrizeService.java`
- Modify: `src/main/java/com/dongqh/luckyhub/prize/service/impl/PrizeServiceImpl.java`
- Modify: `src/test/java/com/dongqh/luckyhub/prize/service/PrizeServiceTests.java`

**Step 1: Add failing tests**

- Update overwrites only editable fields and preserves ID/status/audit ownership.
- Missing update target returns `PRIZE_NOT_FOUND`.
- Disable changes `status` to `0`.
- Disabling an already disabled prize succeeds without a second database update.
- Page query adds `LIKE prize_name`, exact `prize_type`, and exact `status` conditions only when provided.
- Page query orders by `created_at DESC, id DESC`.

**Step 2: Run and verify red**

**Step 3: Implement minimal methods**

```java
PrizeView update(long id, UpdatePrizeCommand command);
void disable(long id);
PageResponse<PrizeView> page(PrizeQuery query);
```

Use `LambdaQueryWrapper<MarketingPrize>` and MyBatis-Plus `Page`.

**Step 4: Run and commit**

Commit message: `feat: update disable and query prizes`.

### Task 5: Seed and Enforce Prize Permissions

**Files:**

- Modify: `src/main/java/com/dongqh/luckyhub/rbac/constant/PermissionCodes.java`
- Create: `src/main/resources/db/migration/V3__add_prize_permissions.sql`
- Modify: `src/test/java/com/dongqh/luckyhub/infrastructure/DatabaseSchemaMigrationTests.java`

**Step 1: Add failing migration assertions**

Assert Flyway version `3` succeeded and all five permission codes exist and are granted to role `ADMIN`:

```text
prize:create
prize:read
prize:update
prize:disable
prize:image:upload
```

**Step 2: Run the migration test and verify failure**

**Step 3: Implement constants and an idempotent migration**

Insert permissions using unique permission codes, then insert missing ADMIN role mappings with `INSERT ... SELECT ... WHERE NOT EXISTS` semantics compatible with MySQL.

**Step 4: Run and commit**

Commit message: `feat: add prize management permissions`.

### Task 6: Expose the Prize Management HTTP API

**Files:**

- Create: `src/main/java/com/dongqh/luckyhub/prize/controller/PrizeController.java`
- Create: `src/test/java/com/dongqh/luckyhub/prize/controller/PrizeControllerTests.java`

**Step 1: Write failing standalone MockMvc tests**

Cover:

- `POST /api/admin/prizes` → `201`
- `GET /api/admin/prizes/{id}` → `200`
- `GET /api/admin/prizes` → `200` and query binding
- `PUT /api/admin/prizes/{id}` → `200`
- `PATCH /api/admin/prizes/{id}/disable` → `200`
- invalid request body → `400`, code `30000`
- annotation reflection verifies each handler uses the matching permission constant

**Step 2: Run and verify red**

**Step 3: Implement the controller**

Return `ApiResponse<T>` consistently, use `@Valid`, and annotate methods with `@RequirePermission`.

**Step 4: Run and commit**

Commit message: `feat: expose prize management API`.

### Task 7: Add OSS Runtime Configuration and Storage Adapter

**Files:**

- Modify: `pom.xml`
- Modify: `src/main/resources/application.yaml`
- Modify: `.env.example`
- Create: `src/main/java/com/dongqh/luckyhub/prize/config/OssProperties.java`
- Create: `src/main/java/com/dongqh/luckyhub/prize/config/OssConfiguration.java`
- Create: `src/main/java/com/dongqh/luckyhub/prize/storage/ObjectStorageGateway.java`
- Create: `src/main/java/com/dongqh/luckyhub/prize/storage/AliyunOssObjectStorageGateway.java`
- Create: `src/main/java/com/dongqh/luckyhub/prize/storage/UnavailableObjectStorageGateway.java`
- Create: `src/test/java/com/dongqh/luckyhub/prize/storage/OssConfigurationTests.java`

**Step 1: Write failing configuration tests**

- With `luckyhub.oss.enabled=false`, the context provides the unavailable gateway and starts without secrets.
- With enabled and complete properties, it provides the Alibaba adapter.
- Public base URL normalization removes a trailing slash.

**Step 2: Run and verify compilation failure**

**Step 3: Add SDK and configuration**

Add:

```xml
<aliyun-oss-v2.version>0.5.0</aliyun-oss-v2.version>
<dependency>
  <groupId>com.aliyun</groupId>
  <artifactId>alibabacloud-oss-v2</artifactId>
  <version>${aliyun-oss-v2.version}</version>
</dependency>
```

Bind:

```yaml
luckyhub:
  oss:
    enabled: ${OSS_ENABLED:false}
    region: ${OSS_REGION:}
    endpoint: ${OSS_ENDPOINT:}
    bucket: ${OSS_BUCKET:}
    access-key-id: ${OSS_ACCESS_KEY_ID:}
    access-key-secret: ${OSS_ACCESS_KEY_SECRET:}
    public-base-url: ${OSS_PUBLIC_BASE_URL:}
```

Create the SDK client with:

```java
OSSClient.newBuilder()
        .region(properties.region())
        .endpoint(properties.endpoint())
        .credentialsProvider(new StaticCredentialsProvider(
                properties.accessKeyId(),
                properties.accessKeySecret()
        ))
        .build();
```

Upload with:

```java
PutObjectRequest.newBuilder()
        .bucket(properties.bucket())
        .key(objectKey)
        .contentType(contentType)
        .body(BinaryData.fromBytes(content))
        .build();
```

Map SDK/runtime exceptions to `BusinessException(OSS_UPLOAD_FAILED)` without logging secrets.

**Step 4: Run and commit**

Commit message: `feat: configure Alibaba Cloud OSS storage`.

### Task 8: Validate and Upload Prize Images

**Files:**

- Create: `src/main/java/com/dongqh/luckyhub/prize/image/ValidatedImage.java`
- Create: `src/main/java/com/dongqh/luckyhub/prize/image/PrizeImageValidator.java`
- Create: `src/main/java/com/dongqh/luckyhub/prize/image/PrizeImageService.java`
- Create: `src/main/java/com/dongqh/luckyhub/prize/vo/ImageUploadView.java`
- Create: `src/test/java/com/dongqh/luckyhub/prize/image/PrizeImageValidatorTests.java`
- Create: `src/test/java/com/dongqh/luckyhub/prize/image/PrizeImageServiceTests.java`

**Step 1: Write failing validator and service tests**

- Empty file → `IMAGE_EMPTY`.
- File greater than 5 MiB → `IMAGE_TOO_LARGE`.
- Unsupported declared content type → `IMAGE_TYPE_UNSUPPORTED`.
- Declared JPEG with PNG magic bytes → `IMAGE_TYPE_UNSUPPORTED`.
- Valid JPEG, PNG, and WebP return normalized extensions and media types.
- Service generates the required date/UUID key, uploads exactly once, and returns `{publicBaseUrl}/{objectKey}`.
- Gateway failure preserves the configured business error.

**Step 2: Run and verify red**

**Step 3: Implement magic-byte validation**

Recognize:

- JPEG: `FF D8 FF`
- PNG: `89 50 4E 47 0D 0A 1A 0A`
- WebP: ASCII `RIFF` at bytes 0–3 and `WEBP` at bytes 8–11

Use an injectable `Clock` and UUID supplier in tests so generated keys are deterministic.

**Step 4: Run and commit**

Commit message: `feat: validate and upload prize images`.

### Task 9: Expose the Image Upload HTTP API

**Files:**

- Create: `src/main/java/com/dongqh/luckyhub/prize/controller/PrizeImageController.java`
- Create: `src/test/java/com/dongqh/luckyhub/prize/controller/PrizeImageControllerTests.java`

**Step 1: Write failing standalone MockMvc tests**

- Multipart field `file` at `POST /api/admin/prize-images` returns `201`.
- Response contains public `url` and `objectKey`.
- The method requires `prize:image:upload`.
- Empty and invalid uploads flow through the global exception envelope.

**Step 2: Run and verify red**

**Step 3: Implement the controller**

Use `@RequestPart("file") MultipartFile file`, `consumes = MediaType.MULTIPART_FORM_DATA_VALUE`, and return `ApiResponse<ImageUploadView>`.

**Step 4: Run and commit**

Commit message: `feat: expose prize image upload API`.

### Task 10: Regression, API Contract, and Operator Handoff

**Files:**

- Modify: `README.md`
- Create: `docs/prize-management-api.md`
- Optionally modify: `src/test/java/com/dongqh/luckyhub/LuckyhubApplicationTests.java`

**Step 1: Document the required user-supplied values**

Document exactly:

```properties
OSS_ENABLED=true
OSS_REGION=cn-hangzhou
OSS_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com
OSS_BUCKET=your-public-prize-image-bucket
OSS_ACCESS_KEY_ID=your-ram-access-key-id
OSS_ACCESS_KEY_SECRET=your-ram-access-key-secret
OSS_PUBLIC_BASE_URL=https://your-public-prize-image-bucket.oss-cn-hangzhou.aliyuncs.com
```

Explain that the bucket must permit public read, the RAM identity needs only object upload permission for the target bucket/prefix, and secrets belong in local `.env` or deployment environment variables.

**Step 2: Run the focused prize suite**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 '-Dtest=*Prize*,DatabaseSchemaMigrationTests' test
```

Expected: all prize and migration tests pass.

**Step 3: Run the full suite**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 test
```

Expected: build success and no regression.

**Step 4: Run package verification**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 '-DskipTests' package
```

Expected: executable JAR builds successfully.

**Step 5: Scan for secrets and placeholders**

```powershell
rg -n "TODO|FIXME|your-ram-access-key|AccessKeySecret|access-key-secret:" src pom.xml README.md docs .env.example
git diff --check
git status --short
```

Expected: only documentation/example placeholders appear; no credential value, TODO, whitespace error, or accidental unrelated modification is introduced.

**Step 6: Commit documentation**

Commit message: `docs: add prize management setup guide`.

**Step 7: Final review**

Compare every endpoint, response, permission, validation rule, error code, and non-goal against `docs/superpowers/specs/2026-07-27-prize-management-oss-design.md`. Report:

- files and behavior changed;
- exact tests and results;
- the six OSS values the user must fill;
- whether a live OSS upload was skipped because real credentials were unavailable.
