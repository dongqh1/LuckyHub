CREATE TABLE membership_product (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    membership_level VARCHAR(30) NOT NULL,
    card_type VARCHAR(20) NOT NULL,
    duration_days INT UNSIGNED NOT NULL,
    price_cent BIGINT NOT NULL,
    discount_basis_points INT UNSIGNED NOT NULL,
    daily_draw_bonus INT UNSIGNED NOT NULL DEFAULT 0,
    points_multiplier_basis_points INT UNSIGNED NOT NULL DEFAULT 10000,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_membership_product_code (product_code),
    CONSTRAINT chk_membership_card_type CHECK (card_type IN ('MONTH','QUARTER','YEAR')),
    CONSTRAINT chk_membership_product_values CHECK (duration_days > 0 AND price_cent >= 0),
    CONSTRAINT chk_membership_discount CHECK (discount_basis_points BETWEEN 1 AND 10000),
    CONSTRAINT chk_membership_multiplier CHECK (points_multiplier_basis_points >= 10000),
    CONSTRAINT chk_membership_product_status CHECK (status IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员产品';

CREATE TABLE user_membership (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    membership_level VARCHAR(30) NOT NULL,
    starts_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    discount_basis_points INT UNSIGNED NOT NULL,
    daily_draw_bonus INT UNSIGNED NOT NULL,
    points_multiplier_basis_points INT UNSIGNED NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_membership_user (user_id),
    CONSTRAINT chk_user_membership_period CHECK (expires_at > starts_at),
    CONSTRAINT chk_user_membership_discount CHECK (discount_basis_points BETWEEN 1 AND 10000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户会员';

CREATE TABLE membership_grant_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    business_no VARCHAR(64) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    membership_product_id BIGINT UNSIGNED NOT NULL,
    starts_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_membership_grant_business (business_no),
    KEY idx_membership_grant_user (user_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员发放幂等历史';
