# LuckyHub 商品、统一奖励与渠道库存 API

> 对应阶段：迷你商城阶段 1——商品、统一奖励与渠道库存基础
>
> 更新时间：2026-08-08
>
> 基础地址示例：`http://localhost:8080`

## 1. 范围与当前边界

本文档覆盖阶段 1 新增的 11 个 HTTP 接口：

- 3 个商品目录接口；
- 2 个统一奖励定义接口；
- 6 个渠道库存接口。

阶段 1 只建立商品、奖励定义和渠道库存基础，不包含积分账户、兑换订单、优惠券、会员、支付、地址或物流。

新建的 `reward_definition` 目前尚未被现有抽奖流程使用。`marketing_prize.reward_definition_id` 只是可空兼容字段；当前抽奖仍使用原有 `marketing_prize`、`marketing_activity_prize` 和 `ActivityPrizeInventoryService`。统一奖励会在阶段 5 接入抽奖。

## 2. 认证、响应与调用准备

所有接口都需要 JWT。先准备 PowerShell 变量：

```powershell
$baseUrl = 'http://localhost:8080'
$token = '<登录接口返回的 accessToken>'
$headers = @{ Authorization = "Bearer $token" }
```

成功响应统一为：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

错误响应示例：

```json
{
  "code": 46002,
  "message": "可用库存不足",
  "data": null,
  "timestamp": 1786171621000,
  "requestId": "c755c7b4-68e5-4f20-b918-ae71ebee59a8"
}
```

通用错误：

| 错误码 | HTTP | 含义 |
| --- | --- | --- |
| `20004` | 401 | JWT 缺失、无效或过期 |
| `20001` | 403 | 已登录但缺少接口权限 |
| `30000` | 400 | 参数校验失败 |
| `30001` | 400 | JSON 格式错误或无法转换 |
| `10000` | 500 | 未分类的系统错误，响应不会暴露原始 SQL 或异常堆栈 |

## 3. 商品目录 API

商品类型：

```text
PHYSICAL
VIRTUAL
```

金额使用整数分，积分价格使用整数。商品创建时至少启用一种购买方式；启用现金购买必须提供 `cashPriceCent`，启用积分兑换必须提供 `pointsPrice`。

### 3.1 创建商品和默认 SKU

```text
POST /api/admin/products
权限：catalog:manage
成功：201 Created
```

请求：

```json
{
  "productCode": "MUG-001",
  "productName": "LuckyHub 陶瓷杯",
  "productType": "PHYSICAL",
  "imageUrl": "https://cdn.example.com/mug.png",
  "description": "抽奖或商城均可使用的示例商品",
  "skuCode": "MUG-001-WHITE",
  "skuName": "白色款",
  "cashPriceCent": 2990,
  "pointsPrice": 3000,
  "cashEnabled": true,
  "pointsEnabled": true
}
```

响应 `data`：

```json
{
  "id": 7,
  "productCode": "MUG-001",
  "productName": "LuckyHub 陶瓷杯",
  "productType": "PHYSICAL",
  "imageUrl": "https://cdn.example.com/mug.png",
  "description": "抽奖或商城均可使用的示例商品",
  "status": 1,
  "skus": [
    {
      "id": 8,
      "productId": 7,
      "skuCode": "MUG-001-WHITE",
      "skuName": "白色款",
      "cashPriceCent": 2990,
      "pointsPrice": 3000,
      "cashEnabled": true,
      "pointsEnabled": true,
      "status": 1,
      "version": 0,
      "createdAt": "2026-08-08T15:00:00",
      "updatedAt": "2026-08-08T15:00:00"
    }
  ],
  "createdAt": "2026-08-08T15:00:00",
  "updatedAt": "2026-08-08T15:00:00"
}
```

接口特有错误：`44002` 商品编码重复、`44003` SKU 编码重复。非法购买方式组合会在请求反序列化或领域命令校验阶段被拒绝；`44004` 是商品配置错误的预留稳定码。

PowerShell：

```powershell
$body = @{
  productCode = 'MUG-001'; productName = 'LuckyHub 陶瓷杯'; productType = 'PHYSICAL'
  imageUrl = 'https://cdn.example.com/mug.png'; description = '示例商品'
  skuCode = 'MUG-001-WHITE'; skuName = '白色款'
  cashPriceCent = 2990; pointsPrice = 3000
  cashEnabled = $true; pointsEnabled = $true
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "$baseUrl/api/admin/products" `
  -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $body
