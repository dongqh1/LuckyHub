CREATE TABLE sku_inventory (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sku_id BIGINT UNSIGNED NOT NULL,
    total_stock INT UNSIGNED NOT NULL,
    allocated_stock INT UNSIGNED NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_inventory_sku (sku_id),
    CONSTRAINT chk_sku_inventory_allocation CHECK (allocated_stock <= total_stock)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SKU总库存';

CREATE TABLE inventory_channel_stock (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sku_id BIGINT UNSIGNED NOT NULL,
    channel_code VARCHAR(100) NOT NULL,
    allocated_stock INT UNSIGNED NOT NULL,
    available_stock INT UNSIGNED NOT NULL,
    reserved_stock INT UNSIGNED NOT NULL DEFAULT 0,
    consumed_stock INT UNSIGNED NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_stock_sku_channel (sku_id, channel_code),
    KEY idx_channel_stock_channel (channel_code),
    CONSTRAINT chk_channel_stock_balance CHECK (
        allocated_stock = available_stock + reserved_stock + consumed_stock
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SKU渠道库存';

CREATE TABLE inventory_reservation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reservation_no VARCHAR(64) NOT NULL,
    sku_id BIGINT UNSIGNED NOT NULL,
    channel_code VARCHAR(100) NOT NULL,
    quantity INT UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_reservation_no (reservation_no),
    KEY idx_inventory_reservation_sku_channel (sku_id, channel_code),
    CONSTRAINT chk_inventory_reservation_quantity CHECK (quantity > 0),
    CONSTRAINT chk_inventory_reservation_status CHECK (
        status IN ('RESERVED', 'CONFIRMED', 'RELEASED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道库存预占';

CREATE TABLE inventory_ledger (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    business_no VARCHAR(100) NOT NULL,
    sku_id BIGINT UNSIGNED NOT NULL,
    channel_code VARCHAR(100) NULL,
    operation VARCHAR(20) NOT NULL,
    quantity INT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_ledger_business_no (business_no),
    KEY idx_inventory_ledger_sku_created (sku_id, created_at),
    CONSTRAINT chk_inventory_ledger_quantity CHECK (quantity > 0),
    CONSTRAINT chk_inventory_ledger_operation CHECK (
        operation IN ('INITIALIZE', 'ALLOCATE', 'RESERVE', 'CONFIRM', 'RELEASE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变库存流水';
