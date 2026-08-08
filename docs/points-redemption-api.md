# LuckyHub 积分账户与积分兑换 API

> 适用版本：阶段 2（Flyway V10）
>
> 基础地址：`http://localhost:8080`
>
> 统一响应：`{"code":0,"message":"success","data":...}`

## 1. 能力与边界

本阶段提供 MySQL 最终记账的积分账户、不可变积分流水，以及单 SKU 的纯积分兑换。兑换只使用 `POINTS` 渠道库存，数量范围为 1～100，不使用现金、优惠券或会员折扣，也不产生返积分。

当前不包含优惠券、会员、现金订单、支付、地址、物流和“抽奖中奖积分自动入账”。这些能力分别留给后续阶段。积分不是现金，不能提现或兑换现金。

## 2. 认证、权限和通用约定

请求头：

```text
Authorization: Bearer <JWT>
Content-Type: application/json; charset=utf-8
```

权限：

| 权限 | 默认角色 | 用途 |
| --- | --- | --- |
| `points:read` | USER、ADMIN | 查询本人余额、流水和兑换 |
| `points:redeem` | USER、ADMIN | 创建本人积分兑换 |
| `points:adjust` | ADMIN | 调整积分、冲正兑换 |

用户接口不接收 `userId`，始终使用 Token 中的当前用户。分页响应统一为：

```json
{
  "code": 0,
  "message": "success",
  "data": { "records": [], "total": 0, "page": 1, "size": 20, "pages": 0 }
}
```

通用错误：缺 Token 为 HTTP 401/`20004`；缺权限或访问他人兑换为 HTTP 403/`20001`；校验失败为 HTTP 400/`30000`；JSON 错误为 HTTP 400/`30001`；未知数据库/系统异常转换为安全的通用错误，不返回 SQL、重复键或堆栈。

以下示例先准备变量：

```powershell
$BaseUrl = 'http://localhost:8080'
$UserHeaders = @{ Authorization = "Bearer $UserToken" }
$AdminHeaders = @{ Authorization = "Bearer $AdminToken" }
```

## 3. 查询本人积分账户

`GET /api/points/account`，权限 `points:read`，无请求体。有效用户尚无账户行时返回余额 0，不会仅为查询创建资产。

响应示例：

```json
{"code":0,"message":"success","data":{"userId":1001,"balance":5000,"updatedAt":"2026-08-08T20:00:00"}}
```

可能错误：`20004`、`20001`、`47009`（用户不存在或已禁用）。

```powershell
Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/points/account" -Headers $UserHeaders
```

## 4. 查询本人积分流水

`GET /api/points/ledgers`，权限 `points:read`。

查询参数：`page` 默认 1；`size` 默认 20、最大 100；`businessId` 最大 100 字符；`businessType` 可为 `LOTTERY_REWARD/ORDER_REWARD/MEMBERSHIP_BONUS/REDEMPTION/REVERSAL/MANUAL_ADJUSTMENT`；`direction` 可为 `CREDIT/DEBIT`。

响应记录示例：

```json
{
  "id": 31,
  "userId": 1001,
  "businessType": "REDEMPTION",
  "businessId": "REDEEM-001",
  "direction": "DEBIT",
  "amount": 3000,
  "balanceAfter": 2000,
  "reversalOfLedgerId": null,
  "remark": "积分兑换",
  "createdAt": "2026-08-08T20:10:00"
}
```

可能错误：`20004`、`20001`、`30000`（分页、枚举或长度非法）。流水只按当前用户过滤，并按 `createdAt DESC, id DESC` 排序。

```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BaseUrl/api/points/ledgers?page=1&size=20&businessType=REDEMPTION" `
  -Headers $UserHeaders
```

## 5. 创建积分兑换

`POST /api/points/redemptions`，权限 `points:redeem`，成功返回 HTTP 201。

请求：

```json
{"redemptionNo":"REDEEM-001","skuId":1001,"quantity":1}
```

`redemptionNo` 最大 64 字符，是客户端生成的幂等号。同一用户、SKU、数量重复提交返回同一订单；相同编号换用户、SKU 或数量返回 `47002`。兑换单保存当时的商品、SKU、类型、图片和积分价格快照。

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 11,
    "redemptionNo": "REDEEM-001",
    "userId": 1001,
    "skuId": 1001,
    "quantity": 1,
    "unitPoints": 3000,
    "totalPoints": 3000,
    "productCode": "P-GIFT",
    "productName": "纪念礼盒",
    "skuCode": "SKU-GIFT-DEFAULT",
    "skuName": "默认规格",
    "productType": "PHYSICAL",
    "imageUrl": "https://example.com/gift.png",
    "status": "COMPLETED",
    "reversalNo": null,
    "failureReason": null,
    "createdAt": "2026-08-08T20:10:00",
    "updatedAt": "2026-08-08T20:10:00"
  }
}
```

