INSERT INTO sys_role (role_code, role_name, status)
SELECT 'ADMIN', '系统管理员', 1
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_role
    WHERE role_code = 'ADMIN'
);

INSERT INTO sys_permission (permission_code, permission_name)
SELECT seed.permission_code, seed.permission_name
FROM (
    SELECT 'user:create' AS permission_code, '创建用户' AS permission_name
    UNION ALL SELECT 'user:read', '查询用户'
    UNION ALL SELECT 'role:create', '创建角色'
    UNION ALL SELECT 'role:read', '查询角色'
    UNION ALL SELECT 'permission:create', '创建权限'
    UNION ALL SELECT 'permission:read', '查询权限'
    UNION ALL SELECT 'user-role:assign', '分配用户角色'
    UNION ALL SELECT 'user-role:read', '查询用户角色'
    UNION ALL SELECT 'role-permission:assign', '分配角色权限'
    UNION ALL SELECT 'role-permission:read', '查询角色权限'
    UNION ALL SELECT 'user-permission:read', '查询用户最终权限'
) AS seed
LEFT JOIN sys_permission AS existing_permission
    ON existing_permission.permission_code = seed.permission_code
WHERE existing_permission.id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT admin_role.id, base_permission.id
FROM sys_role AS admin_role
JOIN sys_permission AS base_permission
    ON base_permission.permission_code IN (
        'user:create',
        'user:read',
        'role:create',
        'role:read',
        'permission:create',
        'permission:read',
        'user-role:assign',
        'user-role:read',
        'role-permission:assign',
        'role-permission:read',
        'user-permission:read'
    )
LEFT JOIN sys_role_permission AS existing_relation
    ON existing_relation.role_id = admin_role.id
    AND existing_relation.permission_id = base_permission.id
WHERE admin_role.role_code = 'ADMIN'
  AND existing_relation.role_id IS NULL;
