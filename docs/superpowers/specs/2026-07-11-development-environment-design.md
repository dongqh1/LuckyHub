# LuckyHub 开发环境设计

## 目标

为 LuckyHub 第一阶段建立 Java 17、Maven、MySQL 8、Redis 与 Docker Compose 开发环境。

## 约束

- 保留系统中的 Java 8 和 Java 17，不修改系统级 `JAVA_HOME`。
- 项目构建命令仅在当前进程临时使用 `C:\Program Files\Java\jdk-17.0.5`。
- 使用现有 Maven 3.9.2。
- MySQL 8 和 Redis 使用 Docker Compose 运行，不安装为 Windows 服务。
- 数据库和 Redis 密码通过本地环境配置提供，不写入公开源码。

## 方案

安装 Docker Desktop 并启用可用的 WSL 2 后端。项目增加本地环境示例和 Compose 编排，启动 MySQL 8 与 Redis；应用仍由 IDE 或 Maven 在 Windows 上运行。验证包括 Maven 使用 Java 17、Docker 服务可用、MySQL 健康检查通过、Redis PING 成功。

## 边界

本次不实现业务代码、不设计数据库表、不添加消息队列，也不修改全局 Java 版本选择。
