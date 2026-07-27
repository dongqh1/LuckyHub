INSERT INTO sys_permission (permission_code, permission_name)
SELECT seed.permission_code, seed.permission_name
FROM (
    SELECT 'prize:create' AS permission_code, '创建奖品' AS permission_name
    UNION ALL SELECT 'prize:read', '查询奖品'
    UNION ALL SELECT 'prize:update', '修改奖品'
    UNION ALL SELECT 'prize:disable', '禁用奖品'
    UNION ALL SELECT 'prize:image:upload', '上传奖品图片'
) AS seed
LEFT JOIN sys_permission AS existing_permission
    ON existing_permission.permission_code = seed.permission_code
WHERE existing_permission.id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT admin_role.id, prize_permission.id
FROM sys_role AS admin_role
JOIN sys_permission AS prize_permission
    ON prize_permission.permission_code IN (
        'prize:create',
        'prize:read',
        'prize:update',
        'prize:disable',
        'prize:image:upload'
    )
LEFT JOIN sys_role_permission AS existing_relation
    ON existing_relation.role_id = admin_role.id
    AND existing_relation.permission_id = prize_permission.id
WHERE admin_role.role_code = 'ADMIN'
  AND existing_relation.role_id IS NULL;
