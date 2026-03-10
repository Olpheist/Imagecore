ALTER TABLE audit_logs
    ADD COLUMN method VARCHAR(10),
    ADD COLUMN path TEXT,
    ADD COLUMN status INT,
    ADD COLUMN duration_ms INT,
    ADD COLUMN ip VARCHAR(45);

ALTER TABLE audit_logs
DROP COLUMN message;

CREATE INDEX idx_audit_logs_status ON audit_logs (status);
CREATE INDEX idx_audit_logs_path ON audit_logs (path);
CREATE INDEX idx_audit_logs_duration ON audit_logs (duration_ms);