ALTER TABLE message_outbox
    ADD COLUMN last_error VARCHAR(500) NULL COMMENT '最近一次投递失败的安全错误' AFTER retry_count;
