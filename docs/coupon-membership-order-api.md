# LuckyHub 优惠券、会员、现金订单与模拟支付 API

## 1. 统一约定

- 金额单位均为整数分，例如 `19900` 表示 199 元。
- 除支付回调外，请求都使用 `Authorization: Bearer <JWT>`。
- 成功响应为 `{"code":0,"message":"success","data":...}`。
- 用户接口只操作当前登录用户的资产，不接收可伪造的 `userId`。
- 计价顺序固定为：商品原价 → 会员折扣 → 优惠券 → 应付金额。

## 2. 权限

| 权限 | 用途 | 默认角色 |
|---|---|---|
| `coupon:read` | 查询本人优惠券 | USER、ADMIN |
| `coupon:manage` | 创建券模板、发券 | ADMIN |
| `membership:read` | 查询、购买/续费本人会员 | USER、ADMIN |
| `membership:manage` | 创建会员产品 | ADMIN |
| `order:create` | 创建现金订单 | USER、ADMIN |
| `order:read` | 查询本人订单 | USER、ADMIN |
| `order:cancel` | 取消本人订单 | USER、ADMIN |
| `payment:create` | 创建本人模拟支付单 | USER、ADMIN |
| `payment:simulate` | 管理端模拟支付结果 | ADMIN |

## 3. 接口清单

### 优惠券

- `POST /api/admin/coupon-templates`：创建模板，201。
- `POST /api/admin/coupons/issues`：向指定用户发券，201；`businessNo` 和 `couponNo` 必须稳定且唯一。
- `GET /api/coupons?page=1&size=20`：查询本人券包。
- `GET /api/coupons/{id}`：查询本人单张券。

模板示例：

```json
{
  "templateCode": "MALL-20",
  "templateName": "满100减20",
  "couponType": "THRESHOLD",
  "thresholdCent": 10000,
  "discountCent": 2000,
  "applicableProductId": null,
  "validFrom": "2026-08-08T00:00:00",
  "validUntil": "2026-12-31T23:59:59",
  "perUserLimit": 1,
  "stackableWithMembership": true
}
```

券状态：`AVAILABLE -> LOCKED -> USED`；订单取消或超时为 `LOCKED -> AVAILABLE`；未使用且过期为 `EXPIRED`。

### 会员

- `POST /api/admin/membership-products`：创建月卡、季卡或年卡，201。
- `GET /api/memberships/me`：查询本人会员；没有会员时 `data` 为 `null`。
- `POST /api/memberships/purchases`：购买或续费本人会员，201。

会员产品示例：

```json
{
  "productCode": "VIP-YEAR",
  "productName": "VIP年卡",
  "membershipLevel": "VIP",
  "cardType": "YEAR",
  "durationDays": 365,
  "priceCent": 9900,
  "discountBasisPoints": 9000,
  "dailyDrawBonus": 2,
  "pointsMultiplierBasisPoints": 12000
}
```

购买示例：`{"businessNo":"MEM-BUY-1001","membershipProductId":1}`。有效会员续费从原到期时间顺延；已过期会员从当前时间开始。重复 `businessNo` 不重复延长。

### 现金订单

- `POST /api/orders`：创建单 SKU 立即购买订单，201。
- `GET /api/orders?page=1&size=20&status=PENDING_PAYMENT`：查询本人订单。
- `GET /api/orders/{orderNo}`：查询本人订单详情。
- `POST /api/orders/{orderNo}/cancel`：取消待支付订单。

创建示例：

```json
{"orderNo":"ORDER-1001","skuId":8,"quantity":1,"userCouponId":21}
```

取消示例：`{"reason":"用户不想要了"}`。订单状态为 `PENDING_PAYMENT -> PAID` 或 `PENDING_PAYMENT -> CANCELLED`。付款截止时间固定为创建后 30 分钟。

订单保存商品、SKU、会员等级与折扣、优惠券名称与金额、各阶段金额快照。后来修改商品或会员规则不会改写历史订单。

### 模拟支付

- `POST /api/payments`：为本人待支付订单创建支付单，201。
- `POST /api/admin/payments/{paymentNo}/simulate`：管理员模拟 `PROCESSING/SUCCESS/FAILURE`。
- `POST /callbacks/payments`：模拟渠道回调，不使用 JWT，使用 SHA-256 签名。

创建示例：`{"paymentNo":"PAY-1001","orderNo":"ORDER-1001"}`。

管理员模拟成功：`{"result":"SUCCESS"}`；模拟失败：`{"result":"FAILURE","failureReason":"余额不足"}`。

支付状态为 `PENDING -> SUCCESS` 或 `PENDING -> FAILED`。`PROCESSING` 不改变待支付状态；失败后订单仍可创建新的支付单。重复成功回调返回原成功结果，不重复扣库存或用券。

## 4. 价格例子

199 元商品使用 9 折会员和 20 元券：

```text
originalAmountCent          = 19900
membershipDiscountCent     = 1990
amountAfterMembershipCent  = 17910
couponDiscountCent         = 2000
payableAmountCent           = 15910
```

优惠金额不会把应付金额减成负数，最低为 0 分。

## 5. 稳定业务错误码

- 优惠券：`48001`～`48008`（模板重复/不存在、券不存在/不可用/不适用、发放上限、状态冲突、幂等冲突）。
- 会员：`49001`～`49006`（产品重复/不存在、用户不可用、幂等冲突、状态冲突、有效期错误）。
- 订单：`50001`～`50009`（订单不存在、SKU 不可售、金额/券错误、状态/幂等冲突、无权访问等）。
- 支付：`51001`～`51006`（支付单不存在、编号冲突、签名错误、状态冲突、金额不一致、订单不可支付）。
- 通用：未登录 `20004`，无权限 `20001`，参数校验 `30000`。

## 6. 本地从启动到支付

```powershell
docker compose up -d
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 spring-boot:run
```

先通过现有认证接口获取普通用户和管理员 token。管理员依次创建商品、初始化并分配 MALL 库存、创建券模板并发券、创建会员产品；用户购买会员、创建订单和支付单；管理员调用模拟支付接口。最终查询订单应为 `PAID`，优惠券为 `USED`，MALL 库存已消耗加 1。

## 7. 当前边界

- 这是本地模拟支付，不是真实微信/支付宝支付，不含退款、购物车和售后。
- 会员产品目前通过领域接口创建和购买，不执行真实资金扣款。
- 阶段 3 不包含地址、发货单、物流轨迹或外部 Gateway；这些按阶段 4 和阶段 6 实现。
- 积分商城仍是独立结算，不与现金、优惠券或会员混合。
