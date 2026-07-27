INSERT INTO sys_permission (permission_code, permission_name)
SELECT seed.permission_code, seed.permission_name
FROM (
    SELECT 'activity:create' AS permission_code, '创建活动' AS permission_name
    UNION ALL SELECT 'activity:read', '查询活动'
    UNION ALL SELECT 'activity:update', '修改活动'
    UNION ALL SELECT 'activity:publish', '发布活动'
    UNION ALL SELECT 'activity:disable', '禁用活动'
    UNION ALL SELECT 'activity:restore', '恢复活动'
    UNION ALL SELECT 'activity:prize:manage', '管理活动奖品'
) AS seed
LEFT JOIN sys_permission AS existing_permission
    ON existing_permission.permission_code = seed.permission_code
WHERE existing_permission.id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT admin_role.id, activity_permission.id
FROM sys_role AS admin_role
JOIN sys_permission AS activity_permission
    ON activity_permission.permission_code IN (
        'activity:create',
        'activity:read',
        'activity:update',
        'activity:publish',
        'activity:disable',
        'activity:restore',
        'activity:prize:manage'
    )
LEFT JOIN sys_role_permission AS existing_relation
    ON existing_relation.role_id = admin_role.id
    AND existing_relation.permission_id = activity_permission.id
WHERE admin_role.role_code = 'ADMIN'
  AND existing_relation.role_id IS NULL;
