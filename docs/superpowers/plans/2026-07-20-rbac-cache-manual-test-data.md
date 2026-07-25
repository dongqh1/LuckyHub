# RBAC Cache Manual Test Data Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 LuckyHub 的认证、RBAC 与 Redis 权限缓存手工验证准备可重复执行的角色、权限关系和用户角色关系。

**Architecture:** 不新增 Flyway 迁移，避免把仅用于本地验证的数据永久带入所有环境。使用一份幂等初始化 SQL 按业务编码和用户名查询主键并补齐缺失关系；另提供一份显式清理 SQL，按依赖顺序删除测试关系和测试角色。

**Tech Stack:** MySQL 8.4、Docker Compose、PowerShell 7、Redis 7.4

## Global Constraints

- 不修改 Flyway V1、V2。
- 不写死 `role_id`、`permission_id`、`user_id`。
- 不创建测试类。
- 保留 `admin` 已有的 `ADMIN` 角色和其他既有关系。
- 初始化脚本必须可重复执行，不重复插入角色或关联数据。
- 清理脚本只交付，不自动执行。

---

### Task 1: 创建幂等测试数据脚本

**Files:**
- Create: `scripts/dev/seed-rbac-cache-test-data.sql`

**Interfaces:**
- Consumes: `sys_user.username`、`sys_role.role_code`、`sys_permission.permission_code` 及 V2 已初始化的 11 个基础权限。
- Produces: `RBAC_VIEWER`、`USER_OPERATOR`、`RBAC_OPERATOR` 三个角色及其权限关系，并为 `player01` 和 `admin` 补充 `RBAC_VIEWER`。

- [x] **Step 1: 写入角色种子数据**

  使用 `INSERT ... SELECT ... LEFT JOIN ... WHERE id IS NULL`，只补充缺失的角色，不覆盖同编码既有角色。

- [x] **Step 2: 写入角色权限关系**

  通过 `role_code` 和 `permission_code` 联表取得主键；只补充缺失关系，不删除角色原有权限。

- [x] **Step 3: 写入用户角色关系**

  通过 `username` 和 `role_code` 联表取得主键；`player01` 或 `admin` 不存在时自然插入零行，不使脚本失败。

- [x] **Step 4: 在事务中提交数据**

  用 `START TRANSACTION` 与 `COMMIT` 保证本次脚本整体执行。

### Task 2: 创建显式清理脚本

**Files:**
- Create: `scripts/dev/cleanup-rbac-cache-test-data.sql`

**Interfaces:**
- Consumes: 三个测试角色编码。
- Produces: 删除测试角色的用户关联、权限关联和角色本身；不删除 V2 基础权限和业务用户。

- [x] **Step 1: 按关联依赖顺序删除数据**

  先按角色编码联表删除 `sys_user_role`、`sys_role_permission`，最后删除三个测试角色。

- [x] **Step 2: 添加醒目安全说明**

  注明该脚本会删除同编码角色及全部关联，只能在确认这些编码属于本地测试数据时手工执行。

### Task 3: 执行并验证初始化结果

**Files:**
- Verify: `scripts/dev/seed-rbac-cache-test-data.sql`

**Interfaces:**
- Consumes: `.env` 中的 MySQL、Redis 凭据和正在运行的 `luckyhub-mysql`、`luckyhub-redis` 容器。
- Produces: 当前开发数据库中的测试角色矩阵，以及被清除的 `admin`、`player01` 权限缓存键。

- [x] **Step 1: 检查容器健康状态**

  Run: `docker compose ps`

  Expected: MySQL、Redis 都处于运行状态，健康检查为 `healthy`。

- [x] **Step 2: 执行初始化脚本两次**

  将 UTF-8 SQL 通过标准输入交给 MySQL 容器；连续执行两次均应退出码为 0，用于验证幂等性。

- [x] **Step 3: 清除受影响用户的旧权限缓存**

  按用户名查询真实用户 ID，再删除 `rbac:permissions:user:{userId}`，不在命令中写死 ID。

- [x] **Step 4: 查询并核对角色权限矩阵**

  Expected:

  - `RBAC_VIEWER`：6 个只读权限。
  - `USER_OPERATOR`：5 个用户管理权限。
  - `RBAC_OPERATOR`：6 个角色/权限管理权限。
  - `player01` 拥有 `RBAC_VIEWER`。
  - `admin` 同时保留 `ADMIN` 并拥有 `RBAC_VIEWER`。

- [x] **Step 5: 核对重复数据计数**

  查询三个角色编码各自数量和关联主键重复情况，确认唯一索引下没有重复角色或重复关联。
