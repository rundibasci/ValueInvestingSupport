CREATE TABLE analyst_estimate (
    id           BIGSERIAL PRIMARY KEY,
    security_id  UUID NOT NULL REFERENCES security(id),
    analyst_firm VARCHAR(100),
    target_price NUMERIC(19, 4),
    rating_label VARCHAR(10),
    target_date  DATE,
    created_at   TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_analyst_estimate_security_date
    ON analyst_estimate(security_id, target_date DESC);
