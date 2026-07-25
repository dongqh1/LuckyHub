-- LuckyHub RBAC 与 Redis 权限缓存手工验证数据。
-- 本脚本可重复执行：只补充缺失角色及关联，不覆盖或删除已有数据。

START TRANSACTION;

INSERT INTO sys_role (role_code, role_name, status)
SELECT seed.role_code, seed.role_name, 1
FROM (
    SELECT 'RBAC_VIEWER' AS role_code, 'RBAC只读员' AS role_name
    UNION ALL SELECT 'USER_OPERATOR', '用户运营员'
    UNION ALL SELECT 'RBAC_OPERATOR', 'RBAC操作员'
) AS seed
LEFT JOIN sys_role AS existing_role
    ON existing_role.role_code = seed.role_code
WHERE existing_role.id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT target_role.id, target_permission.id
FROM (
    SELECT 'RBAC_VIEWER' AS role_code, 'user:read' AS permission_code
    UNION ALL SELECT 'RBAC_VIEWER', 'role:read'
    UNION ALL SELECT 'RBAC_VIEWER', 'permission:read'
    UNION ALL SELECT 'RBAC_VIEWER', 'user-role:read'
    UNION ALL SELECT 'RBAC_VIEWER', 'role-permission:read'
    UNION ALL SELECT 'RBAC_VIEWER', 'user-permission:read'
    UNION ALL SELECT 'USER_OPERATOR', 'user:create'
    UNION ALL SELECT 'USER_OPERATOR', 'user:read'
    UNION ALL SELECT 'USER_OPERATOR', 'user-role:assign'
    UNION ALL SELECT 'USER_OPERATOR', 'user-role:read'
    UNION ALL SELECT 'USER_OPERATOR', 'user-permission:read'
    UNION ALL SELECT 'RBAC_OPERATOR', 'role:create'
    UNION ALL SELECT 'RBAC_OPERATOR', 'role:read'
    UNION ALL SELECT 'RBAC_OPERATOR', 'permission:create'
    UNION ALL SELECT 'RBAC_OPERATOR', 'permission:read'
    UNION ALL SELECT 'RBAC_OPERATOR', 'role-permission:assign'
    UNION ALL SELECT 'RBAC_OPERATOR', 'role-permission:read'
) AS seed
JOIN sys_role AS target_role
    ON target_role.role_code = seed.role_code
JOIN sys_permission AS target_permission
    ON target_permission.permission_code = seed.permission_code
LEFT JOIN sys_role_permission AS existing_relation
    ON existing_relation.role_id = target_role.id
    AND existing_relation.permission_id = target_permission.id
WHERE existing_relation.role_id IS NULL;

INSERT INTO sys_user_role (user_id, role_id)
SELECT target_user.id, target_role.id
FROM (
    SELECT 'player01' AS username, 'RBAC_VIEWER' AS role_code
    UNION ALL SELECT 'admin', 'RBAC_VIEWER'
) AS seed
JOIN sys_user AS target_user
    ON target_user.username = seed.username
JOIN sys_role AS target_role
    ON target_role.role_code = seed.role_code
LEFT JOIN sys_user_role AS existing_relation
    ON existing_relation.user_id = target_user.id
    AND existing_relation.role_id = target_role.id
WHERE existing_relation.user_id IS NULL;

COMMIT;
