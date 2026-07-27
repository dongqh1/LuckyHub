# 奖品管理与阿里云 OSS 图片上传设计

## 1. 目标

在 LuckyHub 现有认证、RBAC、MyBatis-Plus、MySQL 和统一响应基础上，实现：

- 创建奖品；
- 查询奖品详情；
- 按条件分页查询奖品；
- 修改奖品；
- 以禁用代替物理删除；
- 将公开奖品图片通过后端上传至阿里云 OSS；
- 将 OSS 公开 URL 保存到 `marketing_prize.image_url`；
- 使用细粒度 RBAC 权限保护全部后台奖品接口。

本阶段不实现奖品重新启用、物理删除、OSS 旧图片自动清理、前端直传、STS、CDN 和私有 Bucket 签名 URL。

## 2. 已确认的业务决策

### 2.1 奖品删除语义

- `DELETE` 不作为本阶段接口。
- 使用 `PATCH /api/admin/prizes/{prizeId}/disable` 将 `status` 改为 `0`。
- 重复禁用为幂等成功。
- 禁用不删除奖品记录，不删除 OSS 图片，不影响历史抽奖记录和用户权益。
- 后续活动模块不得把禁用奖品加入新活动。

### 2.2 图片上传方式

- 客户端先调用独立图片上传接口。
- LuckyHub 后端接收 `MultipartFile` 并上传阿里云 OSS。
- 上传成功后返回公开 URL。
- 客户端再把公开 URL 放入创建或修改奖品的 JSON 请求。
- 奖品 CRUD 不接收 Multipart 请求，保持 JSON 接口。

### 2.3 OSS 访问方式

- 使用专门存放公开奖品图片的 Bucket。
- Bucket 中不得保存身份证、合同、AccessKey 等私密内容。
- 数据库保存稳定公开 URL，不保存临时签名 URL。
- 后端使用最小权限 RAM 用户，凭据只从运行环境读取。
- AccessKey 不写入 Java、YAML 默认值、测试代码、日志或 Git。

## 3. 数据模型

复用 Flyway V1 已创建的 `marketing_prize`，不修改 V1：

| 字段 | Java 类型 | 规则 |
|---|---|---|
| `id` | `Long` | 自增主键 |
| `prize_name` | `String` | 必填，最长 100 |
| `prize_type` | `PrizeType` | 必填，以枚举名称保存 |
| `prize_level` | `PrizeLevel` | 必填，以枚举名称保存 |
| `image_url` | `String` | 可空，最长 500，保存 OSS 公开 URL |
| `description` | `String` | 可空，最长 500 |
| `stackable` | `Boolean` | 必填 |
| `status` | `Integer` | `1` 启用，`0` 禁用 |
| `created_at` | `LocalDateTime` | 创建时自动填写 |
| `updated_at` | `LocalDateTime` | 创建和修改时自动填写 |

`PrizeType`：

- `COUPON`
- `POINTS`
- `MEMBERSHIP`
- `PHYSICAL`

`PrizeLevel`：

- `FIRST`
- `SECOND`
- `THIRD`
- `CONSOLATION`

创建和修改 DTO 不允许客户端传入 `id`、`status`、`createdAt` 或 `updatedAt`。

## 4. 模块边界

```text
prize/
├─ controller/
│  ├─ PrizeController.java
│  └─ PrizeImageController.java
├─ dto/
│  ├─ CreatePrizeCommand.java
│  ├─ UpdatePrizeCommand.java
│  └─ PrizeQuery.java
├─ entity/
│  └─ MarketingPrize.java
├─ enums/
│  ├─ PrizeErrorCode.java
│  ├─ PrizeLevel.java
│  └─ PrizeType.java
├─ mapper/
│  └─ MarketingPrizeMapper.java
├─ service/
│  ├─ PrizeImageService.java
│  ├─ PrizeService.java
│  └─ impl/
│     ├─ OssPrizeImageService.java
│     └─ PrizeServiceImpl.java
├─ storage/
│  ├─ ObjectStorageGateway.java
│  └─ AliyunOssObjectStorageGateway.java
├─ validation/
│  └─ PrizeImageValidator.java
└─ vo/
   ├─ ImageUploadView.java
   └─ PrizeView.java
```

公共分页结构放在：

```text
common/result/PageResponse.java
```

OSS Spring 配置放在：

```text
config/OssConfig.java
config/OssProperties.java
```