```

### 3.2 分页查询上架商品

```text
GET /api/products
权限：catalog:read
成功：200 OK
```

查询参数：`page` 默认 1、`size` 默认 20 且最大 100、`name` 为名称模糊查询、`type` 为商品类型。该用户接口始终只返回状态为 1 的商品和 SKU，不提供下架商品管理查询。

响应 `data`：

```json
{
  "records": [
    {
      "id": 7,
      "productCode": "MUG-001",
      "productName": "LuckyHub 陶瓷杯",
      "productType": "PHYSICAL",
      "status": 1,
      "skus": [
        {
          "id": 8,
          "productId": 7,
          "skuCode": "MUG-001-WHITE",
          "skuName": "白色款",
          "cashPriceCent": 2990,
          "pointsPrice": 3000,
          "cashEnabled": true,
          "pointsEnabled": true,
          "status": 1,
          "version": 0
        }
      ]
    }
  ],
  "total": 1,
  "page": 1,
  "size": 20,
  "pages": 1
}
```

错误：通用认证、权限和参数错误。商品响应不包含 `totalStock`、`availableStock`、`reservedStock` 等库存字段。

PowerShell：

```powershell
Invoke-RestMethod -Method Get `
  -Uri "$baseUrl/api/products?page=1&size=20&name=陶瓷杯&type=PHYSICAL" `
  -Headers $headers
