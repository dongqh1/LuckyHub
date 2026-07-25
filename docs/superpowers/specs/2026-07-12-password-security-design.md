# LuckyHub 密码安全组件设计

## 目标

提供集中、可测试的 BCrypt 单向密码哈希能力，为后续用户创建和登录认证复用。

## 方案

- 只引入 `spring-security-crypto`，不启用完整 Spring Security 过滤链。
- 生产默认使用 BCrypt 工作因子 12，可由 `BCRYPT_STRENGTH` 调整。
- `PasswordService` 统一提供 `hash`、`matches` 和 `needsUpgrade`。
- 创建密码时拒绝 null、空白和超过 72 个 UTF-8 字节的输入。
- 验证时对无效输入返回 false，不把密码写入日志或异常消息。
- 哈希值直接保存到 `sys_user.password`，不保存明文和单独盐值；BCrypt 输出已包含盐和工作因子。

## 验证

- 相同密码两次哈希结果不同。
- 两份哈希均能匹配原密码。
- 错误密码不能匹配。
- 哈希不包含明文，且工作因子为 12。
- 72 字节允许，73 字节拒绝。
- 工作因子低于当前配置的旧哈希需要升级。

## 本次不包含

- 不创建用户、不修改数据库数据。
- 不实现登录、JWT、Redis Session 或 SecurityFilterChain。
