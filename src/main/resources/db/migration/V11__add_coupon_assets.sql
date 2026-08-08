CREATE TABLE coupon_template (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    coupon_type VARCHAR(20) NOT NULL,
    threshold_cent BIGINT NOT NULL DEFAULT 0,
    discount_cent BIGINT NOT NULL,
    applicable_product_id BIGINT UNSIGNED NULL,
    valid_from DATETIME(3) NOT NULL,
    valid_until DATETIME(3) NOT NULL,
    per_user_limit INT UNSIGNED NOT NULL DEFAULT 1,
    stackable_with_membership TINYINT(1) NOT NULL DEFAULT 1,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupon_template_code (template_code),
    CONSTRAINT chk_coupon_template_type CHECK (coupon_type IN ('NO_THRESHOLD','THRESHOLD')),
    CONSTRAINT chk_coupon_template_amount CHECK (threshold_cent >= 0 AND discount_cent > 0),
    CONSTRAINT chk_coupon_template_period CHECK (valid_until > valid_from),
    CONSTRAINT chk_coupon_template_limit CHECK (per_user_limit > 0),
    CONSTRAINT chk_coupon_template_status CHECK (status IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠券模板';

CREATE TABLE user_coupon (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    coupon_no VARCHAR(64) NOT NULL,
    template_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL,
    valid_from DATETIME(3) NOT NULL,
    valid_until DATETIME(3) NOT NULL,
    locked_order_no VARCHAR(64) NULL,
    used_order_no VARCHAR(64) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_coupon_no (coupon_no),
    KEY idx_user_coupon_user_status (user_id, status, valid_until, id),
    CONSTRAINT chk_user_coupon_status CHECK (status IN ('AVAILABLE','LOCKED','USED','EXPIRED')),
    CONSTRAINT chk_user_coupon_period CHECK (valid_until > valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户优惠券';

CREATE TABLE coupon_issue_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    business_no VARCHAR(64) NOT NULL,
    template_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    user_coupon_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupon_issue_business (business_no),
    KEY idx_coupon_issue_user_template (user_id, template_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠券发放幂等记录';
