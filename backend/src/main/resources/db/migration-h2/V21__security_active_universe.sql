ALTER TABLE security ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX idx_security_active ON security(active);
