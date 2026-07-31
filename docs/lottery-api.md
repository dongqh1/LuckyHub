# LuckyHub 抽奖核心 API 使用指南

本文档描述当前代码中已经实现的 7 个抽奖与权益接口。接口统一返回 JSON，成功结构为：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

失败结构为：

```json
{
  "code": 43004,
  "message": "每日抽奖额度不足",
  "data": null,
  "timestamp": 1785513600000,
  "requestId": "服务端请求追踪ID"
}
```

## 1. 准备工作

默认地址为 `http://localhost:8080`，Swagger UI 为
`http://localhost:8080/swagger-ui.html`。

先登录：

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "你的用户名",
  "password": "你的密码"
}
```

从响应的 `data.token` 复制 JWT。后续请求都需要：

```http
Authorization: Bearer <JWT_TOKEN>
```

Postman 中选择 **Authorization → Bearer Token**，把 `data.token` 填入 Token。建议新建环境变量：

- `baseUrl`：`http://localhost:8080`
- `token`：登录响应中的 token
- `activityId`：一个正在进行的活动 ID
- `requestId`：每次新抽奖生成一个 UUID

未登录或 Token 失效返回 HTTP 401、错误码 `20004`。登录但缺少接口权限返回 HTTP 403、错误码 `20001`。

## 2. 权限与数据范围

| 接口 | Controller 基础权限 | 全量数据权限 | 普通用户范围 |
|---|---|---|---|
| 查询活动 | `lottery:activity:read` | 无 | 可查询指定活动的公开字段 |
| 发起抽奖 | `lottery:draw` | 无 | 用户 ID 只取自 JWT |
| 按 requestId 查询 | `lottery:draw:read` | `lottery:draw:read:all` | 只能查询本人订单 |
| 分页查询订单 | `lottery:order:read:all` | 同左 | 普通 USER 默认无此接口权限 |
| 分页查询记录 | `lottery:record:read` | `lottery:record:read:all` | 只能查本人，传他人 `userId` 返回 403 |
| 分页查询权益 | `benefit:read` | `benefit:read:all` | 只能查本人，传他人 `userId` 返回 403 |
| 查询权益详情 | `benefit:read` | `benefit:read:all` | 只能查本人权益 |

V5 数据库迁移为 `USER` 角色授予 5 个基础权限，为 `ADMIN` 额外授予 4 个 `read:all` 权限。系统没有另建一套管理员 Controller，而是通过权限和 Service 数据范围复用相同接口。

## 3. 七个接口

### 3.1 查询抽奖活动

```http
GET /api/lottery/activities/{activityId}
```

示例：

```powershell
curl.exe "http://localhost:8080/api/lottery/activities/10" `
  -H "Authorization: Bearer $env:LUCKYHUB_TOKEN"
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 10,
    "activityName": "夏日抽奖",
    "description": "每天可以抽 3 次",
    "status": "RUNNING",
    "startTime": "2026-07-31T09:00:00",
    "endTime": "2026-08-10T23:59:59",
    "dailyLimit": 3
  }
}
```

该公开视图故意不返回 `noWinWeight`、奖品权重、总库存和精确剩余库存。

### 3.2 发起单抽或十连抽

```http
POST /api/lottery/draws
Content-Type: application/json
```

请求字段：

| 字段 | 类型 | 必填 | 规则 |
|---|---|---|---|
| `requestId` | String | 是 | 规范的 36 字符 UUID 文本（字母大小写均可）；入口还有最长 64 字符的防护，但更长文本仍不能通过 Service 的 UUID 校验；新抽奖生成新值，网络重试复用原值 |
| `activityId` | Long | 是 | 大于 0 |
| `drawCount` | Integer | 是 | 只能为 `1` 或 `10` |

请求体不能传 `userId`，服务端只相信 JWT 中的用户 ID。

单抽：

```powershell
$body = @{
  requestId = [guid]::NewGuid().ToString()
  activityId = 10
  drawCount = 1
} | ConvertTo-Json

curl.exe -X POST "http://localhost:8080/api/lottery/draws" `
  -H "Authorization: Bearer $env:LUCKYHUB_TOKEN" `
  -H "Content-Type: application/json" `
  --data-binary $body
```

