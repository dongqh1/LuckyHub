-- 危险操作：本脚本会删除以下三个角色及其全部用户、权限关联。
-- 仅在确认 RBAC_VIEWER、USER_OPERATOR、RBAC_OPERATOR 属于本地测试数据时手工执行。
-- 不删除用户，也不删除 Flyway V2 初始化的基础权限。

START TRANSACTION;

DELETE user_role
FROM sys_user_role AS user_role
JOIN sys_role AS test_role
    ON test_role.id = user_role.role_id
WHERE test_role.role_code IN ('RBAC_VIEWER', 'USER_OPERATOR', 'RBAC_OPERATOR');

DELETE role_permission
FROM sys_role_permission AS role_permission
JOIN sys_role AS test_role
    ON test_role.id = role_permission.role_id
WHERE test_role.role_code IN ('RBAC_VIEWER', 'USER_OPERATOR', 'RBAC_OPERATOR');

DELETE FROM sys_role
WHERE role_code IN ('RBAC_VIEWER', 'USER_OPERATOR', 'RBAC_OPERATOR');

COMMIT;
