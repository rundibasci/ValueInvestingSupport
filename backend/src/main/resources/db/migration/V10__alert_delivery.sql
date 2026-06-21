ALTER TABLE alert ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE alert ADD COLUMN delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE alert ADD COLUMN delivery_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE alert ADD COLUMN delivered_at TIMESTAMP;
ALTER TABLE alert ADD COLUMN delivery_error VARCHAR(500);

CREATE INDEX idx_alert_delivery_pending
    ON alert(priority, status, delivery_status);
