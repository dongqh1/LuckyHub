ALTER TABLE user_benefit
    ADD COLUMN claim_deadline DATETIME(3) NULL AFTER expire_at,
    ADD COLUMN claimed_at DATETIME(3) NULL AFTER claim_deadline,
    ADD COLUMN shipping_order_id BIGINT UNSIGNED NULL AFTER claimed_at,
    ADD KEY idx_user_benefit_claim_expiry (status, claim_deadline, id);

UPDATE user_benefit
SET claim_deadline = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 7 DAY)
WHERE status = 'CLAIM_PENDING' AND claim_deadline IS NULL;

ALTER TABLE mall_order
    ADD COLUMN address_snapshot_id BIGINT UNSIGNED NULL AFTER cancel_reason,
    ADD COLUMN shipping_order_id BIGINT UNSIGNED NULL AFTER address_snapshot_id,
    ADD KEY idx_mall_order_address_snapshot (address_snapshot_id),
    ADD KEY idx_mall_order_shipping_order (shipping_order_id);

ALTER TABLE points_redemption_order
    ADD COLUMN address_snapshot_id BIGINT UNSIGNED NULL AFTER failure_reason,
    ADD COLUMN shipping_order_id BIGINT UNSIGNED NULL AFTER address_snapshot_id,
    ADD KEY idx_points_redemption_address_snapshot (address_snapshot_id),
    ADD KEY idx_points_redemption_shipping_order (shipping_order_id);

ALTER TABLE inventory_ledger
    DROP CHECK chk_inventory_ledger_operation,
    ADD CONSTRAINT chk_inventory_ledger_operation CHECK (
        operation IN ('INITIALIZE', 'ALLOCATE', 'RESERVE', 'CONFIRM', 'RELEASE', 'RETURN', 'CLAIM_RETURN')
    );