中奖响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "orderId": 501,
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "activityId": 10,
    "drawCount": 1,
    "drawDate": "2026-07-31",
    "status": "SUCCESS",
    "failReason": null,
    "completedAt": "2026-07-31T23:40:12.123",
    "results": [
      {
        "recordId": 901,
        "sequenceNo": 1,
        "resultType": "WIN",
        "prizeId": 20,
        "prizeName": "10 元优惠券",
        "prizeType": "COUPON",
        "prizeImageUrl": "https://example-bucket.oss-cn-hangzhou.aliyuncs.com/prizes/coupon.png",
        "benefitId": 301
      }
    ]
  }
}
```

未中奖响应中，奖品和权益字段都是 `null`：

```json
{
  "recordId": 902,
  "sequenceNo": 1,
  "resultType": "NO_WIN",
  "prizeId": null,
  "prizeName": null,
  "prizeType": null,
  "prizeImageUrl": null,
  "benefitId": null
}
```

十连抽只需把 `drawCount` 改成 `10`。Postman 的 Body 选择 **raw → JSON**：

```json
{
  "requestId": "0a930ca2-a264-4e0d-82c6-90a731531312",
  "activityId": 10,
  "drawCount": 10
}
```

成功时 `results` 必须有 10 项，`sequenceNo` 为 1～10。剩余额度不足 10 时整笔拒绝，不会先扣一部分，也不会生成不完整结果。

### 3.3 按 requestId 查询抽奖结果

```http
GET /api/lottery/draws/{requestId}
```

```powershell
curl.exe "http://localhost:8080/api/lottery/draws/550e8400-e29b-41d4-a716-446655440000" `
  -H "Authorization: Bearer $env:LUCKYHUB_TOKEN"
```

响应 `data` 与抽奖接口的 `DrawOrderView` 相同。普通用户只能读取自己的订单；不存在或无权限时不会泄漏其他人的订单信息。

这里要区分“查询”与“再次执行”：只要订单存在，GET 对 `PROCESSING` 或 `FAILED` 也返回 HTTP 200 的 `DrawOrderView`，客户端读取 `status` 和 `failReason`，此时 `results` 通常为空。错误码 `43007/43008` 是拿同一个 requestId 再次 POST 时的结果，不是 GET 查询已有订单的结果。

### 3.4 分页查询全部抽奖订单

```http
GET /api/lottery/orders
```

该接口在 Controller 层要求 `lottery:order:read:all`，通常供 ADMIN 使用。

查询参数：

| 参数 | 默认值 | 说明 |
|---|---:|---|
| `page` | 1 | 1～1,000,000 |
| `size` | 20 | 1～100 |
| `userId` | 空 | 指定用户，必须大于 0 |
| `activityId` | 空 | 指定活动，必须大于 0 |
| `status` | 空 | `PROCESSING`、`SUCCESS`、`FAILED` |
| `drawDate` | 空 | `yyyy-MM-dd`，MySQL 支持范围内 |

```powershell
curl.exe "http://localhost:8080/api/lottery/orders?page=1&size=20&activityId=10&status=SUCCESS&drawDate=2026-07-31" `
  -H "Authorization: Bearer $env:LUCKYHUB_TOKEN"
```

分页响应（下面只展示一条订单和一条结果）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "orderId": 501,
        "requestId": "550e8400-e29b-41d4-a716-446655440000",
        "activityId": 10,
        "drawCount": 1,
        "drawDate": "2026-07-31",
        "status": "SUCCESS",
        "failReason": null,
        "completedAt": "2026-07-31T23:40:12.123",
        "results": [
          {
            "recordId": 901,
            "sequenceNo": 1,
            "resultType": "WIN",
            "prizeId": 20,
            "prizeName": "10 元优惠券",
            "prizeType": "COUPON",
            "prizeImageUrl": "https://example-bucket.oss-cn-hangzhou.aliyuncs.com/prizes/coupon.png",
            "benefitId": 301
          }
        ]
      }
    ],
    "total": 1,
    "page": 1,
    "size": 20,
    "pages": 1
  }
}
```

排序固定为 `created_at DESC, id DESC`，保证时间相同也能稳定排序。

### 3.5 分页查询抽奖记录

```http
GET /api/lottery/records
```

查询参数：

| 参数 | 默认值 | 说明 |
|---|---:|---|
| `page` | 1 | 1～1,000,000 |
| `size` | 20 | 1～100 |
| `userId` | 空 | 普通用户只能为空或本人 ID |
| `activityId` | 空 | 活动 ID |
| `resultType` | 空 | `WIN` 或 `NO_WIN` |
| `startDate` | 空 | 包含当天 00:00:00 |
| `endDate` | 空 | 包含整天，实现为“小于次日 00:00:00” |

