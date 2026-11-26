-- Separate journal: a failure inserting audit_logs must not erase evidence of
-- an external mail mutation. No FK cascade: pending events survive actor deletion.
CREATE TABLE mail_audit_outbox (
    event_id VARCHAR(36) PRIMARY KEY,
    actor_id BIGINT NULL,
    action VARCHAR(64) NOT NULL,
    detail VARCHAR(500) NULL,
    ip_address VARCHAR(64) NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    outcome VARCHAR(16) NOT NULL
);
CREATE INDEX idx_mail_audit_outbox_ready ON mail_audit_outbox(outcome, occurred_at, event_id);
