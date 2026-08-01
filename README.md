# LuckyHub

LuckyHub 是基于 Java 17、Spring Boot、MyBatis-Plus、MySQL 和 Redis 的抽奖营销服务。

## 本地启动

1. 将 `.env.example` 复制为 `.env`，填写数据库、Redis、JWT 和 OSS 配置。
2. 启动依赖：

```powershell
docker compose up -d
```

3. 运行测试：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 test
```

4. 启动应用：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 spring-boot:run
```

Swagger UI 地址为 `http://localhost:8080/swagger-ui.html`。

奖品管理和阿里云 OSS 的配置、权限及调用示例见
[`docs/prize-management-api.md`](docs/prize-management-api.md)。

OSS 图片上传的完整调用链、源码解析和排错案例见
[`docs/LuckyHub-OSS图片上传实现详解.md`](docs/LuckyHub-OSS图片上传实现详解.md)。

活动管理接口、状态流转和权限说明见
[`docs/activity-management-api.md`](docs/activity-management-api.md)。

从创建活动请求开始的完整执行流程和源码教学见
[`docs/LuckyHub-活动管理实现详解.md`](docs/LuckyHub-活动管理实现详解.md)。

活动状态定时任务从应用启动、Spring 调度、事务到 MySQL 批量更新的详细执行流程见
[`docs/LuckyHub-活动状态定时任务实现详解.md`](docs/LuckyHub-活动状态定时任务实现详解.md)。

抽奖与权益的七个接口、权限、请求响应、错误码和 Postman 示例见
[`docs/lottery-api.md`](docs/lottery-api.md)。

从 `POST /api/lottery/draws` 开始，逐层讲解 JWT、幂等、Redis Lua、分布式锁、
MySQL 原子库存、事务、Outbox、Redis Stream、权益履约和超时对账的教学文档见
[`docs/LuckyHub-抽奖核心流程实现详解.md`](docs/LuckyHub-抽奖核心流程实现详解.md)。

围绕 `eligibilityService.load(activityId)`，按照执行流程详细讲解抽奖资格配置快照、
Java `record`、十连抽配置复用和 MySQL 最终库存判断的教学文档见
[`docs/LuckyHub-抽奖资格快照EligibilitySnapshot详解.md`](docs/LuckyHub-抽奖资格快照EligibilitySnapshot详解.md)。

当前开发进度、已完成模块和下一步任务见
[`docs/LuckyHub-开发进度交接总结.md`](docs/LuckyHub-开发进度交接总结.md)。
