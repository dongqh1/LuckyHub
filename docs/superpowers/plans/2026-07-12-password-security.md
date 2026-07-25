# LuckyHub Password Security Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reusable BCrypt password hashing and verification component.

**Architecture:** A configuration bean owns the BCrypt work factor, while a small service enforces input boundaries and hides encoder details from future user and authentication services.

**Tech Stack:** Java 17, Spring Security Crypto 7, BCrypt, JUnit 5

## Global Constraints

- Never store, return, or log plaintext passwords.
- Enforce BCrypt's 72-byte UTF-8 input limit.
- Do not enable HTTP security in this task.

---

### Task 1: Password contract tests

- [ ] Write tests for salt randomness, matching, work factor, invalid inputs, byte limit, and upgrade detection.
- [ ] Run tests and confirm compilation fails because the component does not exist.

### Task 2: BCrypt configuration and service

- [ ] Add `spring-security-crypto`.
- [ ] Configure BCrypt strength from `BCRYPT_STRENGTH`, defaulting to 12.
- [ ] Implement `PasswordService`.
- [ ] Run focused tests and confirm green.

### Task 3: Regression verification

- [ ] Run the complete Java 17 test suite.
- [ ] Confirm Docker services and existing Flyway/RBAC tests remain healthy.
- [ ] Scan production sources for plaintext test passwords.