可能错误：`20004`、`20001`、`30000`、`47001` 余额不足、`47002` 幂等冲突、`47005` 总积分溢出、`47007` SKU 不可兑换，以及库存域的 `46001/46002`。任何失败都会同时回滚兑换单、积分和库存。

```powershell
$Body = @{ redemptionNo = 'REDEEM-001'; skuId = 1001; quantity = 1 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/points/redemptions" `
  -Headers $UserHeaders -ContentType 'application/json; charset=utf-8' -Body $Body
```

## 6. 分页查询本人兑换

`GET /api/points/redemptions`，权限 `points:read`。参数：`page`、`size` 与流水相同；`status` 可为 `PROCESSING/COMPLETED/REVERSED`。响应为 `PageResponse<PointsRedemptionView>`，记录结构与创建响应一致。

可能错误：`20004`、`20001`、`30000`。查询条件强制包含当前用户，不能传入别人的用户 ID。

```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BaseUrl/api/points/redemptions?page=1&size=20&status=COMPLETED" `
  -Headers $UserHeaders
```

## 7. 查询本人单笔兑换

`GET /api/points/redemptions/{redemptionNo}`，权限 `points:read`。兑换号长度 1～64，响应为单个 `PointsRedemptionView`。

可能错误：`20004`、`20001`（记录属于其他用户）、`30000`、`47006`（兑换单不存在）。

```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BaseUrl/api/points/redemptions/REDEEM-001" -Headers $UserHeaders
```

## 8. 管理员调整积分

`POST /api/admin/points/adjustments`，权限 `points:adjust`，成功 HTTP 200。

请求：

```json
{"userId":1001,"delta":500,"businessId":"ADJ-001","reason":"活动补发"}
```

`delta > 0` 为入账，`delta < 0` 为扣减，不能为 0 或 `Long.MIN_VALUE`；`businessId` 最大 100 字符并作为 `MANUAL_ADJUSTMENT + businessId` 幂等身份。响应为 `PointsLedgerView`。

可能错误：`20004`、`20001`、`30000`、`47001` 余额不足、`47002` 幂等参数冲突、`47005` 数量非法/溢出、`47009` 用户不可用。

```powershell
$Body = @{ userId = 1001; delta = 500; businessId = 'ADJ-001'; reason = '活动补发' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/admin/points/adjustments" `
  -Headers $AdminHeaders -ContentType 'application/json; charset=utf-8' -Body $Body
```

## 9. 管理员冲正兑换

`POST /api/admin/points/redemptions/{redemptionNo}/reverse`，权限 `points:adjust`，成功 HTTP 200。

请求：

```json
{"reversalNo":"REV-001","reason":"兑换履约失败"}
```

只允许 `COMPLETED -> REVERSED`。同一冲正号重复调用返回同一结果；换冲正号再次处理返回 `47008`。冲正不会删除原扣分流水，而是增加一条 `REVERSAL/CREDIT` 流水，通过 `reversalOfLedgerId` 关联原 `REDEMPTION/DEBIT`，并把已消费的 POINTS 库存恢复一次。

可能错误：`20004`、`20001`、`30000`、`47006`、`47008`，以及异常库存状态的 `46003`。

```powershell
$Body = @{ reversalNo = 'REV-001'; reason = '兑换履约失败' } | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri "$BaseUrl/api/admin/points/redemptions/REDEEM-001/reverse" `
  -Headers $AdminHeaders -ContentType 'application/json; charset=utf-8' -Body $Body
```

## 10. 账户、流水与状态机

账户余额是当前可用值；流水是不可修改的资产历史。每次成功变化都新建流水并写入 `balanceAfter`。数据库 CHECK 和条件 SQL 双重保证余额与流水快照不为负。

兑换创建状态机：

```text
PROCESSING
  -> POINTS 库存预占
  -> REDEMPTION 扣分
  -> 库存确认
  -> COMPLETED
  -> 管理员冲正：REVERSAL 加分 + 库存反向恢复
  -> REVERSED
```

上述创建或冲正在单个 `READ_COMMITTED` MySQL 事务中完成。商品后来改名、改图或调价不会重写历史兑换快照。

## 11. 本地运行与验证

```powershell
docker compose up -d
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 test
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 package '-DskipTests'
java -jar .\target\luckyhub-0.0.1-SNAPSHOT.jar
```

阶段 2 最终证据：空库 V1→V10 契约 17/17、阶段聚焦测试 44/44、全量回归 312/312，均 0 失败、0 错误；打包 `BUILD SUCCESS`；JAR 实际启动后 Tomcat 监听 8080，`/v3/api-docs` 烟雾检查通过。
