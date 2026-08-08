CREATE TABLE mall_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    sku_id BIGINT UNSIGNED NOT NULL,
    quantity INT UNSIGNED NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    sku_name VARCHAR(100) NOT NULL,
    product_type VARCHAR(20) NOT NULL,
    image_url VARCHAR(500) NULL,
    unit_price_cent BIGINT NOT NULL,
    original_amount_cent BIGINT NOT NULL,
    membership_level VARCHAR(30) NULL,
    membership_discount_basis_points INT UNSIGNED NULL,
    membership_discount_cent BIGINT NOT NULL DEFAULT 0,
    amount_after_membership_cent BIGINT NOT NULL,
    user_coupon_id BIGINT UNSIGNED NULL,
    coupon_template_id BIGINT UNSIGNED NULL,
    coupon_name VARCHAR(100) NULL,
    coupon_discount_cent BIGINT NOT NULL DEFAULT 0,
    payable_amount_cent BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    payment_deadline DATETIME(3) NOT NULL,
    paid_at DATETIME(3) NULL,
    cancelled_at DATETIME(3) NULL,
    cancel_reason VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_order_no (order_no),
    KEY idx_mall_order_user_created (user_id, created_at, id),
    KEY idx_mall_order_timeout (status, payment_deadline, id),
    CONSTRAINT chk_mall_order_quantity CHECK (quantity BETWEEN 1 AND 100),
    CONSTRAINT chk_mall_order_type CHECK (product_type IN ('PHYSICAL','VIRTUAL')),
    CONSTRAINT chk_mall_order_amount CHECK (
      unit_price_cent > 0 AND original_amount_cent > 0 AND
      membership_discount_cent >= 0 AND amount_after_membership_cent >= 0 AND
      coupon_discount_cent >= 0 AND payable_amount_cent >= 0),
    CONSTRAINT chk_mall_order_status CHECK (status IN ('PENDING_PAYMENT','PAID','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='现金商城订单及价格快照';

CREATE TABLE payment_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    payment_no VARCHAR(64) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    amount_cent BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    callback_result VARCHAR(20) NULL,
    failure_reason VARCHAR(500) NULL,
    paid_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no),
    KEY idx_payment_order_no (order_no, id),
    CONSTRAINT chk_payment_amount CHECK (amount_cent >= 0),
    CONSTRAINT chk_payment_status CHECK (status IN ('PENDING','SUCCESS','FAILED')),
    CONSTRAINT chk_payment_result CHECK (callback_result IS NULL OR callback_result IN ('PROCESSING','SUCCESS','FAILURE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模拟支付单';

INSERT INTO sys_permission (permission_code, permission_name)
SELECT seed.permission_code, seed.permission_name FROM (
    SELECT 'coupon:read' permission_code, '查询本人优惠券' permission_name
    UNION ALL SELECT 'coupon:manage', '管理优惠券模板与发放'
    UNION ALL SELECT 'membership:read', '查询和购买会员'
    UNION ALL SELECT 'membership:manage', '管理会员产品'
    UNION ALL SELECT 'order:create', '创建现金订单'
    UNION ALL SELECT 'order:read', '查询本人现金订单'
    UNION ALL SELECT 'order:cancel', '取消本人现金订单'
    UNION ALL SELECT 'payment:create', '创建本人模拟支付单'
    UNION ALL SELECT 'payment:simulate', '模拟支付结果'
) seed LEFT JOIN sys_permission p ON p.permission_code = seed.permission_code
WHERE p.id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN (
 'coupon:read','membership:read','order:create','order:read','order:cancel','payment:create')
LEFT JOIN sys_role_permission rp ON rp.role_id=r.id AND rp.permission_id=p.id
WHERE r.role_code IN ('USER','ADMIN') AND rp.role_id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN (
 'coupon:manage','membership:manage','payment:simulate')
LEFT JOIN sys_role_permission rp ON rp.role_id=r.id AND rp.permission_id=p.id
WHERE r.role_code='ADMIN' AND rp.role_id IS NULL;
