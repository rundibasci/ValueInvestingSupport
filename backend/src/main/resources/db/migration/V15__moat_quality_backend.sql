CREATE TABLE moat_result (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    security_id UUID NOT NULL,
    result_date DATE NOT NULL,
    moat_strength VARCHAR(30) NOT NULL,
    roic_trend VARCHAR(30) NOT NULL,
    years_analyzed INT,
    years_roic_above_wacc INT,
    roic_consistency_percentage DECIMAL(10,4),
    average_roic DECIMAL(10,4),
    estimated_wacc DECIMAL(10,4),
    average_roic_spread DECIMAL(10,4),
    trend_slope DECIMAL(10,4),
    reinvestment_rate DECIMAL(10,4),
    availability_message VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT fk_moat_result_security FOREIGN KEY (security_id) REFERENCES security(id) ON DELETE CASCADE
);

CREATE INDEX idx_moat_result_security_date ON moat_result(security_id, result_date);
CREATE INDEX idx_moat_result_strength ON moat_result(moat_strength);

CREATE TABLE capital_allocation_result (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    security_id UUID NOT NULL,
    result_date DATE NOT NULL,
    shares_outstanding_trend VARCHAR(40) NOT NULL,
    classification VARCHAR(50) NOT NULL,
    years_analyzed INT,
    shares_change_percentage DECIMAL(10,4),
    shares_cagr DECIMAL(10,4),
    dividend_yield DECIMAL(10,4),
    net_buyback_yield DECIMAL(10,4),
    total_shareholder_yield DECIMAL(10,4),
    insider_ownership_percentage DECIMAL(10,4),
    acquisition_spend_to_fcf DECIMAL(10,4),
    availability_message VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT fk_capital_allocation_security FOREIGN KEY (security_id) REFERENCES security(id) ON DELETE CASCADE
);

CREATE INDEX idx_capital_allocation_security_date ON capital_allocation_result(security_id, result_date);
CREATE INDEX idx_capital_allocation_shares_trend ON capital_allocation_result(shares_outstanding_trend);

CREATE TABLE valuation_band_result (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    security_id UUID NOT NULL,
    result_date DATE NOT NULL,
    metric VARCHAR(30) NOT NULL,
    years_analyzed INT,
    current_value DECIMAL(10,4),
    median_value DECIMAL(10,4),
    percentile25 DECIMAL(10,4),
    percentile75 DECIMAL(10,4),
    current_percentile DECIMAL(10,4),
    position VARCHAR(40) NOT NULL,
    availability_message VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT fk_valuation_band_security FOREIGN KEY (security_id) REFERENCES security(id) ON DELETE CASCADE
);

CREATE INDEX idx_valuation_band_security_date ON valuation_band_result(security_id, result_date);

CREATE TABLE stability_result (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    security_id UUID NOT NULL,
    result_date DATE NOT NULL,
    criterion_code VARCHAR(50) NOT NULL,
    label VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    actual_value DECIMAL(20,4),
    message VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT fk_stability_result_security FOREIGN KEY (security_id) REFERENCES security(id) ON DELETE CASCADE
);

CREATE INDEX idx_stability_result_security_date ON stability_result(security_id, result_date);