```powershell
curl.exe "http://localhost:8080/api/lottery/records?page=1&size=20&resultType=WIN&startDate=2026-07-01&endDate=2026-07-31" `
  -H "Authorization: Bearer $env:LUCKYHUB_TOKEN"
```

记录按 `draw_time DESC, id DESC` 排序。`endDate` 最大为 `9999-12-30`，因为代码需要计算下一天作为排他上界。

`data.records` 中每项结构示例：

```json
{
  "id": 901,
  "orderId": 501,
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "sequenceNo": 1,
  "userId": 8,
  "activityId": 10,
  "resultType": "WIN",
  "prizeId": 20,
  "prizeName": "10 元优惠券",
  "prizeType": "COUPON",
  "prizeImageUrl": "https://example-bucket.oss-cn-hangzhou.aliyuncs.com/prizes/coupon.png",
  "benefitId": 301,
  "drawTime": "2026-07-31T23:40:12.123"
}
```

### 3.6 分页查询权益

```http
GET /api/benefits
```

查询参数：

| 参数 | 默认值 | 说明 |
|---|---:|---|
| `page` | 1 | 1～1,000,000 |
| `size` | 20 | 1～100 |
| `userId` | 空 | 普通用户只能为空或本人 ID |
| `status` | 空 | `PENDING`、`AVAILABLE`、`CLAIM_PENDING`、`GRANT_FAILED` |
| `prizeType` | 空 | `COUPON`、`POINTS`、`MEMBERSHIP`、`PHYSICAL` |
| `startDate` | 空 | 获得日期起点，包含当天 |
| `endDate` | 空 | 获得日期终点，包含整天；最大 `9999-12-30` |

```powershell
curl.exe "http://localhost:8080/api/benefits?page=1&size=20&status=AVAILABLE&prizeType=COUPON" `
  -H "Authorization: Bearer $env:LUCKYHUB_TOKEN"
```

权益按 `obtained_at DESC, id DESC` 排序。奖品名称和图片来自抽奖记录中的历史快照，而不是当前奖品表。

响应使用与 3.4 相同的分页外壳，`data.records` 中每一项就是下一节展示的 `BenefitView` 结构。例如：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 301,
        "drawRecordId": 901,
        "userId": 8,
        "prizeId": 20,
        "prizeType": "COUPON",
        "prizeName": "10 元优惠券",
        "prizeImageUrl": "https://example-bucket.oss-cn-hangzhou.aliyuncs.com/prizes/coupon.png",
        "quantity": 1,
        "status": "AVAILABLE",
        "obtainedAt": "2026-07-31T23:40:12.123",
        "expireAt": null
      }
    ],
    "total": 1,
    "page": 1,
    "size": 20,
    "pages": 1
  }
}
```

### 3.7 查询权益详情

```http
GET /api/benefits/{id}
```

```powershell
curl.exe "http://localhost:8080/api/benefits/301" `
  -H "Authorization: Bearer $env:LUCKYHUB_TOKEN"
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 301,
    "drawRecordId": 901,
    "userId": 8,
    "prizeId": 20,
    "prizeType": "COUPON",
    "prizeName": "10 元优惠券",
    "prizeImageUrl": "https://example-bucket.oss-cn-hangzhou.aliyuncs.com/prizes/coupon.png",
    "quantity": 1,
    "status": "AVAILABLE",
    "obtainedAt": "2026-07-31T23:40:12.123",
    "expireAt": null
  }
}
```

## 4. requestId 的正确使用

`requestId` 是一次业务抽奖的身份证，不是 HTTP 请求追踪 ID。

- 新的一次抽奖：生成新的 UUID。
- 请求超时、不知道是否成功：使用原 UUID 重试，或调用查询接口。
- 同一 UUID、同一用户、同一 `activityId`、同一 `drawCount`：成功订单返回数据库中原结果，不重复扣额度、库存或创建权益。
- 同一 UUID 改了活动或抽数：返回 `43006`。
- 其他用户使用相同 UUID 发起抽奖：返回幂等冲突，不能“接管”订单。
- 原订单为 `PROCESSING`：再次 POST 返回 `43007`，稍后用原 ID 执行 GET 查询。
- 原订单为 `FAILED`：再次 POST 返回 `43008`；GET 仍能查到失败状态，但该 ID 不能再次执行，新尝试必须换新 UUID。

