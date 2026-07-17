ALTER TABLE fundamental_snapshot ADD COLUMN pretax_income DECIMAL(20,2);
ALTER TABLE fundamental_snapshot ADD COLUMN income_tax_expense DECIMAL(20,2);

CREATE TABLE roic_observation (
    id UUID DEFAULT RANDOM_UUID() NOT NULL,
    security_id UUID NOT NULL,
    fiscal_year INT NOT NULL,
    observation_date DATE,
    roic DECIMAL(16,8),
    source VARCHAR(30) NOT NULL,
    input_provider VARCHAR(40),
    formula_note VARCHAR(500) NOT NULL,
    unavailable_reason VARCHAR(80),
    PRIMARY KEY (id),
    CONSTRAINT fk_roic_observation_security FOREIGN KEY (security_id) REFERENCES security(id) ON DELETE CASCADE,
    CONSTRAINT uk_roic_observation_security_year UNIQUE (security_id, fiscal_year)
);

CREATE INDEX idx_roic_observation_security_year ON roic_observation(security_id, fiscal_year DESC);
