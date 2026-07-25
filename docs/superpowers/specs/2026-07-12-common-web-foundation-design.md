# LuckyHub 通用 Web 基础设计

## 目标

建立所有后续 REST 接口复用的响应、错误码、异常处理、参数校验、请求追踪和 OpenAPI 基础，并建立总体设计文档建议的模块目录。

## 响应契约

成功响应仅包含 `code`、`message`、`data`，其中成功码固定为 0，消息固定为 `success`。失败响应包含 `code`、`message`、`data: null`、毫秒时间戳和 `requestId`。

HTTP 状态码保持标准语义：参数错误 400、未认证 401、无权限 403、资源不存在 404、数据冲突 409、系统异常 500、基础服务异常 503。

## 错误码

定义 `ErrorCode` 接口和 `CommonErrorCode` 枚举。通用基础提供系统错误、服务不可用、未认证、无权限、参数错误、JSON 格式错误、资源不存在和数据冲突。业务模块后续在规定号段内定义自己的枚举。

## 异常处理

`BusinessException` 保存错误码和安全的客户端消息。通用子类包括 `NotFoundException`、`UnauthorizedException`、`ForbiddenException`。全局处理器处理 Bean Validation、方法参数校验、JSON 解析、数据冲突、数据库/Redis访问和未知异常。

未知异常只返回通用系统消息，完整异常写服务端日志；日志不包含密码、Token 或数据库密码。

## 请求追踪

过滤器读取 `X-Request-Id`。合法值被沿用；缺失或不合法时生成 UUID。该值进入 MDC、请求属性和响应头。请求结束后清理 MDC。

## OpenAPI

使用 `springdoc-openapi-starter-webmvc-ui:3.0.3`。Swagger UI 位于 `/swagger-ui.html`，OpenAPI JSON 位于 `/v3/api-docs`。配置项目名称、版本和说明。

## 模块目录

创建 `common`、`auth`、`rbac`、`prize`、`activity`、`lottery`、`inventory`、`benefit`、`config` 及文档建议的子目录。当前只在 `common` 和 `config` 放置实现，其他目录保留给后续模块。

## 验证

- 单元测试验证成功和失败响应。
- Web 测试验证参数错误、JSON 错误、业务异常和未知异常。
- 过滤器测试验证 requestId 生成、透传和清理。
- OpenAPI 端点测试验证文档可访问并包含 LuckyHub 元数据。
- 原有 Flyway、MySQL 和 Redis 测试继续通过。

## 本次不包含

- 不实现认证、RBAC 或业务接口。
- 不定义活动、抽奖、库存和权益的具体业务错误码。
- 不增加示例生产接口。
