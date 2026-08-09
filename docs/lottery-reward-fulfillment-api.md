# 抽奖统一奖励与履约 API

## 适用范围

阶段 5 支持五类抽奖奖励：优惠券、积分、会员、实物和奖励抽奖次数。券、积分和会员通过统一履约与本地模拟供应方执行；实物只进入待领取状态，收货地址和物流执行留到阶段 6。

## 配置顺序

1. 先创建可用的优惠券模板、会员产品或商品 SKU；积分和抽奖次数不需要目标资产。
2. 调用 `POST /api/admin/reward-definitions` 创建奖励定义。
3. 创建或修改奖品时填写 `rewardDefinitionId`，系统会校验奖励类型与兼容奖品类型。
4. 把奖品加入运行中的活动，用户通过 `POST /api/lottery/draws` 抽奖。

示例奖励定义：

```json
{
  "rewardCode": "SUMMER-COUPON-2",
  "rewardName": "夏日优惠券两张",
  "rewardType": "COUPON",
  "targetId": 12,
  "quantity": 2,
  "configSnapshot": null
}
```

PowerShell 示例：

```powershell
$headers = @{ Authorization = "Bearer $token" }
$body = @{
  requestId = [guid]::NewGuid().ToString()
  activityId = 1001
  drawCount = 1
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/lottery/draws' `
  -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $body
```

## 五类状态时间线

```text
COUPON      PENDING -> 履约任务 SUCCEEDED -> 本地用户券 -> AVAILABLE
POINTS      PENDING -> 履约任务 SUCCEEDED -> 积分账户/流水 -> AVAILABLE
MEMBERSHIP  PENDING -> 履约任务 SUCCEEDED -> 会员发放/续期 -> AVAILABLE
PRODUCT     PENDING -> CLAIM_PENDING（阶段 6 再创建物流任务）
DRAW_CHANCE PENDING -> 次数账户/流水 -> AVAILABLE
```

履约任务隔离或终止时，权益变为 `GRANT_FAILED`。管理员重试履约成功后，投影器可以恢复为 `AVAILABLE`。本地资产写入失败只保存“本地资产投影失败”，下一轮可安全重试。

## 权益查询

`GET /api/benefits` 和 `GET /api/benefits/{id}` 保留原字段，并追加：

```json
{
  "rewardDefinitionId": 31,
  "rewardType": "COUPON",
  "rewardQuantity": 2,
  "fulfillmentNo": "LOTTERY-BENEFIT-501",
  "fulfillmentStatus": "SUCCEEDED"
}
```

阶段 5 之前的历史权益没有奖励快照，上述字段返回 `null`，原字段和原有含义不变。

## 幂等与安全

- 抽奖请求以 UUID `requestId` 幂等。
- 履约编号固定为 `LOTTERY-BENEFIT-{benefitId}`。
- 券和会员的单项业务编号带稳定序号，重复投影不会重复发放。
- 积分和抽奖次数使用唯一业务流水，不会重复入账。
- 消费端交叉核对订单、中奖记录、权益和冻结快照。身份不一致的事件只进入隔离表，不调用供应方，也不创建本地资产。

## 阶段 6 边界

实物中奖后只返回 `CLAIM_PENDING`。阶段 5 不收集收货地址、不创建 `LOGISTICS` 履约任务，也不产生 `sim_logistics_record`。阶段 6 将实现地址快照、领取期限、发货和物流查询。
