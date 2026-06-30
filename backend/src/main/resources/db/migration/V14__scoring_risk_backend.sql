ALTER TABLE value_score ADD COLUMN raw_total_score DECIMAL(5,2);
ALTER TABLE value_score ADD COLUMN mos_gate_applied BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE value_score ADD COLUMN weight_profile VARCHAR(50);

CREATE TABLE piotroski_result (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    security_id UUID NOT NULL,
    result_date DATE NOT NULL,
    total_score INT NOT NULL,
    positive_net_income BOOLEAN NOT NULL,
    positive_operating_cash_flow BOOLEAN NOT NULL,
    improving_roa BOOLEAN NOT NULL,
    cash_flow_quality BOOLEAN NOT NULL,
    lower_leverage BOOLEAN NOT NULL,
    improving_current_ratio BOOLEAN NOT NULL,
    no_share_dilution BOOLEAN NOT NULL,
    improving_gross_margin BOOLEAN NOT NULL,
    improving_asset_turnover BOOLEAN NOT NULL,
    availability_status VARCHAR(40) NOT NULL,
    availability_message VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT fk_piotroski_security FOREIGN KEY (security_id) REFERENCES security(id) ON DELETE CASCADE
);

CREATE INDEX idx_piotroski_security_date ON piotroski_result(security_id, result_date);

CREATE TABLE altman_result (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    security_id UUID NOT NULL,
    result_date DATE NOT NULL,
    score DECIMAL(10,4),
    zone VARCHAR(20) NOT NULL,
    formula_variant VARCHAR(40) NOT NULL,
    working_capital_to_assets DECIMAL(10,4),
    retained_earnings_to_assets DECIMAL(10,4),
    ebit_to_assets DECIMAL(10,4),
    market_value_equity_to_liabilities DECIMAL(10,4),
    sales_to_assets DECIMAL(10,4),
    availability_status VARCHAR(40) NOT NULL,
    availability_message VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT fk_altman_security FOREIGN KEY (security_id) REFERENCES security(id) ON DELETE CASCADE
);

CREATE INDEX idx_altman_security_date ON altman_result(security_id, result_date);
CREATE INDEX idx_altman_zone ON altman_result(zone);

CREATE TABLE cyclicality_result (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    security_id UUID NOT NULL,
    result_date DATE NOT NULL,
    classification VARCHAR(30) NOT NULL,
    revenue_coefficient DECIMAL(10,4),
    earnings_coefficient DECIMAL(10,4),
    normalized_earnings DECIMAL(20,2),
    cycle_adjusted_pe DECIMAL(10,4),
    years_analyzed INT NOT NULL,
    availability_status VARCHAR(40) NOT NULL,
    availability_message VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT fk_cyclicality_security FOREIGN KEY (security_id) REFERENCES security(id) ON DELETE CASCADE
);

CREATE INDEX idx_cyclicality_security_date ON cyclicality_result(security_id, result_date);

CREATE TABLE earnings_quality_result (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    security_id UUID NOT NULL,
    result_date DATE NOT NULL,
    fcf_to_net_income DECIMAL(10,4),
    sloan_accruals_ratio DECIMAL(10,4),
    classification VARCHAR(30) NOT NULL,
    deteriorating BOOLEAN NOT NULL,
    years_analyzed INT NOT NULL,
    availability_status VARCHAR(40) NOT NULL,
    availability_message VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT fk_earnings_quality_security FOREIGN KEY (security_id) REFERENCES security(id) ON DELETE CASCADE
);

CREATE INDEX idx_earnings_quality_security_date ON earnings_quality_result(security_id, result_date);
