CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    created_at  TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    log_level   VARCHAR(10) NOT NULL,
    username    VARCHAR(255),
    message     TEXT NOT NULL
);

CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);
CREATE INDEX idx_audit_logs_username   ON audit_logs (username);
CREATE INDEX idx_audit_logs_log_level  ON audit_logs (log_level);