`ObjectStorageGateway` 隔离阿里云 SDK，使奖品图片服务测试不依赖真实 OSS。

## 5. API 契约

### 5.1 上传奖品图片

```http
POST /api/admin/prize-images
Content-Type: multipart/form-data
Authorization: Bearer <token>
```

表单字段：

```text
file
```

成功返回 HTTP 201：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "url": "https://example-bucket.oss-cn-hangzhou.aliyuncs.com/prizes/2026/07/uuid.png",
    "objectKey": "prizes/2026/07/uuid.png"
  }
}
```

### 5.2 创建奖品

```http
POST /api/admin/prizes
Content-Type: application/json
```

成功返回 HTTP 201 和 `PrizeView`。服务端强制设置 `status = 1`。

### 5.3 查询奖品详情

```http
GET /api/admin/prizes/{prizeId}
```

奖品不存在返回 HTTP 404。

### 5.4 分页查询奖品

```http
GET /api/admin/prizes?page=1&size=20&name=&type=&status=
```

- `page` 默认 1，最小 1；
- `size` 默认 20，范围 1 到 100；
- `name` 可选，按奖品名称模糊匹配；
- `type` 可选，按 `PrizeType` 精确匹配；
- `status` 可选，只允许 0 或 1；
- 默认按 `id` 倒序。

返回 `PageResponse<PrizeView>`：

```json
{
  "records": [],
  "total": 0,
  "page": 1,
  "size": 20,
  "pages": 0
}
```

### 5.5 修改奖品

```http
PUT /api/admin/prizes/{prizeId}
Content-Type: application/json
```

- 完整替换可编辑字段；
- 不修改 `status`；
- 奖品不存在返回 HTTP 404；
- 已禁用奖品仍可修改基础资料，但不会自动重新启用。

### 5.6 禁用奖品

```http
PATCH /api/admin/prizes/{prizeId}/disable
```

- 奖品不存在返回 HTTP 404；
- 当前状态为 1 时更新为 0；
- 当前状态已为 0 时直接返回当前 `PrizeView`；
- 不删除数据库记录和 OSS 图片。

## 6. 权限模型

Flyway V3 幂等新增并授予 `ADMIN`：

| 权限编码 | 含义 |
|---|---|
| `prize:create` | 创建奖品 |
| `prize:read` | 查询奖品 |
| `prize:update` | 修改奖品 |
| `prize:disable` | 禁用奖品 |
| `prize:image:upload` | 上传奖品图片 |

V3 必须：

- 通过 `permission_code` 和 `role_code` 查询真实主键；
- 不写死 ID；
- 不覆盖已有权限名称；
- 只补充缺失的 ADMIN 权限关系；
- 不修改 V1 或 V2；
- 不创建物理外键。

## 7. OSS 设计

### 7.1 SDK

使用：

```xml
<groupId>com.aliyun</groupId>
<artifactId>alibabacloud-oss-v2</artifactId>
<version>0.5.0</version>
```

Java SDK V2 支持 Java 8 及以上，LuckyHub 使用 Java 17。

官方文档：

- https://help.aliyun.com/zh/oss/developer-reference/oss-sdk-for-java-2-0/

### 7.2 配置

`.env.example` 只提供占位值：

```properties
OSS_ACCESS_KEY_ID=replace-with-ram-access-key-id
OSS_ACCESS_KEY_SECRET=replace-with-ram-access-key-secret
OSS_REGION=cn-hangzhou
OSS_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com
OSS_BUCKET=replace-with-public-prize-image-bucket
OSS_PUBLIC_BASE_URL=https://replace-with-public-prize-image-bucket.oss-cn-hangzhou.aliyuncs.com
```

真实值写入已被 `.gitignore` 忽略的 `.env` 或部署平台环境变量。

### 7.3 Object Key

服务端生成：

```text
prizes/{yyyy}/{MM}/{uuid}.{extension}
```

不使用客户端原始文件名，避免路径穿越、重名和特殊字符问题。

### 7.4 图片校验

- 文件不能为空；
- 最大 5 MiB；
- 允许 JPEG、PNG、WebP；
- 根据文件签名识别真实类型，不只信任请求 `Content-Type`；
- JPEG 签名以 `FF D8 FF` 开头；
- PNG 使用标准 8 字节签名；
- WebP 必须同时包含 `RIFF` 和 `WEBP` 标记；
- 由识别结果决定 `.jpg`、`.png` 或 `.webp` 扩展名；
- 上传流必须可以安全重复读取或在校验后使用同一份字节数据上传。

### 7.5 URL

上传成功后：

```text
publicBaseUrl + "/" + objectKey
```

配置加载时去除 `publicBaseUrl` 末尾斜杠，保证 URL 中不出现重复斜杠。

### 7.6 一致性边界

- 图片上传和 MySQL 创建奖品是两个独立请求，不做分布式事务。
- 上传成功但用户未创建奖品时可能产生孤儿图片。
- 本阶段接受该边界，不自动删除旧图或孤儿图。
- 后续可通过对象元数据、上传记录表或定时任务清理孤儿图片。

## 8. 错误处理

`PrizeErrorCode`：

| 业务码 | HTTP | 含义 |
|---:|---:|---|
| 41001 | 404 | 奖品不存在 |
| 41002 | 400 | 图片文件为空 |
| 41003 | 400 | 图片类型不支持 |
| 41004 | 400 | 图片超过 5 MiB |
| 51001 | 502 | OSS 上传失败 |
| 51002 | 503 | OSS 配置不可用 |

- 参数校验错误继续交给 `GlobalExceptionHandler`；
- 阿里云异常不得把 AccessKey、签名、内部请求详情返回客户端；
- 服务端日志记录 requestId、objectKey 和阿里云请求 ID；
- 日志不得记录 AccessKey Secret、完整 JWT 或上传文件内容。

## 9. 数据流

### 9.1 图片上传

```text
AuthenticationFilter
→ PermissionInterceptor(prize:image:upload)
→ PrizeImageController
→ PrizeImageValidator
→ PrizeImageService
→ ObjectStorageGateway
→ Alibaba Cloud OSS
→ ImageUploadView
```

### 9.2 创建奖品

```text
AuthenticationFilter
→ PermissionInterceptor(prize:create)
→ Bean Validation
→ PrizeController
→ PrizeService
→ MarketingPrizeMapper
→ MySQL
→ PrizeView
```

查询、修改和禁用使用同一分层，不允许 Controller 直接操作 Mapper。

## 10. 测试策略

### 10.1 DTO 和图片校验

- 合法创建、修改命令通过；
- 空名称、超长名称、空类型、空等级和空 stackable 被拒绝；
- 空文件被拒绝；
- 超过 5 MiB 被拒绝；
- JPEG、PNG、WebP 文件签名被正确识别；
- 伪造 Content-Type 的非图片被拒绝。

### 10.2 Mapper 集成测试

- 插入后生成 ID；
- 枚举按名称写入并读取；
- 自动填写创建和更新时间；
- 分页和筛选条件正确；
- 测试使用事务回滚。

### 10.3 Service 测试

- 创建强制启用；
- 详情不存在返回 404；
- 修改保留原状态；
- 禁用把状态改为 0；
- 重复禁用幂等；
- 查询条件正确映射为分页结果。

### 10.4 OSS 测试

- 单元测试使用假的 `ObjectStorageGateway`，不访问公网；
- 验证 Object Key 格式和公开 URL 拼接；
- 验证上传异常转换为统一业务异常；
- 真实 OSS 只做需要显式凭据的手工冒烟测试，不纳入默认 Maven 测试。

### 10.5 Controller 和权限测试

- 创建返回 201；
- 查询、修改和禁用返回统一响应；
- 参数错误返回 400；
- 无 Token 返回 401；
- 有 Token 无权限返回 403；
- ADMIN 拥有五项奖品权限；
- OpenAPI 包含全部奖品接口。

### 10.6 完成标准

- 全量 Maven 测试通过；
- Flyway V3 成功应用；
- `.env`、AccessKey 和真实 Bucket 信息未进入 Git；
- 手工上传真实图片后能通过公开 URL 访问；
- 创建奖品后数据库保存相同公开 URL；
- 查询、修改、禁用接口行为符合本设计。

## 11. 用户需要准备的信息

实施完成后，用户需要提供或填写：

- OSS Bucket 所在地域，例如 `cn-hangzhou`；
- Bucket 名称；
- OSS Endpoint；
- 公开访问基础 URL；
- 最小权限 RAM 用户的 AccessKey ID；
- 最小权限 RAM 用户的 AccessKey Secret；
- 在阿里云控制台确认该专用 Bucket 中的奖品图片允许公开读取。

代码和文档不得收集阿里云主账号 AccessKey。