```

### 3.3 查询商品详情

```text
GET /api/products/{id}
权限：catalog:read
成功：200 OK
```

请求参数：路径 `id` 必须为正数。

响应：与创建商品接口的 `ProductView` 相同，只包含当前启用的 SKU，仍不暴露库存。

接口特有错误：`44001` 商品不存在或商品已下架。

PowerShell：

```powershell
Invoke-RestMethod -Method Get -Uri "$baseUrl/api/products/7" -Headers $headers
```

## 4. 统一奖励定义 API

奖励类型：

```text
PRODUCT
COUPON
POINTS
MEMBERSHIP
DRAW_CHANCE
```

目标规则：`PRODUCT`、`COUPON`、`MEMBERSHIP` 必须有 `targetId`；`POINTS`、`DRAW_CHANCE` 不允许有 `targetId`。阶段 1 只会验证 `PRODUCT.targetId` 指向已启用 SKU；券和会员目标要等后续阶段创建相应领域后再做真实关联。

### 4.1 创建奖励定义

```text
POST /api/admin/reward-definitions
权限：reward:manage
成功：201 Created
```

请求示例——500 积分：

```json
{
  "rewardCode": "POINTS-500",
  "rewardName": "500 积分",
  "rewardType": "POINTS",
  "quantity": 500,
  "configSnapshot": "{\"source\":\"lottery\"}"
}
```

响应 `data`：

```json
{
  "id": 9,
  "rewardCode": "POINTS-500",
  "rewardName": "500 积分",
  "rewardType": "POINTS",
  "targetId": null,
  "quantity": 500,
  "configSnapshot": "{\"source\":\"lottery\"}",
  "status": 1,
  "createdAt": "2026-08-08T15:00:00",
  "updatedAt": "2026-08-08T15:00:00"
}
```

接口特有错误：`45002` 奖励编码重复、`45003` 奖励目标不合法、`45004` `configSnapshot` 不是合法 JSON。

PowerShell：

```powershell
$body = @{
  rewardCode = 'POINTS-500'; rewardName = '500 积分'; rewardType = 'POINTS'
  quantity = 500; configSnapshot = '{"source":"lottery"}'
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "$baseUrl/api/admin/reward-definitions" `
  -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $body
```

### 4.2 查询奖励定义

```text
GET /api/admin/reward-definitions/{id}
权限：reward:manage
成功：200 OK
```

请求参数：路径 `id` 必须为正数。响应为上面的 `RewardDefinitionView`。

接口特有错误：`45001` 奖励定义不存在。

PowerShell：

```powershell
Invoke-RestMethod -Method Get `
  -Uri "$baseUrl/api/admin/reward-definitions/9" -Headers $headers
```

## 5. 渠道库存 API

阶段 1 的渠道编码约定必须保持为：

```text
MALL
POINTS
LOTTERY:{activityId}
```

- `MALL`：现金商城库存；
- `POINTS`：积分兑换库存；
- `LOTTERY:{activityId}`：指定抽奖活动库存，例如 `LOTTERY:1001`。

阶段 1 只配置和使用 `MALL`、`POINTS`。`LOTTERY:{activityId}` 仅保留命名约定，将在阶段 5 抽奖奖励迁移时接入。不同渠道库存不会自动互借。

渠道编码进入服务后会去掉首尾空格并转成大写。所有库存写操作都依赖稳定业务号或预占号实现幂等。

### 5.1 初始化 SKU 总库存

```text
POST /api/admin/inventory/skus/initialize
权限：inventory:manage
成功：201 Created
```

请求：

```json
{"skuId": 8, "totalStock": 100, "businessNo": "INIT-SKU-8"}
```

响应 `data`：

```json
{
  "skuId": 8,
  "channelCode": null,
  "totalStock": 100,
  "allocatedStock": 0,
  "availableStock": null,
  "reservedStock": null,
  "consumedStock": null,
  "reservationNo": null,
  "reservationStatus": null
}
```

特有错误：`46003` 重复初始化状态冲突、`46004` 相同业务号参数冲突、`46005` SKU 不存在或已禁用。

PowerShell：

```powershell
$body = @{ skuId = 8; totalStock = 100; businessNo = 'INIT-SKU-8' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$baseUrl/api/admin/inventory/skus/initialize" `
  -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $body
```

### 5.2 分配渠道库存

```text
POST /api/admin/inventory/channels/allocate
权限：inventory:manage
成功：200 OK
```

请求：

```json
{"skuId": 8, "channelCode": "MALL", "quantity": 30, "businessNo": "ALLOC-SKU-8-MALL-1"}
```

响应 `data`：

```json
{
  "skuId": 8,
  "channelCode": "MALL",
  "totalStock": 100,
  "allocatedStock": 30,
  "availableStock": 30,
  "reservedStock": 0,
  "consumedStock": 0,
  "reservationNo": null,
  "reservationStatus": null
}
```

特有错误：`46001` SKU 总库存未初始化、`46002` 未分配总库存不足、`46004` 相同业务号参数冲突。

PowerShell：

```powershell
$body = @{
  skuId = 8; channelCode = 'MALL'; quantity = 30; businessNo = 'ALLOC-SKU-8-MALL-1'
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$baseUrl/api/admin/inventory/channels/allocate" `
  -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $body
```

### 5.3 预占渠道库存

```text
POST /api/admin/inventory/reservations
权限：inventory:manage
成功：201 Created
```

请求：

```json
{"skuId": 8, "channelCode": "MALL", "quantity": 1, "reservationNo": "ORDER-1001"}
```

响应 `data`：

```json
{
  "skuId": 8,
  "channelCode": "MALL",
  "totalStock": 100,
  "allocatedStock": 30,
  "availableStock": 29,
  "reservedStock": 1,
  "consumedStock": 0,
  "reservationNo": "ORDER-1001",
  "reservationStatus": "RESERVED"
}
```

特有错误：`46001` 渠道库存不存在、`46002` 可用库存不足、`46004` 相同预占号对应了不同 SKU、渠道或数量。

PowerShell：

```powershell
$body = @{ skuId = 8; channelCode = 'MALL'; quantity = 1; reservationNo = 'ORDER-1001' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$baseUrl/api/admin/inventory/reservations" `
  -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $body
```

### 5.4 确认库存消耗

```text
POST /api/admin/inventory/reservations/{reservationNo}/confirm
权限：inventory:manage
成功：200 OK
```

请求体：无。响应为 `ChannelInventoryView`，`reservationStatus` 为 `CONFIRMED`，数量从 `reservedStock` 转入 `consumedStock`。

特有错误：`46001` 预占不存在、`46003` 已释放后又确认等状态冲突。

PowerShell：

```powershell
Invoke-RestMethod -Method Post `
  -Uri "$baseUrl/api/admin/inventory/reservations/ORDER-1001/confirm" -Headers $headers
```

### 5.5 释放预占库存

```text
POST /api/admin/inventory/reservations/{reservationNo}/release
权限：inventory:manage
成功：200 OK
```

请求体：无。响应为 `ChannelInventoryView`，`reservationStatus` 为 `RELEASED`，数量从 `reservedStock` 退回 `availableStock`。

特有错误：`46001` 预占不存在、`46003` 已确认后又释放等状态冲突。

PowerShell：

```powershell
Invoke-RestMethod -Method Post `
  -Uri "$baseUrl/api/admin/inventory/reservations/ORDER-1001/release" -Headers $headers
```

同一条预占只能走确认或释放其中一条终态路径。重复调用同一个终态操作会直接返回当前结果，不会重复增减库存。

### 5.6 查询渠道库存

```text
GET /api/admin/inventory/skus/{skuId}/channels/{channelCode}
权限：inventory:manage
成功：200 OK
```

路径限制：`skuId` 必须为正数，URL 解码后的 `channelCode` 最长 100 个字符。

响应：与渠道分配接口相同，不包含预占身份和状态。

特有错误：`46001` SKU 总库存或指定渠道不存在。

PowerShell：

```powershell
Invoke-RestMethod -Method Get `
  -Uri "$baseUrl/api/admin/inventory/skus/8/channels/MALL" -Headers $headers
```

## 6. 完整业务错误码

### 商品

| 错误码 | HTTP | 含义 |
| --- | --- | --- |
| `44001` | 404 | 商品不存在或不可见 |
| `44002` | 409 | 商品编码已存在 |
| `44003` | 409 | SKU 编码已存在 |
| `44004` | 400 | 商品配置不合法；预留稳定码 |

### 奖励定义

| 错误码 | HTTP | 含义 |
| --- | --- | --- |
| `45001` | 404 | 奖励定义不存在 |
| `45002` | 409 | 奖励编码已存在 |
| `45003` | 400 | 奖励类型与目标不匹配，或商品 SKU 不可用 |
| `45004` | 400 | 配置快照不是合法 JSON |

### 渠道库存

| 错误码 | HTTP | 含义 |
| --- | --- | --- |
| `46001` | 404 | 总库存、渠道库存或预占记录不存在 |
| `46002` | 409 | 可用总库存或渠道库存不足 |
| `46003` | 409 | 初始化或预占状态冲突 |
| `46004` | 409 | 相同业务号或预占号使用了不同参数 |
| `46005` | 400 | SKU 不存在或已禁用 |

## 7. V8/V9 数据模型

### V8：商品与统一奖励基础

| 表或变更 | 用途 | 核心约束 |
| --- | --- | --- |
| `product` | 商品 SPU | 商品编码唯一；类型为 `PHYSICAL/VIRTUAL`；状态为 0/1 |
| `product_sku` | 可定价和配置库存的 SKU | SKU 编码唯一；现金/积分启用时必须有对应价格 |
| `reward_definition` | 商品、券、积分、会员、抽奖资格的统一奖励描述 | 奖励编码唯一；数量大于 0；配置快照为 JSON |
| `marketing_prize.reward_definition_id` | 未来把旧抽奖奖品关联到统一奖励 | 可空；阶段 1 不改变旧抽奖行为 |
| RBAC 种子 | 新增 4 个权限并分配角色 | USER/ADMIN 有 `catalog:read`；ADMIN 有 3 个管理权限 |

V8 不修改 V1–V7，只新增结构和兼容字段。

### V9：渠道库存

| 表 | 用途 | 核心约束 |
| --- | --- | --- |
| `sku_inventory` | SKU 总库存与已分配总量 | 一个 SKU 一行；`allocated_stock <= total_stock` |
| `inventory_channel_stock` | 各渠道库存余额 | SKU+渠道唯一；分配量恒等于可用+预占+已消费 |
| `inventory_reservation` | 订单或奖励的库存预占 | 预占号唯一；状态只有 RESERVED/CONFIRMED/RELEASED |
| `inventory_ledger` | 不可变幂等流水 | 业务号唯一；记录 SKU、渠道、操作和数量 |

库存变化使用数据库事务和条件更新。即使多个请求并发执行，分配不能超过总库存，预占不能让可用库存变成负数。

## 8. 权限分配

| 权限 | 默认角色 | 用途 |
| --- | --- | --- |
| `catalog:read` | USER、ADMIN | 查询上架商品 |
| `catalog:manage` | ADMIN | 创建商品和默认 SKU |
| `reward:manage` | ADMIN | 创建、查询奖励定义 |
| `inventory:manage` | ADMIN | 初始化、分配和变更渠道库存 |

## 9. 推荐调用顺序

```text
创建商品和 SKU
  -> 初始化 SKU 总库存
  -> 分配 MALL / POINTS 渠道库存
  -> 创建商品型或其他类型奖励定义
  -> 后续订单预占库存
  -> 业务成功确认 / 业务失败释放
```

例如一个商品既可 29.90 元购买，也可 3000 积分兑换，可以为同一 SKU 分别分配 `MALL=60`、`POINTS=40`。商城订单只使用 `MALL`，积分兑换单只使用 `POINTS`，两边不会因为另一渠道有剩余库存而自动借用。

## 10. 后续阶段

阶段 2 将基于当前 SKU、`pointsPrice` 和 `POINTS` 渠道库存实现积分账户、不可变积分流水与积分兑换单。阶段 2 不在本文档范围内，也不应直接修改阶段 1 已建立的库存幂等规则。
