CREATE TABLE product (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    product_type VARCHAR(20) NOT NULL,
    image_url VARCHAR(500) NULL,
    description VARCHAR(1000) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_code (product_code),
    KEY idx_product_status_created (status, created_at),
    CONSTRAINT chk_product_type CHECK (product_type IN ('PHYSICAL', 'VIRTUAL')),
    CONSTRAINT chk_product_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品';

CREATE TABLE product_sku (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id BIGINT UNSIGNED NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    sku_name VARCHAR(100) NOT NULL,
    cash_price_cent BIGINT UNSIGNED NULL,
    points_price BIGINT UNSIGNED NULL,
    cash_enabled TINYINT NOT NULL DEFAULT 0,
    points_enabled TINYINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_sku_code (sku_code),
    KEY idx_product_sku_product (product_id, status),
    CONSTRAINT chk_product_sku_cash CHECK (
        (cash_enabled = 0) OR (cash_enabled = 1 AND cash_price_cent IS NOT NULL)
    ),
    CONSTRAINT chk_product_sku_points CHECK (
        (points_enabled = 0) OR (points_enabled = 1 AND points_price IS NOT NULL)
    ),
    CONSTRAINT chk_product_sku_enabled CHECK (
        cash_enabled IN (0, 1) AND points_enabled IN (0, 1)
    ),
    CONSTRAINT chk_product_sku_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品SKU';

CREATE TABLE reward_definition (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reward_code VARCHAR(64) NOT NULL,
    reward_name VARCHAR(100) NOT NULL,
    reward_type VARCHAR(30) NOT NULL,
    target_id BIGINT UNSIGNED NULL,
    quantity BIGINT UNSIGNED NOT NULL,
    config_snapshot JSON NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_reward_definition_code (reward_code),
    KEY idx_reward_definition_type_status (reward_type, status),
    CONSTRAINT chk_reward_definition_type CHECK (
        reward_type IN ('PRODUCT', 'COUPON', 'POINTS', 'MEMBERSHIP', 'DRAW_CHANCE')
    ),
    CONSTRAINT chk_reward_definition_quantity CHECK (quantity > 0),
    CONSTRAINT chk_reward_definition_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一奖励定义';

ALTER TABLE marketing_prize
    ADD COLUMN reward_definition_id BIGINT UNSIGNED NULL AFTER prize_type,
    ADD KEY idx_marketing_prize_reward_definition (reward_definition_id);

INSERT INTO sys_permission (permission_code, permission_name)
SELECT seed.permission_code, seed.permission_name
FROM (
    SELECT 'catalog:read' AS permission_code, '查询商城商品' AS permission_name
    UNION ALL SELECT 'catalog:manage', '管理商城商品'
    UNION ALL SELECT 'reward:manage', '管理统一奖励定义'
    UNION ALL SELECT 'inventory:manage', '管理渠道库存'
) seed
LEFT JOIN sys_permission existing_permission
    ON existing_permission.permission_code = seed.permission_code
WHERE existing_permission.id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role_record.id, permission.id
FROM sys_role role_record
JOIN sys_permission permission ON permission.permission_code = 'catalog:read'
LEFT JOIN sys_role_permission existing_relation
    ON existing_relation.role_id = role_record.id
    AND existing_relation.permission_id = permission.id
WHERE role_record.role_code IN ('USER', 'ADMIN')
  AND existing_relation.role_id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT admin_role.id, permission.id
FROM sys_role admin_role
JOIN sys_permission permission ON permission.permission_code IN (
    'catalog:manage', 'reward:manage', 'inventory:manage'
)
LEFT JOIN sys_role_permission existing_relation
    ON existing_relation.role_id = admin_role.id
    AND existing_relation.permission_id = permission.id
WHERE admin_role.role_code = 'ADMIN'
  AND existing_relation.role_id IS NULL;
