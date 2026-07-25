# ADMIN Base Permissions Implementation Plan

> **For agentic workers:** Execute inline and verify each database state transition. Do not create test classes.

**Goal:** Initialize the ADMIN role and all permissions required by the existing admin RBAC endpoints, then safely bind the existing `admin` user.

**Architecture:** A deterministic Flyway V2 seeds roles, permissions, and role-permission relations by business codes. User-role bootstrap remains a separate idempotent post-migration operation so a missing user cannot break a fresh database migration.

**Tech Stack:** Java 17, Spring Boot 4.1, Flyway, MySQL 8.4, Maven, Docker Compose

## Global Constraints

- Never modify `V1__create_luckyhub_schema.sql`.
- Never hard-code `role_id`, `permission_id`, or `user_id`.
- Do not create physical foreign keys.
- Preserve existing role and permission rows.
- Do not create test classes.

---

### Task 1: Create the deterministic V2 migration

**Files:**
- Create: `src/main/resources/db/migration/V2__initialize_admin_role_and_base_permissions.sql`

- [x] Insert ADMIN only when `role_code = 'ADMIN'` is absent.
- [x] Insert each of the 11 permissions only when its code is absent.
- [x] Insert only missing ADMIN-permission relations using IDs selected by business codes.
- [x] Compile with `pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-Maven.ps1 -DskipTests compile`.

### Task 2: Apply and verify the migration

**Files:**
- No additional files.

- [x] Start the Spring Boot application and confirm Flyway applies V2.
- [x] Query `flyway_schema_history` and confirm V2 succeeded.
- [x] Query ADMIN permissions and confirm all 11 expected codes are present.

### Task 3: Bind the existing administrator safely

**Files:**
- No additional files.

- [x] Run an idempotent `INSERT ... SELECT` matching `sys_user.username = 'admin'` and `sys_role.role_code = 'ADMIN'`.
- [x] Confirm the user-role relation exists exactly once.
- [x] Run the effective-permission join and confirm the administrator receives all 11 codes.
