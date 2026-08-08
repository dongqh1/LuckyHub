# 阶段 2 · 任务 7：积分与兑换 API 完成介绍

## 1. 完成内容

本任务把任务 3～6 已完成的积分账户、不可变流水和积分兑换事务开放为 7 个带真实登录与 RBAC 权限校验的 HTTP API：

- 用户查询自己的积分余额和流水；
- 用户创建、分页查询和查看自己的积分兑换；
- 管理员调整指定用户积分；
- 管理员冲正已完成的兑换；
- `/api/points/*` 已接入统一 Token 认证过滤器和权限拦截器。

控制器只负责读取登录用户、校验请求和调用服务。积分计算、库存状态变化、幂等判断和事务仍全部留在领域服务中。

## 2. 权限与数据隔离

| 操作 | 地址 | 权限 |
| --- | --- | --- |
| 查询余额、流水、兑换 | `/api/points/**` 的 GET 接口 | `points:read` |
| 创建兑换 | `POST /api/points/redemptions` | `points:redeem` |
| 调整积分、冲正兑换 | `/api/admin/points/**` | `points:adjust` |

普通用户接口没有 `userId` 参数。后端始终从 `LoginContext` 读取当前用户 ID，并把它加入流水和兑换查询条件，因此不能通过修改 JSON 或查询参数读取其他人的资产。

未携带有效 Token 返回 HTTP 401、业务码 `20004`；已经登录但缺少权限返回 HTTP 403、业务码 `20001`。

## 3. PowerShell 使用示例

以下示例假设 `$UserToken` 是普通用户 Token，`$AdminToken` 是管理员 Token：

```powershell
$BaseUrl = 'http://localhost:8080'
$UserHeaders = @{ Authorization = "Bearer $UserToken" }
$AdminHeaders = @{ Authorization = "Bearer $AdminToken" }
```

查询自己的余额：

```powershell
Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/points/account" -Headers $UserHeaders
```

管理员给用户 1001 增加 500 积分。`businessId` 是幂等号，相同参数可安全重试；负数 `delta` 表示扣减：

```powershell
$Body = @{
  userId = 1001
  delta = 500
  businessId = 'ADJ-20260808-001'
  reason = '活动补发'
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/admin/points/adjustments" `
  -Headers $AdminHeaders -ContentType 'application/json; charset=utf-8' -Body $Body
```

用户兑换一件价值 3000 积分的 SKU：

```powershell
$Body = @{ redemptionNo = 'REDEEM-20260808-001'; skuId = 1001; quantity = 1 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/points/redemptions" `
  -Headers $UserHeaders -ContentType 'application/json; charset=utf-8' -Body $Body
```

查询自己的积分流水：

```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BaseUrl/api/points/ledgers?page=1&size=20&businessType=REDEMPTION" `
  -Headers $UserHeaders
```

管理员冲正兑换，系统会新增 REVERSAL 加分流水并恢复已确认的 POINTS 库存：

```powershell
$Body = @{ reversalNo = 'REV-20260808-001'; reason = '兑换履约失败' } | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri "$BaseUrl/api/admin/points/redemptions/REDEEM-20260808-001/reverse" `
  -Headers $AdminHeaders -ContentType 'application/json; charset=utf-8' -Body $Body
```

## 4. 查询规则

余额表示用户当前还能使用多少积分；流水是不可修改的历史证据。流水和兑换分页统一返回 `PageResponse`，页码从 1 开始、每页 1～100 条。流水按创建时间和 ID 倒序排列。

业务查询参数经过 Jakarta Validation 校验，非法页码、超长业务号或超长兑换号返回 HTTP 400、业务码 `30000`，不会把数据库异常暴露给调用方。

## 5. 测试与修正

测试先证明控制器不存在而编译失败，再实现 API。首次真实鉴权链验证发现积分地址尚未进入认证与权限配置，随后补齐：

- `AuthenticationFilterConfig` 的 `/api/points/*` 认证范围；
- `PermissionInterceptorConfig` 的 `/api/points/**` 权限范围。

最终验证：

- `PointsControllerTests`：2 个通过；
- `PointsRedemptionControllerTests`：2 个通过；
- `PointsSecurityChainIntegrationTests`：1 个通过；
- `LotterySecurityChainIntegrationTests`：1 个通过。

共 6 个测试，0 失败、0 错误，原有抽奖鉴权链未回归。

## 6. 下一步

任务 8 将生成完整 API 手册，验证 V10 可从空库迁移，运行阶段 2 聚焦测试、全量回归与打包，并形成最终交接记录。
