CREATE TABLE research_snapshot (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    security_id UUID REFERENCES security(id),
    symbol VARCHAR(20) NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    captured_at TIMESTAMP NOT NULL,
    current_price NUMERIC(15,4),
    composite_fair_value NUMERIC(15,4),
    margin_of_safety NUMERIC(10,4),
    value_score NUMERIC(5,2),
    wacc_used NUMERIC(10,4),
    data_source VARCHAR(30),
    piotroski_score INTEGER,
    moat_classification VARCHAR(30),
    rationale TEXT
);

CREATE INDEX idx_research_snapshot_user_time ON research_snapshot(user_id, captured_at DESC);
CREATE INDEX idx_research_snapshot_symbol_time ON research_snapshot(symbol, captured_at DESC);

CREATE TABLE investment_checklist (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_investment_checklist_user ON investment_checklist(user_id, updated_at DESC);

CREATE TABLE checklist_criterion (
    id UUID PRIMARY KEY,
    checklist_id UUID NOT NULL REFERENCES investment_checklist(id) ON DELETE CASCADE,
    label VARCHAR(240) NOT NULL,
    criterion_type VARCHAR(30) NOT NULL,
    metric_key VARCHAR(80),
    operator VARCHAR(10),
    threshold NUMERIC(20,4),
    display_order INTEGER NOT NULL
);

CREATE TABLE checklist_evaluation (
    id UUID PRIMARY KEY,
    checklist_id UUID NOT NULL REFERENCES investment_checklist(id) ON DELETE CASCADE,
    security_id UUID REFERENCES security(id),
    symbol VARCHAR(20) NOT NULL,
    evaluated_at TIMESTAMP NOT NULL
);

CREATE TABLE checklist_evaluation_item (
    id UUID PRIMARY KEY,
    evaluation_id UUID NOT NULL REFERENCES checklist_evaluation(id) ON DELETE CASCADE,
    criterion_id UUID REFERENCES checklist_criterion(id) ON DELETE SET NULL,
    label VARCHAR(240) NOT NULL,
    status VARCHAR(30) NOT NULL,
    actual_value NUMERIC(20,4),
    message TEXT
);

CREATE TABLE user_competence_preferences (
    user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    preferred_sectors TEXT,
    competence_industries TEXT,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE advisor_acknowledgement (
    user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    acknowledged_at TIMESTAMP NOT NULL,
    session_key VARCHAR(120)
);
