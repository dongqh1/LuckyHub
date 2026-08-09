# LuckyHub 统一履约与模拟供应方 API

## 1. 用途与边界

阶段 4 为优惠券、积分、会员和物流提供同一套异步履约流程。当前四个 Gateway 连接本地 MySQL 模拟供应方，可以直接运行、注入故障和验证幂等；未来接真实供应商时替换 Gateway 实现即可。

当前边界：

- 尚未把抽奖 `user_benefit` 迁移到本履约引擎，该工作属于阶段 5。
- 物流只接受脱敏合成数据，没有真实地址、包裹和轨迹，该工作属于阶段 6。
- 模拟供应方是开发/测试设施，不代表真实券商、积分中心、会员中心或快递公司。

## 2. 权限

| 权限 | 能力 |
|---|---|
| `fulfillment:create` | 创建履约任务 |
| `fulfillment:read` | 查询任务与分页 |
| `fulfillment:operate` | 重试隔离任务或终止任务 |
| `simulator:control` | 配置本地模拟供应方故障 |

四项权限默认只授予 `ADMIN`。所有接口都位于 `/api/admin/*`，必须携带有效 JWT。

## 3. 状态与处理规则

```text
PENDING / RETRY_WAITING
  -> PROCESSING（短事务领取并获得随机租约）
  -> 事务外调用 Gateway
  -> SUCCEEDED | RETRY_WAITING | RECONCILING | QUARANTINED

RECONCILING
  -> PROCESSING
  -> 事务外按 fulfillmentNo 查询 Gateway
  -> SUCCEEDED | PENDING

QUARANTINED
  -> 管理员 retry -> PENDING
  -> 管理员 terminate -> TERMINATED
```

- `SUCCEEDED`：供应方明确成功。
- `RETRYABLE_FAILURE`：按 1、2、4……秒指数退避，默认最长 1 分钟。
- `PERMANENT_FAILURE`：立即隔离。
- `UNKNOWN`：先查询，不直接重发。
- `NOT_FOUND`：只有查询确认不存在后才回到待执行。
- `PROCESSING` 租约过期：进入 `RECONCILING`，不盲目执行第二次。

## 4. 创建任务

```http
POST /api/admin/fulfillment/tasks
Authorization: Bearer <token>
Content-Type: application/json
```

积分示例：

```json
{
  "fulfillmentNo": "FUL-MANUAL-001",
  "sourceType": "MANUAL",
  "sourceId": "TICKET-001",
  "fulfillmentType": "POINTS",
  "targetUserId": 11,
  "payload": {
    "points": 500,
    "reason": "人工补发"
  },
  "maxAttempts": 5
}
```

四种 payload：

```json
{"couponTemplateCode":"NEW20","quantity":1}
```

```json
{"points":500,"reason":"抽奖奖励"}
```

```json
{"membershipCode":"VIP_MONTH","durationDays":30}
```

```json
{
  "skuCode":"SKU-CUP",
  "quantity":1,
  "receiverMasked":"张*",
  "phoneMasked":"138****5678",
  "regionMasked":"浙江省杭州市***"
}
```

物流请求若含完整姓名、11 位明文手机号或未脱敏地区会被拒绝。创建返回 HTTP 201，任务状态为 `PENDING`，此时不会同步调用供应方。

`fulfillmentNo` 是平台和供应方共同使用的稳定幂等键。同编号、同参数重复创建返回原任务；同编号、不同参数返回 `52002`。

## 5. 查询

```http
GET /api/admin/fulfillment/tasks/FUL-MANUAL-001
GET /api/admin/fulfillment/tasks?page=1&size=20&status=QUARANTINED&fulfillmentType=POINTS&targetUserId=11
```

可选筛选：`status`、`fulfillmentType`、`targetUserId`、`sourceType`、`sourceId`。响应包含强类型 payload、请求指纹、尝试次数、外部流水号和安全错误摘要，不返回租约令牌。

## 6. 人工操作

重新处理隔离任务：

```http
POST /api/admin/fulfillment/tasks/FUL-MANUAL-001/retry
Content-Type: application/json

{"note":"供应方已恢复，确认重试"}
```

终止任务：

```http
POST /api/admin/fulfillment/tasks/FUL-MANUAL-001/terminate
Content-Type: application/json

{"note":"业务已取消"}
```

正在供应方调用的 `PROCESSING` 任务不能强制终止，避免外部已成功而平台误判终止；`SUCCEEDED` 也不能终止。

## 7. 模拟故障

```http
POST /api/admin/simulators/failure-rules
Content-Type: application/json

{
  "fulfillmentType": "MEMBERSHIP",
  "failureMode": "UNKNOWN_AFTER_SUCCESS",
  "count": 1
}
```

| 模式 | 是否写供应方记录 | Gateway 返回 |
|---|---:|---|
| `SUCCESS` | 是 | 成功 |
| `RETRYABLE` | 否 | 临时失败 |
| `PERMANENT` | 否 | 永久失败 |
| `UNKNOWN_BEFORE` | 否 | 发送前未知 |
| `UNKNOWN_AFTER_SUCCESS` | 是 | 已处理但响应丢失 |

规则使用事务行锁按次数原子消费。`UNKNOWN_AFTER_SUCCESS` 用于证明后续查询能找到同一条供应方记录，平台不会重复执行。

## 8. PowerShell 完整示例

```powershell
$headers = @{
    Authorization = "Bearer $token"
    "Content-Type" = "application/json"
}

$rule = @{
    fulfillmentType = "MEMBERSHIP"
    failureMode = "UNKNOWN_AFTER_SUCCESS"
    count = 1
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
    -Uri "http://localhost:8080/api/admin/simulators/failure-rules" `
    -Headers $headers -Body $rule

$task = @{
    fulfillmentNo = "FUL-DEMO-001"
    sourceType = "MANUAL"
    sourceId = "DEMO-001"
    fulfillmentType = "MEMBERSHIP"
    targetUserId = 11
    payload = @{ membershipCode = "VIP_MONTH"; durationDays = 30 }
    maxAttempts = 5
} | ConvertTo-Json -Depth 4

Invoke-RestMethod -Method Post `
    -Uri "http://localhost:8080/api/admin/fulfillment/tasks" `
    -Headers $headers -Body $task

Invoke-RestMethod -Method Get `
    -Uri "http://localhost:8080/api/admin/fulfillment/tasks/FUL-DEMO-001" `
    -Headers $headers
```

## 9. 错误码

| 错误码 | 含义 |
|---:|---|
| 52001 | 任务不存在 |
| 52002 | 履约编号幂等参数冲突 |
| 52003 | payload 或请求不合法 |
| 52004 | 任务状态冲突 |
| 52005 | 租约冲突 |
| 52006 | Gateway 暂不可用 |
| 52007 | 隔离记录不存在 |
| 52008 | 模拟供应方幂等冲突 |
| 52009 | 模拟失败规则不合法 |
| 52010 | 当前状态不允许操作 |

## 10. 替换为真实供应方

以真实券平台为例：新增一个 `CouponGateway` 实现，把供应方的成功码、可重试码、永久错误和超时翻译为 `GatewayResult`；将 `fulfillmentNo` 作为供应方幂等号；`query` 必须能按该编号查结果。履约任务、Worker、退避、隔离和管理 API 不需要改变。

真实适配器不得把 API 密钥、原始响应、完整手机号/地址或堆栈写入任务和尝试表，只能保存外部流水号、错误分类、稳定错误码和最多 500 字的安全摘要。
