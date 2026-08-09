# 阶段 4 任务 7：履约管理 API 与安全完成介绍

## 为什么有这个

履约引擎不能只在数据库里运行。运营人员需要创建测试任务、查看状态、处理隔离任务和控制模拟故障；这些能力又能影响用户资产，必须经过登录和精确权限校验，并防止敏感信息落库。

## 这是干什么的

本任务提供 6 个管理员接口，并分配四个独立权限：创建、读取、人工操作、模拟器控制。人工操作支持重新处理隔离任务或终止尚未完成的任务。

## 具体实现

- `POST /api/admin/fulfillment/tasks`：创建强类型任务，返回 201。
- `GET /api/admin/fulfillment/tasks`：按状态、类型、用户和来源分页。
- `GET /api/admin/fulfillment/tasks/{fulfillmentNo}`：查看任务详情。
- `POST .../{fulfillmentNo}/retry`：仅允许隔离任务恢复为待处理，并记录操作人和说明。
- `POST .../{fulfillmentNo}/terminate`：终止安全状态下的任务；成功或正在供应方调用中的任务不能强制终止。
- `POST /api/admin/simulators/failure-rules`：配置某种模拟供应方接下来 N 次的行为。
- 新增 `fulfillment:create`、`fulfillment:read`、`fulfillment:operate`、`simulator:control` 常量，数据库权限已在 V14 分配给管理员。
- API payload 会按履约类型转换为强类型模型，完整物流信息会返回 400。
- 安全消息最多 500 字符；任务、尝试和模拟供应方只保存脱敏快照与固定错误摘要。

## 举例解释

管理员可以先让积分模拟器下一次返回临时失败，再创建 100 积分任务，观察任务进入 `RETRY_WAITING`。如果达到上限进入隔离，确认供应方恢复后调用 `/retry`，任务重新进入队列，同时隔离记录保存“谁在什么时候确认重试”。

四个权限互相独立：客服可以只有读取权限，运维可以处理隔离任务，测试人员才拥有模拟故障权限，避免所有管理员都能随意制造失败。

## PowerShell 示例

```powershell
$headers = @{ Authorization = "Bearer $token" }
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/admin/fulfillment/tasks?status=QUARANTINED" -Headers $headers
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/admin/fulfillment/tasks/FUL-001/retry" -Headers $headers -ContentType "application/json" -Body '{"note":"供应方已恢复"}'
```

## 测试证据

- 红灯：修正测试 JSON 语法后，测试稳定因四个权限常量和两个人工操作方法不存在而编译失败。
- 绿灯：控制器、安全链和安全数据测试共 6 个通过（最终安全类增加到 3 个用例），覆盖 201/200、参数错误、401/403、精确权限、错误截断、物流脱敏、人工重试和终止。
- 回归：原抽奖安全链测试通过。

下一任务进行 V1—V15 空库迁移、全量测试、可执行 JAR 和 OpenAPI 验证，并更新总路线与交接文档。
