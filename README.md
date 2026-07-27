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
