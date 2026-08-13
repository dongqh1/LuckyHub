# 实物物流 API

本文只使用虚构测试数据。先登录并把 JWT 放入环境变量：

```powershell
$BaseUrl = 'http://localhost:8080'
$Token = '<从登录接口取得的测试 JWT>'
$Headers = @{ Authorization = "Bearer $Token"; 'Content-Type' = 'application/json' }
```

## 地址与三种来源

创建地址时，示例值是虚构测试数据，不能粘贴真实姓名、手机或门牌号到脚本、工单和日志：

```powershell
$Address = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/shipping/addresses" -Headers $Headers -Body (@{
  receiver='测试收件人'; phone='13900000000'; province='测试省'; city='测试市'
  district='测试区'; detail='测试路 1 号（非真实地址）'; isDefault=$true
} | ConvertTo-Json)
$AddressId = $Address.data.id
```

现金实物下单后仍需走支付与支付回调；`status` 表示支付，`shippingStatus` 单独表示物流：

```powershell
$OrderNo = "DOC-CASH-$([guid]::NewGuid())"
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/orders" -Headers $Headers -Body (@{
  orderNo=$OrderNo; skuId=123; quantity=1; addressId=$AddressId
} | ConvertTo-Json)
```

积分实物兑换成功会原子扣积分和库存，并创建同一个物流聚合：

```powershell
$RedemptionNo = "DOC-POINTS-$([guid]::NewGuid())"
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/points/redemptions" -Headers $Headers -Body (@{
  redemptionNo=$RedemptionNo; skuId=456; quantity=1; addressId=$AddressId
} | ConvertTo-Json)
```

抽奖实物中奖时没有发货单；权益为 `CLAIM_PENDING` 后才领取：

```powershell
$BenefitId = 789
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/benefits/$BenefitId/claim" -Headers $Headers -Body (@{
  requestId=[guid]::NewGuid().ToString(); addressId=$AddressId
} | ConvertTo-Json)
```

## 物流查询与本地模拟事件

用户查询只返回脱敏后的姓名、手机和地区摘要；客户端不得依赖星号数量或具体掩码形状：

```powershell
$ShippingNo = '<接口返回的 SHIPPING-...>'
Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/shipping/orders/$ShippingNo" -Headers $Headers
Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/shipping/orders/$ShippingNo/tracking" -Headers $Headers
```

管理员只能在本地模拟器生成事件。接口内部会随机生成 nonce、用服务端测试密钥签名，并走正式回调处理；不要在命令行提供密钥或签名：

```powershell
$FulfillmentNo = '<LOGISTICS-...>'
$AdminHeaders = @{ Authorization = 'Bearer <测试管理员 JWT>'; 'Content-Type' = 'application/json' }
foreach ($Type in 'PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED') {
  Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/admin/simulators/logistics/$FulfillmentNo/events" `
    -Headers $AdminHeaders -Body (@{ eventType=$Type; eventTime=(Get-Date).ToString('s');
      locationSummary='测试物流节点'; description='脱敏轨迹摘要' } | ConvertTo-Json)
}
Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/admin/shipping/orders/$ShippingNo" -Headers $AdminHeaders
```

真实回调入口是 `POST /api/shipping/callbacks/logistics`。它要求 HMAC、时间窗和 nonce 防重放；文档不提供可复用密钥或伪造签名示例。

## 安全边界

- 姓名、手机和详细地址只允许存在于地址簿/地址快照的加密列，以及 Gateway 调用前的瞬时内存。
- API、日志、履约任务、尝试、回调收据和模拟器表只能保存脱敏摘要或摘要哈希。
- 示例 ID 都要替换为当前环境公开接口创建的数据；不要直接改数据库业务状态。
- 当前不包含真实快递、运费、购物车、退款、退货、换货或售后。
