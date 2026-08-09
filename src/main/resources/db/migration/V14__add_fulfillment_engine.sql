CREATE TABLE fulfillment_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    fulfillment_no VARCHAR(64) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id VARCHAR(100) NOT NULL,
    fulfillment_type VARCHAR(20) NOT NULL,
    target_user_id BIGINT UNSIGNED NOT NULL,
    request_payload JSON NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    max_attempts INT UNSIGNED NOT NULL DEFAULT 5,
    next_attempt_at DATETIME(3) NULL,
    lease_token VARCHAR(64) NULL,
    lease_until DATETIME(3) NULL,
    external_reference VARCHAR(100) NULL,
    last_error_category VARCHAR(30) NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(500) NULL,
    completed_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_fulfillment_task_no (fulfillment_no),
    KEY idx_fulfillment_task_due (status, next_attempt_at, id),
    KEY idx_fulfillment_task_lease (status, lease_until, id),
    KEY idx_fulfillment_task_source (source_type, source_id),
    CONSTRAINT chk_fulfillment_task_type CHECK (fulfillment_type IN ('COUPON','POINTS','MEMBERSHIP','LOGISTICS')),
    CONSTRAINT chk_fulfillment_task_status CHECK (status IN ('PENDING','PROCESSING','RETRY_WAITING','RECONCILING','SUCCEEDED','QUARANTINED','TERMINATED')),
    CONSTRAINT chk_fulfillment_task_attempts CHECK (max_attempts BETWEEN 1 AND 100 AND attempt_count <= max_attempts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一履约任务';

CREATE TABLE fulfillment_attempt (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_id BIGINT UNSIGNED NOT NULL,
    fulfillment_no VARCHAR(64) NOT NULL,
    sequence_no INT UNSIGNED NOT NULL,
    operation VARCHAR(20) NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    started_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) NOT NULL,
    duration_ms BIGINT UNSIGNED NOT NULL,
    external_reference VARCHAR(100) NULL,
    error_category VARCHAR(30) NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_fulfillment_attempt_sequence (task_id, sequence_no),
    KEY idx_fulfillment_attempt_no (fulfillment_no, id),
    CONSTRAINT chk_fulfillment_attempt_operation CHECK (operation IN ('EXECUTE','QUERY')),
    CONSTRAINT chk_fulfillment_attempt_outcome CHECK (outcome IN ('SUCCEEDED','RETRYABLE_FAILURE','PERMANENT_FAILURE','UNKNOWN','NOT_FOUND'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='履约调用尝试记录';

CREATE TABLE fulfillment_quarantine (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_id BIGINT UNSIGNED NOT NULL,
    fulfillment_no VARCHAR(64) NOT NULL,
    reason VARCHAR(100) NOT NULL,
    error_category VARCHAR(30) NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    quarantined_at DATETIME(3) NOT NULL,
    resolved_at DATETIME(3) NULL,
    resolution VARCHAR(30) NULL,
    resolved_by BIGINT UNSIGNED NULL,
    resolution_note VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_fulfillment_quarantine_task (task_id),
    UNIQUE KEY uk_fulfillment_quarantine_no (fulfillment_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='履约隔离区';

INSERT INTO sys_permission (permission_code, permission_name)
SELECT seed.permission_code, seed.permission_name FROM (
    SELECT 'fulfillment:create' permission_code, '创建履约任务' permission_name
    UNION ALL SELECT 'fulfillment:read', '查询履约任务'
    UNION ALL SELECT 'fulfillment:operate', '重试或终止履约任务'
    UNION ALL SELECT 'simulator:control', '控制本地供应商模拟器'
) seed LEFT JOIN sys_permission p ON p.permission_code = seed.permission_code
WHERE p.id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
  'fulfillment:create','fulfillment:read','fulfillment:operate','simulator:control')
LEFT JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.permission_id = p.id
WHERE r.role_code = 'ADMIN' AND rp.role_id IS NULL;
