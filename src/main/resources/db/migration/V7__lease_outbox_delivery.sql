ALTER TABLE message_outbox
    DROP CHECK chk_message_outbox_status,
    ADD COLUMN claim_token VARCHAR(64) NULL COMMENT '当前投递租约令牌' AFTER last_error,
    ADD CONSTRAINT chk_message_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED'));
