# 阶段 1 · 任务 7：渠道库存管理 API 完成介绍

## 1. 这一步解决了什么问题

任务 6 已经实现了安全的渠道库存服务，但当时只能由 Java 代码在系统内部调用。本任务增加了受保护的 HTTP API，使管理后台、测试工具以及后续订单系统能够通过统一接口操作库存。

本次开放的能力包括：

- 初始化 SKU 总库存；
- 给商城、积分商城等渠道分配库存；
- 为订单或奖励预占库存；
- 业务成功后确认消耗；
- 业务取消后释放库存；
- 查询指定 SKU 的渠道库存。

所有接口都需要 `inventory:manage` 权限，普通用户不能直接修改库存。

## 2. 已开放的六个接口

| 操作 | 请求方法与路径 | 成功状态码 |
| --- | --- | --- |
| 初始化 SKU 库存 | `POST /api/admin/inventory/skus/initialize` | `201` |
| 分配渠道库存 | `POST /api/admin/inventory/channels/allocate` | `200` |
| 预占渠道库存 | `POST /api/admin/inventory/reservations` | `201` |
| 确认库存消耗 | `POST /api/admin/inventory/reservations/{reservationNo}/confirm` | `200` |
| 释放预占库存 | `POST /api/admin/inventory/reservations/{reservationNo}/release` | `200` |
| 查询渠道库存 | `GET /api/admin/inventory/skus/{skuId}/channels/{channelCode}` | `200` |

接口返回项目统一的响应结构：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

错误响应会额外带上 `timestamp` 和 `requestId`，便于定位一次失败请求。

## 3. 完整使用例子

假设商品 SKU `31` 有 100 件库存，计划先给商城渠道分配 30 件。

### 3.1 初始化总库存

请求：

```http
POST /api/admin/inventory/skus/initialize
Authorization: Bearer <token>
Content-Type: application/json

{
  "skuId": 31,
  "totalStock": 100,
  "businessNo": "INIT-31"
}
```

`businessNo` 是本次初始化的唯一业务号。网络超时后使用相同内容重试，不会再次初始化。

### 3.2 给商城分配库存

```http
POST /api/admin/inventory/channels/allocate
Authorization: Bearer <token>
Content-Type: application/json

{
  "skuId": 31,
  "channelCode": "MALL",
  "quantity": 30,
  "businessNo": "ALLOC-31-MALL"
}
```

结果可理解为：

```text
SKU 总库存：100
已分配总量：30
MALL 可用库存：30
```

### 3.3 用户下单时预占一件

```http
POST /api/admin/inventory/reservations
Authorization: Bearer <token>
Content-Type: application/json

{
  "skuId": 31,
  "channelCode": "MALL",
  "quantity": 1,
  "reservationNo": "ORDER-1001"
}
```

预占成功后：

```text
availableStock：29
reservedStock：1
reservationStatus：RESERVED
```

订单系统即使因为网络问题重复提交 `ORDER-1001`，也只会预占一次。

### 3.4 支付成功后确认消耗

```http
POST /api/admin/inventory/reservations/ORDER-1001/confirm
Authorization: Bearer <token>
```

确认后预占状态变成 `CONFIRMED`，对应数量从预占库存转入已消费库存。

### 3.5 订单取消时释放库存

如果订单还处于 `RESERVED`，可以调用：

```http
POST /api/admin/inventory/reservations/ORDER-1001/release
Authorization: Bearer <token>
```

释放后状态变成 `RELEASED`，原来预占的一件回到可用库存。已经确认消耗的预占不能再释放。

### 3.6 查询当前库存

```http
GET /api/admin/inventory/skus/31/channels/MALL
Authorization: Bearer <token>
```

响应中的关键字段包括：

```text
totalStock       SKU 总库存
allocatedStock   当前渠道分配量
availableStock   可以继续售卖或兑换的数量
reservedStock    已下单但尚未最终确认的数量
consumedStock    已经正式消耗的数量
```

## 4. 权限和登录保护

控制器统一要求：

```text
inventory:manage
```

调用结果：

| 调用者情况 | HTTP 状态 |
| --- | --- |
| 没有登录凭证 | `401 Unauthorized` |
| 已登录但没有库存管理权限 | `403 Forbidden` |
| 已登录并拥有 `inventory:manage` | 进入参数校验和库存服务 |

六个接口都通过真实的认证过滤器和权限拦截器进行了测试，不是只在文档中声明权限。

## 5. 接口参数保护

控制器和请求对象会拒绝明显无效的数据：

- SKU ID 必须是正数；
- 数量必须是正数；
- 渠道编码最长 100 个字符；
- 预占单号最长 64 个字符；
- 业务号不能为空，最长 100 个字符；
- 请求 JSON 缺少必填字段时返回统一参数错误。

例如请求 `skuId = 0`，会返回 HTTP `400` 和统一错误码 `30000`，请求不会进入库存运算。

## 6. 业务错误保持稳定

HTTP 层不会把库存错误改成模糊的系统异常。现有库存错误码会原样进入统一错误响应：

| 错误码 | HTTP 状态 | 含义 |
| --- | --- | --- |
| `46001` | `404` | 库存、渠道或预占记录不存在 |
| `46002` | `409` | 可用库存不足 |
| `46003` | `409` | 库存状态冲突 |
| `46004` | `409` | 相同幂等号的参数不一致 |
| `46005` | `400` | SKU 不存在或不可用于库存配置 |

例如商城仅剩 2 件库存，但订单请求预占 3 件，接口返回 `409` 和错误码 `46002`，调用方可以明确提示“库存不足”，而不是误报服务器故障。

## 7. 控制器与库存服务的职责边界

控制器只负责：

1. 接收 HTTP 请求；
2. 校验请求格式和路径参数；
3. 检查 `inventory:manage` 权限；
4. 调用 `ChannelInventoryService`；
5. 包装统一响应。

库存数量的判断、扣减、幂等、事务和并发安全仍全部由任务 6 的服务层负责。这样未来订单系统直接调用服务，或者改成消息消费方式时，不会复制一套库存算法。

## 8. 当前边界和下一步

这些接口当前属于管理员和系统集成接口，还没有商城前端页面，也没有自动接入支付订单。

现有抽奖主链路仍使用原来的活动奖品库存。本阶段预留的 `LOTTERY:{activityId}` 渠道要等后续统一抽奖履约阶段再接入，不能仅凭本任务就认为抽奖已经切换到新库存。

下一项任务 8 会整理阶段 1 的完整 API 文档、README、开发交接说明，并运行阶段 1 聚焦测试和全量回归测试。

## 9. 验证结果

本任务采用测试先行：

1. 在控制器不存在时运行测试，4 个测试因端点返回 `404` 而失败；
2. 实现最小控制器；
3. 再次运行接口测试，4 个测试全部通过；
4. 联合现有安全链测试进行计划级验证。

计划指定的最终验证命令：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 `
  '-Dtest=ChannelInventoryControllerTests,LotterySecurityChainIntegrationTests' test
```

最近一次结果：共 5 个测试，0 失败，0 错误。
