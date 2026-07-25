# LuckyHub 数据库与 Redis 连接设计

## 目标

让本机运行的 Spring Boot 应用通过环境变量连接 Docker Compose 中的 MySQL 8.4 与 Redis 7.4，并用自动化测试验证两条连接。

## 技术选择

- Java 17、Spring Boot 4.1.0。
- MyBatis-Plus 3.5.17 的 Spring Boot 4 Starter。
- MySQL Connector/J 由 MyBatis-Plus Starter 配合 Spring Boot 依赖管理使用。
- Spring Data Redis 使用项目已有 Starter。

## 配置设计

`application.yaml` 导入项目根目录的 `.env`，并从中读取数据库、Redis 的主机、端口、库名、用户名和密码。开发默认值只覆盖主机和端口等非敏感信息；密码必须由 `.env` 或操作系统环境变量提供。

本机应用连接：

- MySQL：`localhost:3307/luckyhub`
- Redis：`localhost:6379`

Docker Compose 继续使用同一个 `.env`。`.env` 保持在 `.gitignore` 中，仓库只保存 `.env.example`。

## 验证设计

新增集成测试并加载完整 Spring 容器：

- 通过 Spring JDBC 执行 `SELECT 1`，断言返回 `1`。
- 通过 `StringRedisTemplate` 执行 `PING`，断言返回 `PONG`。

测试前要求 Docker Compose 中的 MySQL 和 Redis 为 healthy。测试必须通过项目 Java 17 Maven 脚本执行。

## 本次不包含

- 不创建业务表、实体类、Mapper 或 Service。
- 不加入代码生成器、分页插件或复杂 MyBatis-Plus 全局配置。
- 不修改系统级 `JAVA_HOME`。
