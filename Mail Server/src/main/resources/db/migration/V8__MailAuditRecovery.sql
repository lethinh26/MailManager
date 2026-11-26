-- Preserve all V7 intents and prior audit rows. Only delivery scheduling and
-- safe correlation metadata are added; no mail content or credentials.
ALTER TABLE mail_audit_outbox ADD COLUMN delivery_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE mail_audit_outbox ADD COLUMN next_attempt_at TIMESTAMP(6) NULL;
CREATE INDEX idx_mail_audit_outbox_due
    ON mail_audit_outbox(outcome, next_attempt_at, occurred_at, event_id);
ALTER TABLE audit_logs ADD COLUMN mail_event_id VARCHAR(36) NULL;
ALTER TABLE audit_logs ADD COLUMN mail_actor_id BIGINT NULL;
ALTER TABLE audit_logs ADD CONSTRAINT uk_audit_mail_event UNIQUE (mail_event_id);
