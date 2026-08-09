CREATE TABLE sim_coupon_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, fulfillment_no VARCHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL, request_payload JSON NOT NULL,
    external_reference VARCHAR(100) NOT NULL, status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_sim_coupon_fulfillment (fulfillment_no), UNIQUE KEY uk_sim_coupon_reference (external_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠券供应商模拟记录';

CREATE TABLE sim_points_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, fulfillment_no VARCHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL, request_payload JSON NOT NULL,
    external_reference VARCHAR(100) NOT NULL, status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_sim_points_fulfillment (fulfillment_no), UNIQUE KEY uk_sim_points_reference (external_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='积分供应商模拟记录';

CREATE TABLE sim_membership_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, fulfillment_no VARCHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL, request_payload JSON NOT NULL,
    external_reference VARCHAR(100) NOT NULL, status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_sim_membership_fulfillment (fulfillment_no), UNIQUE KEY uk_sim_membership_reference (external_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员供应商模拟记录';

CREATE TABLE sim_logistics_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, fulfillment_no VARCHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL, request_payload JSON NOT NULL,
    external_reference VARCHAR(100) NOT NULL, status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_sim_logistics_fulfillment (fulfillment_no), UNIQUE KEY uk_sim_logistics_reference (external_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物流供应商模拟记录';

CREATE TABLE sim_failure_rule (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    fulfillment_type VARCHAR(20) NOT NULL, failure_mode VARCHAR(30) NOT NULL,
    remaining_count INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_sim_failure_type (fulfillment_type),
    CONSTRAINT chk_sim_failure_type CHECK (fulfillment_type IN ('COUPON','POINTS','MEMBERSHIP','LOGISTICS')),
    CONSTRAINT chk_sim_failure_mode CHECK (failure_mode IN ('SUCCESS','RETRYABLE','PERMANENT','UNKNOWN_BEFORE','UNKNOWN_AFTER_SUCCESS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='本地供应商失败注入规则';
