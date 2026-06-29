ALTER TABLE valuation_result
    ADD COLUMN dcf_terminal_value_percentage DECIMAL(10,4),
    ADD COLUMN dcf_high_terminal_dependence BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN epv_fair_value DECIMAL(15,4),
    ADD COLUMN epv_normalized_earnings DECIMAL(20,2),
    ADD COLUMN epv_years_averaged INT,
    ADD COLUMN owner_earnings DECIMAL(20,2),
    ADD COLUMN maintenance_capex_estimate DECIMAL(20,2);

CREATE TABLE wacc_result (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    valuation_result_id UUID NOT NULL,
    wacc DECIMAL(10,6),
    risk_free_rate DECIMAL(10,6),
    equity_risk_premium DECIMAL(10,6),
    beta DECIMAL(10,6),
    cost_of_equity DECIMAL(10,6),
    cost_of_debt DECIMAL(10,6),
    debt_weight DECIMAL(10,6),
    equity_weight DECIMAL(10,6),
    effective_tax_rate DECIMAL(10,6),
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    source VARCHAR(50),
    PRIMARY KEY (id),
    CONSTRAINT uq_wacc_result_valuation UNIQUE (valuation_result_id),
    CONSTRAINT fk_wacc_result_valuation
        FOREIGN KEY (valuation_result_id) REFERENCES valuation_result(id) ON DELETE CASCADE
);

CREATE TABLE graham_checklist_item (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    valuation_result_id UUID NOT NULL,
    criterion_code VARCHAR(80) NOT NULL,
    label VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    actual_value DECIMAL(15,6),
    PRIMARY KEY (id),
    CONSTRAINT fk_graham_checklist_valuation
        FOREIGN KEY (valuation_result_id) REFERENCES valuation_result(id) ON DELETE CASCADE
);

CREATE INDEX idx_graham_checklist_valuation
    ON graham_checklist_item(valuation_result_id);

CREATE TABLE composite_weight_preference (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    dcf_weight DECIMAL(10,6) NOT NULL,
    graham_weight DECIMAL(10,6) NOT NULL,
    ddm_weight DECIMAL(10,6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_composite_weight_preference_user UNIQUE (user_id),
    CONSTRAINT fk_composite_weight_preference_user
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT chk_composite_weight_preference_sum
        CHECK (dcf_weight + graham_weight + ddm_weight = 1.000000)
);
