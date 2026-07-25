# LuckyHub Database and Redis Connectivity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the Spring Boot application to Docker MySQL and Redis and prove both connections with integration tests.

**Architecture:** Import the gitignored root `.env` as a Spring property source so Compose and the Windows-hosted application share connection settings. Use the official MyBatis-Plus Spring Boot 4 starter for JDBC/MyBatis integration and the existing Spring Data Redis starter.

**Tech Stack:** Java 17, Spring Boot 4.1.0, MyBatis-Plus 3.5.17, MySQL 8.4, Redis 7.4, JUnit 5

## Global Constraints

- Do not modify system-level `JAVA_HOME`.
- Do not commit `.env` or expose real passwords in tracked files.
- Do not create business tables, entities, mappers, services, generators, or pagination configuration.

---

### Task 1: Failing connectivity tests

**Files:**
- Create: `src/test/java/com/dongqh/luckyhub/infrastructure/InfrastructureConnectivityTests.java`

**Interfaces:**
- Consumes: Spring `JdbcTemplate` and `StringRedisTemplate` beans.
- Produces: executable assertions for MySQL `SELECT 1` and Redis `PING`.

- [ ] Create two integration tests using constructor injection for `JdbcTemplate` and `StringRedisTemplate`.
- [ ] Run only `InfrastructureConnectivityTests` through `scripts/Invoke-Maven.ps1`.
- [ ] Confirm compilation fails because `JdbcTemplate` is unavailable before the MyBatis-Plus dependency is added.

### Task 2: Minimal dependency and connection configuration

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yaml`
- Modify: `.env.example`
- Modify: `.env`

**Interfaces:**
- Consumes: `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, and `REDIS_PASSWORD` properties.
- Produces: Spring `DataSource`, `JdbcTemplate`, `RedisConnectionFactory`, and `StringRedisTemplate` beans.

- [ ] Add `mybatis-plus-spring-boot4-starter:3.5.17` to `pom.xml`.
- [ ] Import optional `.env` as properties from `application.yaml`.
- [ ] Configure `spring.datasource` with the MySQL Connector/J URL and credentials.
- [ ] Configure `spring.data.redis` with host, port, password, timeout, and database 0.
- [ ] Add host variables to `.env.example` and `.env`.
- [ ] Run only `InfrastructureConnectivityTests` and confirm both tests pass.

### Task 3: Full verification

**Files:**
- Verify: `compose.yaml`
- Verify: `pom.xml`
- Verify: `src/main/resources/application.yaml`

- [ ] Confirm Compose configuration is valid and both services are healthy.
- [ ] Run the complete Maven test suite through Java 17.
- [ ] Inspect the final diff and confirm `.env` remains ignored.