`dailyLimit` 不属于幂等身份。管理员后来修改每日上限，也不会改变已经存在的预占或订单。

## 5. 错误码

| HTTP | code | message | 常见原因 |
|---:|---:|---|---|
| 400 | 30000 | 参数校验失败 | DTO 注解失败、分页或日期越界 |
| 400 | 30001 | 请求内容格式错误 | JSON 语法错误或枚举拼写错误 |
| 401 | 20000 | 请先登录 | 线程上下文中没有登录用户 |
| 401 | 20004 | 登录凭证无效或已过期 | JWT 缺失、无效、过期或会话失效 |
| 403 | 20001 | 无权执行此操作 | 缺少 `@RequirePermission` 指定的权限 |
| 404 | 43001 | 抽奖活动不存在 | activityId 不存在 |
| 409 | 43002 | 当前活动不可参与抽奖 | 非 RUNNING、未开始或已经结束 |
| 400 | 43003 | 抽奖参数不合法 | requestId 不是标准 UUID等 Service 校验失败 |
| 409 | 43004 | 每日抽奖额度不足 | 单抽剩余额度不足 |
| 409 | 43005 | 十连抽额度不足 | 剩余不足 10 次，整笔拒绝 |
| 409 | 43006 | 重复请求的参数不一致 | requestId 身份冲突 |
| 409 | 43007 | 抽奖订单正在处理中 | 相同 requestId 的订单仍在执行 |
| 409 | 43008 | 抽奖订单处理失败 | 失败订单、终态 Redis 预占却无订单等 |
| 403 | 43009 | 无权访问该抽奖数据 | requestId 不存在，或读取不属于自己的抽奖数据；故意使用同一响应避免泄漏订单是否存在 |
| 503 | 53001 | 抽奖额度服务不可用 | Redis/Lua 返回异常或预占数据损坏 |
| 409 | 53002 | 抽奖请求正在处理中 | Redisson 锁未取得或线程被中断 |
| 500 | 53003 | 抽奖配置没有有效权重 | 权重/库存快照配置不合法 |
| 500 | 53004 | 抽奖事务处理失败 | 数据库事务或状态竞争失败 |
| 404 | 44001 | 权益不存在 | 权益 ID 不存在 |
| 403 | 44002 | 无权访问该权益 | 权益领域预留的访问拒绝错误 |
| 409 | 44003 | 当前权益状态不允许执行该操作 | 异步履约状态冲突 |
| 500 | 54001 | 权益发放失败 | 第一版履约处理失败，消息会保留重试 |

当前查询实现通过通用 `DataScopeService` 拒绝读取他人权益，因此实际 HTTP 查询越权通常返回通用 `20001`；`44002` 已在权益错误枚举中定义，但当前两个权益查询方法没有直接抛出它。

`44003` 和 `54001` 当前只用于 Redis Stream 的异步权益履约和日志处理，不是上面 7 个 HTTP Controller 正常暴露的响应。它们列在这里是为了排查后台消息，而不是要求前端在抽奖/查询接口中等待权益履约完成。

## 6. 快速测试顺序

1. 确认 MySQL、Redis、应用均已启动，Flyway 已执行到 V7。
2. 使用 `/api/auth/login` 登录并保存 JWT。
3. 用管理员接口准备活动：至少关联一个启用且有可用库存的奖品，并保证“奖品权重总和 + `noWinWeight`”大于 0；发布后让当前时间处于 `RUNNING` 区间。
4. 调用活动公开查询确认 `status=RUNNING`。
5. 生成 UUID，执行单抽；保存 `requestId`、`orderId`、`recordId` 和可能的 `benefitId`。
6. 使用完全相同请求重试，确认返回同一订单和记录。
7. 使用相同 UUID 把 `drawCount` 改成 10，确认返回 `43006`。
8. 换新 UUID 测十连抽，确认成功时正好 10 条结果。
9. 查询记录和权益；普通用户再尝试传其他 `userId`，确认返回 403。

更深入的执行流程、Redis Lua、事务、Outbox、Stream、对账和排错方法见
[`LuckyHub-抽奖核心流程实现详解.md`](LuckyHub-抽奖核心流程实现详解.md)。
