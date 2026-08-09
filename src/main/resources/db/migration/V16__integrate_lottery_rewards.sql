ALTER TABLE lottery_draw_record
    ADD COLUMN reward_definition_id BIGINT UNSIGNED NULL AFTER prize_image_url,
    ADD COLUMN reward_type VARCHAR(30) NULL AFTER reward_definition_id,
    ADD COLUMN reward_target_id BIGINT UNSIGNED NULL AFTER reward_type,
    ADD COLUMN reward_quantity BIGINT UNSIGNED NULL AFTER reward_target_id,
    ADD COLUMN reward_payload JSON NULL AFTER reward_quantity,
    ADD COLUMN reward_fingerprint CHAR(64) NULL AFTER reward_payload,
    ADD KEY idx_draw_record_reward_definition (reward_definition_id),
    ADD CONSTRAINT chk_draw_record_reward_type CHECK (
        reward_type IS NULL OR reward_type IN ('PRODUCT','COUPON','POINTS','MEMBERSHIP','DRAW_CHANCE')
    ),
    ADD CONSTRAINT chk_draw_record_reward_quantity CHECK (
        reward_quantity IS NULL OR reward_quantity > 0
    );

ALTER TABLE user_benefit
    ADD COLUMN reward_definition_id BIGINT UNSIGNED NULL AFTER prize_type,
    ADD COLUMN reward_type VARCHAR(30) NULL AFTER reward_definition_id,
    ADD COLUMN reward_target_id BIGINT UNSIGNED NULL AFTER reward_type,
    ADD COLUMN reward_quantity BIGINT UNSIGNED NULL AFTER reward_target_id,
    ADD COLUMN reward_payload JSON NULL AFTER reward_quantity,
    ADD COLUMN reward_fingerprint CHAR(64) NULL AFTER reward_payload,
    ADD COLUMN fulfillment_no VARCHAR(64) NULL AFTER reward_fingerprint,
    ADD KEY idx_user_benefit_reward_definition (reward_definition_id),
    ADD UNIQUE KEY uk_user_benefit_fulfillment (fulfillment_no),
    ADD CONSTRAINT chk_user_benefit_reward_type CHECK (
        reward_type IS NULL OR reward_type IN ('PRODUCT','COUPON','POINTS','MEMBERSHIP','DRAW_CHANCE')
    ),
    ADD CONSTRAINT chk_user_benefit_reward_quantity CHECK (
        reward_quantity IS NULL OR reward_quantity > 0
    );

CREATE TABLE draw_chance_account (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    available_balance BIGINT UNSIGNED NOT NULL DEFAULT 0,
    reserved_balance BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_draw_chance_account_user (user_id),
    CONSTRAINT chk_draw_chance_account_balances CHECK (
        available_balance >= 0 AND reserved_balance >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='奖励抽奖次数账户';

CREATE TABLE draw_chance_ledger (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    business_type VARCHAR(30) NOT NULL,
    business_id VARCHAR(100) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount BIGINT UNSIGNED NOT NULL,
    available_after BIGINT UNSIGNED NOT NULL,
    reserved_after BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_draw_chance_ledger_business (business_type, business_id),
    KEY idx_draw_chance_ledger_user_created (user_id, created_at, id),
    CONSTRAINT chk_draw_chance_ledger_business_type CHECK (
        business_type IN ('LOTTERY_REWARD','DRAW_CONSUME','DRAW_RELEASE','MANUAL_ADJUSTMENT')
    ),
    CONSTRAINT chk_draw_chance_ledger_direction CHECK (direction IN ('CREDIT','DEBIT')),
    CONSTRAINT chk_draw_chance_ledger_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='奖励抽奖次数不可变流水';

CREATE TABLE draw_chance_reservation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    activity_id BIGINT UNSIGNED NOT NULL,
    draw_date DATE NOT NULL,
    draw_count INT UNSIGNED NOT NULL,
    bonus_reserved BIGINT UNSIGNED NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    settled_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_draw_chance_reservation_request (request_id),
    KEY idx_draw_chance_reservation_user_date (user_id, draw_date, status, id),
    KEY idx_draw_chance_reservation_reconcile (status, created_at, id),
    CONSTRAINT chk_draw_chance_reservation_count CHECK (draw_count IN (1,10)),
    CONSTRAINT chk_draw_chance_reservation_status CHECK (
        status IN ('RESERVED','CONFIRMED','RELEASED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='奖励抽奖次数预留';

CREATE TABLE lottery_reward_quarantine (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    order_id BIGINT UNSIGNED NULL,
    draw_record_id BIGINT UNSIGNED NULL,
    benefit_id BIGINT UNSIGNED NULL,
    prize_id BIGINT UNSIGNED NULL,
    reward_definition_id BIGINT UNSIGNED NULL,
    reason_code VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    quarantined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    resolved_at DATETIME(3) NULL,
    resolved_by BIGINT UNSIGNED NULL,
    resolution_note VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_lottery_reward_quarantine_event (event_id),
    KEY idx_lottery_reward_quarantine_status (status, quarantined_at, id),
    CONSTRAINT chk_lottery_reward_quarantine_status CHECK (
        status IN ('OPEN','RESOLVED','IGNORED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽奖奖励事件隔离';
