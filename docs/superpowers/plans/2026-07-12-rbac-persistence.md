# LuckyHub RBAC Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Map the user, role, and permission tables with MyBatis-Plus and verify real CRUD behavior.

**Architecture:** Focused entity classes map one table each, thin `BaseMapper` interfaces provide persistence operations, and a shared MyBatis-Plus configuration supplies mapper scanning and audit timestamps.

**Tech Stack:** Java 17, Spring Boot 4.1.0, MyBatis-Plus 3.5.17, MySQL 8.4, JUnit 5

## Global Constraints

- Use MySQL auto-increment IDs.
- Never include password values in entity `toString()` output.
- Do not expose HTTP endpoints in this task.

---

### Task 1: Failing persistence contract tests

- [ ] Create transactional tests for user CRUD, role/permission queries, audit fields, and unique constraints.
- [ ] Run the test and confirm compilation fails because entity and Mapper types do not exist.

### Task 2: Entities and Mapper interfaces

- [ ] Create `SysUser`, `SysRole`, and `SysPermission` with exact table mappings.
- [ ] Create three `BaseMapper` interfaces.
- [ ] Run the persistence test and identify the remaining configuration requirement.

### Task 3: MyBatis-Plus configuration

- [ ] Add mapper scanning configuration.
- [ ] Add audit timestamp filling.
- [ ] Run the persistence tests and confirm all CRUD and constraint tests pass.

### Task 4: Regression verification

- [ ] Run the complete Java 17 Maven test suite.
- [ ] Confirm MySQL and Redis remain healthy.
- [ ] Verify transactional test data did not remain in the database.
