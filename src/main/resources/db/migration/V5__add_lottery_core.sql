ALTER TABLE marketing_activity
    ADD COLUMN no_win_weight INT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '独立未中奖权重' AFTER daily_limit;

ALTER TABLE lottery_draw_order
    ADD COLUMN draw_date DATE NULL COMMENT '上海时区抽奖日期' AFTER draw_count;

UPDATE lottery_draw_order
SET draw_date = DATE(created_at)
WHERE draw_date IS NULL;

ALTER TABLE lottery_draw_order
    MODIFY draw_date DATE NOT NULL COMMENT '上海时区抽奖日期';

ALTER TABLE lottery_draw_record
    ADD COLUMN result_type VARCHAR(20) NOT NULL DEFAULT 'WIN' AFTER activity_id,
    MODIFY prize_id BIGINT UNSIGNED NULL,
    ADD COLUMN prize_name VARCHAR(100) NULL AFTER prize_id,
    ADD COLUMN prize_type VARCHAR(30) NULL AFTER prize_name,
    ADD COLUMN prize_image_url VARCHAR(1000) NULL AFTER prize_type;

ALTER TABLE user_benefit
    ADD COLUMN draw_record_id BIGINT UNSIGNED NOT NULL AFTER id,
    ADD COLUMN prize_type VARCHAR(30) NOT NULL AFTER prize_id,
    ADD COLUMN grant_error VARCHAR(500) NULL AFTER status,
    ADD UNIQUE KEY uk_user_benefit_draw_record (draw_record_id);

CREATE TABLE message_outbox (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id VARCHAR(64) NOT NULL COMMENT '事件唯一标识',
    event_type VARCHAR(100) NOT NULL COMMENT '事件类型',
    event_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '事件版本',
    aggregate_type VARCHAR(100) NOT NULL COMMENT '聚合类型',
    aggregate_id VARCHAR(64) NOT NULL COMMENT '聚合标识',
    payload JSON NOT NULL COMMENT '事件载荷',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '投递状态',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_at DATETIME(3) NULL COMMENT '下次重试时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    sent_at DATETIME(3) NULL COMMENT '发送时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_outbox_event_id (event_id),
    KEY idx_message_outbox_status_retry (status, next_retry_at),
    CONSTRAINT chk_message_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可靠事件发件箱';

CREATE TABLE message_consume_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id VARCHAR(64) NOT NULL COMMENT '事件唯一标识',
    consumer_name VARCHAR(100) NOT NULL COMMENT '消费者名称',
    consumed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '消费时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_consume_event_consumer (event_id, consumer_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息消费幂等记录';

INSERT INTO sys_role (role_code, role_name, status)
SELECT 'USER', '普通用户', 1
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_role
    WHERE role_code = 'USER'
);

INSERT INTO sys_permission (permission_code, permission_name)
SELECT seed.permission_code, seed.permission_name
FROM (
    SELECT 'lottery:activity:read' AS permission_code, '查询可参与抽奖活动' AS permission_name
    UNION ALL SELECT 'lottery:draw', '参与抽奖'
    UNION ALL SELECT 'lottery:draw:read', '查询抽奖订单'
    UNION ALL SELECT 'lottery:record:read', '查询抽奖记录'
    UNION ALL SELECT 'benefit:read', '查询用户权益'
    UNION ALL SELECT 'lottery:order:read:all', '查询全部抽奖订单'
    UNION ALL SELECT 'lottery:draw:read:all', '查询全部抽奖详情'
    UNION ALL SELECT 'lottery:record:read:all', '查询全部抽奖记录'
    UNION ALL SELECT 'benefit:read:all', '查询全部用户权益'
) AS seed
LEFT JOIN sys_permission AS existing_permission
    ON existing_permission.permission_code = seed.permission_code
WHERE existing_permission.id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role_record.id, permission.id
FROM sys_role AS role_record
JOIN sys_permission AS permission
    ON permission.permission_code IN (
        'lottery:activity:read',
        'lottery:draw',
        'lottery:draw:read',
        'lottery:record:read',
        'benefit:read'
    )
LEFT JOIN sys_role_permission AS existing_relation
    ON existing_relation.role_id = role_record.id
    AND existing_relation.permission_id = permission.id
WHERE role_record.role_code IN ('USER', 'ADMIN')
  AND existing_relation.role_id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT admin_role.id, permission.id
FROM sys_role AS admin_role
JOIN sys_permission AS permission
    ON permission.permission_code IN (
        'lottery:order:read:all',
        'lottery:draw:read:all',
        'lottery:record:read:all',
        'benefit:read:all'
    )
LEFT JOIN sys_role_permission AS existing_relation
    ON existing_relation.role_id = admin_role.id
    AND existing_relation.permission_id = permission.id
WHERE admin_role.role_code = 'ADMIN'
  AND existing_relation.role_id IS NULL;

INSERT INTO sys_user_role (user_id, role_id)
SELECT user_record.id, user_role.id
FROM sys_user AS user_record
JOIN sys_role AS user_role
    ON user_role.role_code = 'USER'
LEFT JOIN sys_user_role AS existing_relation
    ON existing_relation.user_id = user_record.id
    AND existing_relation.role_id = user_role.id
WHERE existing_relation.user_id IS NULL;
