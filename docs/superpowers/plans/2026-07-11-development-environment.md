# LuckyHub Development Environment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configure a reproducible LuckyHub development environment using Java 17, Maven, MySQL 8, Redis, and Docker Compose.

**Architecture:** Keep both installed JDKs and select Java 17 only for project commands. Run MySQL and Redis as Compose services while running the Spring Boot application from Windows.

**Tech Stack:** Java 17, Maven 3.9.2, Docker Desktop, Docker Compose, MySQL 8, Redis

## Global Constraints

- Do not modify the system-level `JAVA_HOME`.
- Use `C:\Program Files\Java\jdk-17.0.5` for LuckyHub Maven commands.
- Do not overwrite existing user changes.

---

### Task 1: Host tooling

- [ ] Verify Java 17 and Maven installations.
- [ ] Install Docker Desktop and enable the WSL 2 engine if required.
- [ ] Verify `docker version` and `docker compose version`.

### Task 2: Project-local environment

- [ ] Add a PowerShell helper that sets Java 17 only for its child Maven process.
- [ ] Add a local environment example containing MySQL and Redis settings.
- [ ] Add Compose services for MySQL 8 and Redis with health checks and persistent volumes.

### Task 3: Verification

- [ ] Confirm Maven reports Java 17 through the project helper.
- [ ] Start the Compose services.
- [ ] Confirm MySQL is healthy and Redis returns `PONG`.
- [ ] Report every host and project change made.
