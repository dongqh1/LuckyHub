# LuckyHub Common Web Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the reusable REST response, exception, validation, request tracing, OpenAPI, and module directory foundation.

**Architecture:** Small common packages expose stable response and error interfaces. A request filter establishes correlation metadata, while one `@RestControllerAdvice` translates framework, infrastructure, business, and unknown failures into safe error responses.

**Tech Stack:** Java 17, Spring Boot 4.1.0, Jakarta Bean Validation, SpringDoc OpenAPI 3.0.3, JUnit 5, MockMvc

## Global Constraints

- Success body contains only `code`, `message`, and `data`.
- Failure body also contains `timestamp` and `requestId`.
- Never expose stack traces or secret values to clients.
- Do not implement business modules in this task.

---

### Task 1: Response and error contracts

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/common/result/ApiResponse.java`
- Create: `src/main/java/com/dongqh/luckyhub/common/result/ErrorResponse.java`
- Create: `src/main/java/com/dongqh/luckyhub/common/enums/ErrorCode.java`
- Create: `src/main/java/com/dongqh/luckyhub/common/enums/CommonErrorCode.java`
- Test: `src/test/java/com/dongqh/luckyhub/common/result/ApiResponseTests.java`

- [ ] Write failing response contract tests.
- [ ] Implement immutable response records and error code contracts.
- [ ] Run response tests and confirm green.

### Task 2: Request ID propagation

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/common/web/RequestIdFilter.java`
- Create: `src/main/java/com/dongqh/luckyhub/common/web/RequestIdSupport.java`
- Test: `src/test/java/com/dongqh/luckyhub/common/web/RequestIdFilterTests.java`

- [ ] Write failing generation, propagation, invalid-input, and MDC cleanup tests.
- [ ] Implement the filter and support constants.
- [ ] Run filter tests and confirm green.

### Task 3: Exception translation and validation

**Files:**
- Create: common exception classes under `common/exception`.
- Create: `src/main/java/com/dongqh/luckyhub/common/web/GlobalExceptionHandler.java`
- Modify: `pom.xml`
- Test: `src/test/java/com/dongqh/luckyhub/common/web/GlobalExceptionHandlerTests.java`

- [ ] Write failing MockMvc tests using a test-only controller.
- [ ] Add the validation starter.
- [ ] Implement exception classes and the global handler.
- [ ] Run handler tests and confirm green.

### Task 4: OpenAPI and module directories

**Files:**
- Create: `src/main/java/com/dongqh/luckyhub/config/OpenApiConfig.java`
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yaml`
- Create: documented module package directories.
- Test: `src/test/java/com/dongqh/luckyhub/config/OpenApiConfigTests.java`

- [ ] Write a failing OpenAPI endpoint and metadata test.
- [ ] Add SpringDoc 3.0.3 and OpenAPI configuration.
- [ ] Create the complete module directory tree.
- [ ] Run OpenAPI test and confirm green.

### Task 5: Regression verification

- [ ] Run all tests with Java 17.
- [ ] Confirm Docker MySQL and Redis remain healthy.
- [ ] Inspect the complete file tree and diff.