CREATE TABLE user_shipping_address (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    receiver_ciphertext TEXT NOT NULL,
    phone_ciphertext TEXT NOT NULL,
    province_ciphertext TEXT NOT NULL,
    city_ciphertext TEXT NOT NULL,
    district_ciphertext TEXT NOT NULL,
    detail_ciphertext TEXT NOT NULL,
    receiver_masked VARCHAR(64) NOT NULL,
    phone_masked VARCHAR(32) NOT NULL,
    region_masked VARCHAR(200) NOT NULL,
    is_default TINYINT UNSIGNED NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_shipping_address_user_status (user_id, status, id),
    KEY idx_shipping_address_user_default (user_id, is_default, status, id),
    CONSTRAINT chk_shipping_address_default CHECK (is_default IN (0, 1)),
    CONSTRAINT chk_shipping_address_status CHECK (status IN ('ACTIVE', 'DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户加密收货地址簿';

CREATE TABLE shipping_address_snapshot (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    snapshot_no VARCHAR(64) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    address_id BIGINT UNSIGNED NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id VARCHAR(100) NOT NULL,
    receiver_ciphertext TEXT NOT NULL,
    phone_ciphertext TEXT NOT NULL,
    province_ciphertext TEXT NOT NULL,
    city_ciphertext TEXT NOT NULL,
    district_ciphertext TEXT NOT NULL,
    detail_ciphertext TEXT NOT NULL,
    receiver_masked VARCHAR(64) NOT NULL,
    phone_masked VARCHAR(32) NOT NULL,
    region_masked VARCHAR(200) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipping_snapshot_no (snapshot_no),
    UNIQUE KEY uk_shipping_snapshot_source (source_type, source_id),
    KEY idx_shipping_snapshot_user (user_id, id),
    CONSTRAINT chk_shipping_snapshot_source_type CHECK (
        source_type IN ('LOTTERY_BENEFIT', 'CASH_ORDER', 'POINTS_REDEMPTION')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物流加密地址快照';

CREATE TABLE shipping_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    shipping_no VARCHAR(64) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id VARCHAR(100) NOT NULL,
    target_user_id BIGINT UNSIGNED NOT NULL,
    address_snapshot_id BIGINT UNSIGNED NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    image_url VARCHAR(500) NULL,
    quantity INT UNSIGNED NOT NULL,
    fulfillment_no VARCHAR(64) NULL,
    claim_request_id VARCHAR(64) NULL,
    carrier_code VARCHAR(32) NULL,
    carrier_name VARCHAR(64) NULL,
    waybill_no VARCHAR(100) NULL,
    status VARCHAR(30) NOT NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(500) NULL,
    shipped_at DATETIME(3) NULL,
    delivered_at DATETIME(3) NULL,
    failed_at DATETIME(3) NULL,
    terminated_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipping_order_no (shipping_no),
    UNIQUE KEY uk_shipping_order_source (source_type, source_id),
    UNIQUE KEY uk_shipping_order_fulfillment (fulfillment_no),
    UNIQUE KEY uk_shipping_order_claim_request (claim_request_id),
    UNIQUE KEY uk_shipping_order_waybill (waybill_no),
    KEY idx_shipping_order_user_created (target_user_id, created_at, id),
    KEY idx_shipping_order_status_updated (status, updated_at, id),
    CONSTRAINT chk_shipping_order_source_type CHECK (
        source_type IN ('LOTTERY_BENEFIT', 'CASH_ORDER', 'POINTS_REDEMPTION')
    ),
    CONSTRAINT chk_shipping_order_status CHECK (
        status IN ('READY', 'FULFILLING', 'SHIPPED', 'IN_TRANSIT', 'DELIVERED', 'FAILED', 'TERMINATED')
    ),
    CONSTRAINT chk_shipping_order_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一实物发货单';

CREATE TABLE shipping_tracking_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    shipping_order_id BIGINT UNSIGNED NOT NULL,
    provider_event_id VARCHAR(100) NOT NULL,
    waybill_no VARCHAR(100) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    location_summary VARCHAR(200) NULL,
    description VARCHAR(500) NULL,
    event_time DATETIME(3) NOT NULL,
    received_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipping_tracking_provider_event (provider_event_id),
    KEY idx_shipping_tracking_order_event (shipping_order_id, event_time, id),
    KEY idx_shipping_tracking_waybill_event (waybill_no, event_time, id),
    CONSTRAINT chk_shipping_tracking_event_type CHECK (
        event_type IN ('PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物流轨迹事件';

CREATE TABLE shipping_callback_receipt (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    callback_id VARCHAR(100) NOT NULL,
    nonce_digest CHAR(64) NOT NULL,
    signature_digest CHAR(64) NOT NULL,
    waybill_no VARCHAR(100) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    event_time DATETIME(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    received_at DATETIME(3) NOT NULL,
    processed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipping_callback_id (callback_id),
    UNIQUE KEY uk_shipping_callback_nonce_digest (nonce_digest),
    KEY idx_shipping_callback_waybill_received (waybill_no, received_at, id),
    CONSTRAINT chk_shipping_callback_event_type CHECK (
        event_type IN ('PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED')
    ),
    CONSTRAINT chk_shipping_callback_status CHECK (status IN ('RECEIVED', 'PROCESSED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物流回调安全收据';

INSERT INTO sys_permission (permission_code, permission_name)
SELECT seed.permission_code, seed.permission_name
FROM (
    SELECT 'shipping:address:manage' AS permission_code, '管理本人收货地址' AS permission_name
    UNION ALL SELECT 'shipping:read', '查询本人物流信息'
    UNION ALL SELECT 'shipping:operate', '管理物流履约'
) seed
LEFT JOIN sys_permission existing_permission
    ON existing_permission.permission_code = seed.permission_code
WHERE existing_permission.id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role_record.id, permission.id
FROM sys_role role_record
JOIN sys_permission permission
    ON permission.permission_code IN ('shipping:address:manage', 'shipping:read')
LEFT JOIN sys_role_permission existing_relation
    ON existing_relation.role_id = role_record.id
    AND existing_relation.permission_id = permission.id
WHERE role_record.role_code IN ('USER', 'ADMIN')
  AND existing_relation.role_id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT admin_role.id, permission.id
FROM sys_role admin_role
JOIN sys_permission permission ON permission.permission_code = 'shipping:operate'
LEFT JOIN sys_role_permission existing_relation
    ON existing_relation.role_id = admin_role.id
    AND existing_relation.permission_id = permission.id
WHERE admin_role.role_code = 'ADMIN'
  AND existing_relation.role_id IS NULL;
