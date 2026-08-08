CREATE TABLE points_account (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    balance BIGINT NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_points_account_user (user_id),
    CONSTRAINT chk_points_account_balance CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户积分账户';

CREATE TABLE points_ledger (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    business_type VARCHAR(30) NOT NULL,
    business_id VARCHAR(100) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    reversal_of_ledger_id BIGINT UNSIGNED NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_points_ledger_business (business_type, business_id),
    UNIQUE KEY uk_points_ledger_reversal (reversal_of_ledger_id),
    KEY idx_points_ledger_user_created (user_id, created_at, id),
    CONSTRAINT chk_points_ledger_business_type CHECK (
        business_type IN ('LOTTERY_REWARD', 'ORDER_REWARD', 'MEMBERSHIP_BONUS',
                          'REDEMPTION', 'REVERSAL', 'MANUAL_ADJUSTMENT')
    ),
    CONSTRAINT chk_points_ledger_direction CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_points_ledger_amount CHECK (amount > 0),
    CONSTRAINT chk_points_ledger_balance CHECK (balance_after >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变积分流水';

CREATE TABLE points_redemption_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    redemption_no VARCHAR(64) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    sku_id BIGINT UNSIGNED NOT NULL,
    quantity INT UNSIGNED NOT NULL,
    unit_points BIGINT NOT NULL,
    total_points BIGINT NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    sku_name VARCHAR(100) NOT NULL,
    product_type VARCHAR(20) NOT NULL,
    image_url VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL,
    reversal_no VARCHAR(64) NULL,
    failure_reason VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_points_redemption_no (redemption_no),
    UNIQUE KEY uk_points_redemption_reversal (reversal_no),
    KEY idx_points_redemption_user_created (user_id, created_at, id),
    CONSTRAINT chk_points_redemption_quantity CHECK (quantity > 0),
    CONSTRAINT chk_points_redemption_price CHECK (unit_points > 0 AND total_points > 0),
    CONSTRAINT chk_points_redemption_type CHECK (product_type IN ('PHYSICAL', 'VIRTUAL')),
    CONSTRAINT chk_points_redemption_status CHECK (
        status IN ('PROCESSING', 'COMPLETED', 'REVERSED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='积分兑换单';

ALTER TABLE inventory_reservation
    DROP CHECK chk_inventory_reservation_status,
    ADD CONSTRAINT chk_inventory_reservation_status CHECK (
        status IN ('RESERVED', 'CONFIRMED', 'RELEASED', 'REVERSED')
    );

ALTER TABLE inventory_ledger
    DROP CHECK chk_inventory_ledger_operation,
    ADD CONSTRAINT chk_inventory_ledger_operation CHECK (
        operation IN ('INITIALIZE', 'ALLOCATE', 'RESERVE', 'CONFIRM', 'RELEASE', 'RETURN')
    );

INSERT INTO sys_permission (permission_code, permission_name)
SELECT seed.permission_code, seed.permission_name
FROM (
    SELECT 'points:read' AS permission_code, '查询本人积分与兑换记录' AS permission_name
    UNION ALL SELECT 'points:redeem', '创建积分兑换'
    UNION ALL SELECT 'points:adjust', '管理积分调整与兑换冲正'
) seed
LEFT JOIN sys_permission existing_permission
    ON existing_permission.permission_code = seed.permission_code
WHERE existing_permission.id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role_record.id, permission.id
FROM sys_role role_record
JOIN sys_permission permission ON permission.permission_code IN ('points:read', 'points:redeem')
LEFT JOIN sys_role_permission existing_relation
    ON existing_relation.role_id = role_record.id
    AND existing_relation.permission_id = permission.id
WHERE role_record.role_code IN ('USER', 'ADMIN')
  AND existing_relation.role_id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT admin_role.id, permission.id
FROM sys_role admin_role
JOIN sys_permission permission ON permission.permission_code = 'points:adjust'
LEFT JOIN sys_role_permission existing_relation
    ON existing_relation.role_id = admin_role.id
    AND existing_relation.permission_id = permission.id
WHERE admin_role.role_code = 'ADMIN'
  AND existing_relation.role_id IS NULL;
