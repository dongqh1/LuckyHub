# LuckyHub Initial Database Schema Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create and verify the 11-table LuckyHub V1 schema through Flyway.

**Architecture:** Spring Boot runs Flyway against the existing Docker MySQL DataSource before JDBC-dependent beans and tests. A single immutable V1 migration defines tables, constraints, comments, and indexes; integration tests query `information_schema` and `flyway_schema_history`.

**Tech Stack:** Java 17, Spring Boot 4.1.0, Flyway, MySQL 8.4, JUnit 5, JdbcTemplate

## Global Constraints

- Use MySQL auto-increment `BIGINT UNSIGNED` primary keys.
- Do not create physical foreign keys.
- Do not insert initial business data.
- Keep `.env` ignored and do not expose credentials.

---

### Task 1: Schema contract tests

**Files:**
- Create: `src/test/java/com/dongqh/luckyhub/infrastructure/DatabaseSchemaMigrationTests.java`

- [ ] Add a test that reads `information_schema.tables` and asserts the exact 11 business table names.
- [ ] Add a test that reads `flyway_schema_history` and asserts V1 succeeded.
- [ ] Add a test that reads `information_schema.statistics` and asserts the 8 required unique index column sets.
- [ ] Run only `DatabaseSchemaMigrationTests` and confirm failure because V1 has not been installed.

### Task 2: Flyway V1 migration

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yaml`
- Create: `src/main/resources/db/migration/V1__create_luckyhub_schema.sql`

- [ ] Add `spring-boot-starter-flyway` and `org.flywaydb:flyway-mysql`.
- [ ] Enable Flyway, migration validation, and default migration location.
- [ ] Create the five RBAC tables and their indexes.
- [ ] Create the three marketing tables and their indexes.
- [ ] Create the two lottery tables and their indexes.
- [ ] Create the user benefit table and its indexes.
- [ ] Run `DatabaseSchemaMigrationTests` and confirm all schema assertions pass.

### Task 3: Repeatability and regression verification

**Files:**
- Verify: `src/main/resources/db/migration/V1__create_luckyhub_schema.sql`

- [ ] Run the full Maven test suite once and record zero failures.
- [ ] Run the full Maven test suite a second time and confirm Flyway reports the schema is up to date.
- [ ] Confirm Docker MySQL and Redis remain healthy.
- [ ] Inspect the final diff and report all schema decisions.
