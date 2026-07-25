CREATE TABLE sys_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '加密密码',
    nickname VARCHAR(50) NULL COMMENT '昵称',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '用户状态：0-禁用，1-启用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_status (status),
    CONSTRAINT chk_sys_user_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户表';

CREATE TABLE sys_role (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_code VARCHAR(50) NOT NULL COMMENT '角色编码',
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_role_code (role_code),
    KEY idx_sys_role_status (status),
    CONSTRAINT chk_sys_role_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

CREATE TABLE sys_permission (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    permission_code VARCHAR(100) NOT NULL COMMENT '权限编码',
    permission_name VARCHAR(100) NOT NULL COMMENT '权限名称',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表';

CREATE TABLE sys_user_role (
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id),
    KEY idx_sys_user_role_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';

CREATE TABLE sys_role_permission (
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    permission_id BIGINT UNSIGNED NOT NULL COMMENT '权限ID',
    PRIMARY KEY (role_id, permission_id),
    KEY idx_sys_role_permission_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';

CREATE TABLE marketing_prize (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    prize_name VARCHAR(100) NOT NULL COMMENT '奖品名称',
    prize_type VARCHAR(30) NOT NULL COMMENT '奖品类型',
    prize_level VARCHAR(30) NOT NULL COMMENT '奖品等级',
    image_url VARCHAR(500) NULL COMMENT '图片地址',
    description VARCHAR(500) NULL COMMENT '奖品说明',
    stackable TINYINT NOT NULL DEFAULT 0 COMMENT '是否可叠加：0-否，1-是',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    PRIMARY KEY (id),
    KEY idx_marketing_prize_type (prize_type),
    KEY idx_marketing_prize_status (status),
    CONSTRAINT chk_marketing_prize_stackable CHECK (stackable IN (0, 1)),
    CONSTRAINT chk_marketing_prize_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='奖品表';

CREATE TABLE marketing_activity (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    activity_name VARCHAR(100) NOT NULL COMMENT '活动名称',
    description VARCHAR(1000) NULL COMMENT '活动说明',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' COMMENT '活动状态',
    start_time DATETIME(3) NOT NULL COMMENT '开始时间',
    end_time DATETIME(3) NOT NULL COMMENT '结束时间',
    daily_limit INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '用户每日参与次数上限',
    created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    PRIMARY KEY (id),
    KEY idx_marketing_activity_status (status),
    KEY idx_marketing_activity_start_time (start_time),
    KEY idx_marketing_activity_end_time (end_time),
    KEY idx_marketing_activity_name (activity_name),
    KEY idx_marketing_activity_created_by (created_by),
    CONSTRAINT chk_marketing_activity_time CHECK (end_time > start_time),
    CONSTRAINT chk_marketing_activity_daily_limit CHECK (daily_limit > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='营销活动表';

CREATE TABLE marketing_activity_prize (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    activity_id BIGINT UNSIGNED NOT NULL COMMENT '活动ID',
    prize_id BIGINT UNSIGNED NOT NULL COMMENT '奖品ID',
    weight INT UNSIGNED NOT NULL COMMENT '中奖权重',
    total_stock INT UNSIGNED NOT NULL COMMENT '总库存',
    remaining_stock INT UNSIGNED NOT NULL COMMENT '数据库剩余库存',
    sort_order INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '展示顺序',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_prize (activity_id, prize_id),
    KEY idx_activity_prize_prize_id (prize_id),
    CONSTRAINT chk_activity_prize_weight CHECK (weight > 0),
    CONSTRAINT chk_activity_prize_stock CHECK (remaining_stock <= total_stock)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动奖品表';

CREATE TABLE lottery_draw_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    request_id VARCHAR(64) NOT NULL COMMENT '请求唯一标识',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    activity_id BIGINT UNSIGNED NOT NULL COMMENT '活动ID',
    draw_count INT UNSIGNED NOT NULL COMMENT '抽奖次数',
    status VARCHAR(30) NOT NULL DEFAULT 'PROCESSING' COMMENT '订单状态',
    fail_reason VARCHAR(500) NULL COMMENT '失败原因',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    completed_at DATETIME(3) NULL COMMENT '完成时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lottery_draw_order_request_id (request_id),
    KEY idx_draw_order_user_id (user_id),
    KEY idx_draw_order_activity_id (activity_id),
    KEY idx_draw_order_status (status),
    KEY idx_draw_order_created_at (created_at),
    CONSTRAINT chk_draw_order_count CHECK (draw_count IN (1, 10)),
    CONSTRAINT chk_draw_order_status CHECK (status IN ('PROCESSING', 'SUCCESS', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽奖订单表';

CREATE TABLE lottery_draw_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '抽奖订单ID',
    request_id VARCHAR(64) NOT NULL COMMENT '请求ID',
    sequence_no INT UNSIGNED NOT NULL COMMENT '结果序号',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    activity_id BIGINT UNSIGNED NOT NULL COMMENT '活动ID',
    prize_id BIGINT UNSIGNED NOT NULL COMMENT '奖品ID',
    draw_time DATETIME(3) NOT NULL COMMENT '抽奖时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_draw_record_request_sequence (request_id, sequence_no),
    KEY idx_draw_record_order_id (order_id),
    KEY idx_draw_record_user_id (user_id),
    KEY idx_draw_record_activity_id (activity_id),
    KEY idx_draw_record_prize_id (prize_id),
    KEY idx_draw_record_draw_time (draw_time),
    CONSTRAINT chk_draw_record_sequence CHECK (sequence_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='中奖记录表';

CREATE TABLE user_benefit (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    prize_id BIGINT UNSIGNED NOT NULL COMMENT '奖品ID',
    quantity INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '数量',
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE' COMMENT '权益状态',
    obtained_at DATETIME(3) NOT NULL COMMENT '获得时间',
    expire_at DATETIME(3) NULL COMMENT '过期时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    PRIMARY KEY (id),
    KEY idx_user_benefit_user_id (user_id),
    KEY idx_user_benefit_prize_id (prize_id),
    KEY idx_user_benefit_status (status),
    KEY idx_user_benefit_obtained_at (obtained_at),
    CONSTRAINT chk_user_benefit_quantity CHECK (quantity > 0),
    CONSTRAINT chk_user_benefit_expire_time CHECK (expire_at IS NULL OR expire_at > obtained_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户权益表';
