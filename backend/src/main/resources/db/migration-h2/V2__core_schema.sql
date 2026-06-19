-- Phase A2: Domain Entities & DB Schema (H2-compatible)
-- Differences from PostgreSQL version:
--   - gen_random_uuid() → RANDOM_UUID()
--   - price_quote: no PARTITION BY; partition sub-tables removed
--   - Timestamps: TIMESTAMP (H2 has no TIMESTAMPTZ)

CREATE TABLE security (
    id              UUID         NOT NULL DEFAULT RANDOM_UUID(),
    symbol          VARCHAR(20)  NOT NULL,
    company_name    VARCHAR(255) NOT NULL,
    exchange        VARCHAR(50),
    sector          VARCHAR(100),
    industry        VARCHAR(100),
    country         VARCHAR(50),
    currency        VARCHAR(10),
    market_cap      DECIMAL(20,2),
    description     VARCHAR(32767),
    website         VARCHAR(255),
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_security_symbol UNIQUE (symbol)
);

CREATE TABLE fundamental_snapshot (
    id                  UUID        NOT NULL DEFAULT RANDOM_UUID(),
    security_id         UUID        NOT NULL,
    period              VARCHAR(20) NOT NULL,
    fiscal_year         INT,
    fiscal_quarter      INT,
    report_date         DATE,
    revenue             DECIMAL(20,2),
    net_income          DECIMAL(20,2),
    operating_income    DECIMAL(20,2),
    gross_profit        DECIMAL(20,2),
    eps                 DECIMAL(10,4),
    eps_diluted         DECIMAL(10,4),
    free_cash_flow      DECIMAL(20,2),
    operating_cash_flow DECIMAL(20,2),
    total_assets        DECIMAL(20,2),
    total_liabilities   DECIMAL(20,2),
    total_equity        DECIMAL(20,2),
    total_debt          DECIMAL(20,2),
    cash                DECIMAL(20,2),
    shares_outstanding  BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT fk_fundamental_security
        FOREIGN KEY (security_id) REFERENCES security(id) ON DELETE CASCADE
);

CREATE INDEX idx_fundamental_security_period
    ON fundamental_snapshot(security_id, period, fiscal_year);

CREATE TABLE ratio_snapshot (
    id               UUID        NOT NULL DEFAULT RANDOM_UUID(),
    security_id      UUID        NOT NULL,
    period           VARCHAR(20) NOT NULL,
    report_date      DATE,
    pe_ratio         DECIMAL(10,4),
    pb_ratio         DECIMAL(10,4),
    ps_ratio         DECIMAL(10,4),
    ev_to_ebitda     DECIMAL(10,4),
    roic             DECIMAL(10,4),
    roe              DECIMAL(10,4),
    roa              DECIMAL(10,4),
    debt_to_equity   DECIMAL(10,4),
    current_ratio    DECIMAL(10,4),
    dividend_yield   DECIMAL(10,4),
    payout_ratio     DECIMAL(10,4),
    gross_margin     DECIMAL(10,4),
    operating_margin DECIMAL(10,4),
    net_margin       DECIMAL(10,4),
    PRIMARY KEY (id),
    CONSTRAINT fk_ratio_security
        FOREIGN KEY (security_id) REFERENCES security(id) ON DELETE CASCADE
);

CREATE INDEX idx_ratio_security_period ON ratio_snapshot(security_id, period);

-- H2 does not support declarative table partitioning; plain table used instead.
CREATE TABLE price_quote (
    id             UUID          NOT NULL DEFAULT RANDOM_UUID(),
    security_id    UUID          NOT NULL,
    quote_date     DATE          NOT NULL,
    open           DECIMAL(15,4),
    high           DECIMAL(15,4),
    low            DECIMAL(15,4),
    close          DECIMAL(15,4) NOT NULL,
    adjusted_close DECIMAL(15,4),
    volume         BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT fk_price_quote_security
        FOREIGN KEY (security_id) REFERENCES security(id) ON DELETE CASCADE
);

CREATE INDEX idx_price_quote_security_date ON price_quote(security_id, quote_date);

CREATE TABLE valuation_result (
    id                   UUID        NOT NULL DEFAULT RANDOM_UUID(),
    security_id          UUID        NOT NULL,
    valuation_date       DATE        NOT NULL,
    dcf_fair_value       DECIMAL(15,4),
    dcf_fair_value_low   DECIMAL(15,4),
    dcf_fair_value_high  DECIMAL(15,4),
    graham_number        DECIMAL(15,4),
    ddm_fair_value       DECIMAL(15,4),
    composite_fair_value DECIMAL(15,4),
    current_price        DECIMAL(15,4),
    margin_of_safety     DECIMAL(10,4),
    recommendation       VARCHAR(30),
    PRIMARY KEY (id),
    CONSTRAINT fk_valuation_security
        FOREIGN KEY (security_id) REFERENCES security(id) ON DELETE CASCADE
);

CREATE INDEX idx_valuation_security ON valuation_result(security_id);

CREATE TABLE value_score (
    id             UUID       NOT NULL DEFAULT RANDOM_UUID(),
    security_id    UUID       NOT NULL,
    score_date     DATE       NOT NULL,
    mos_score      DECIMAL(5,2),
    quality_score  DECIMAL(5,2),
    safety_score   DECIMAL(5,2),
    growth_score   DECIMAL(5,2),
    dividend_score DECIMAL(5,2),
    total_score    DECIMAL(5,2),
    PRIMARY KEY (id),
    CONSTRAINT fk_value_score_security
        FOREIGN KEY (security_id) REFERENCES security(id) ON DELETE CASCADE
);

CREATE INDEX idx_value_score_security ON value_score(security_id);

CREATE TABLE dividend_record (
    id               UUID          NOT NULL DEFAULT RANDOM_UUID(),
    security_id      UUID          NOT NULL,
    ex_dividend_date DATE          NOT NULL,
    payment_date     DATE,
    amount           DECIMAL(10,4) NOT NULL,
    currency         VARCHAR(10),
    frequency        VARCHAR(20),
    PRIMARY KEY (id),
    CONSTRAINT fk_dividend_security
        FOREIGN KEY (security_id) REFERENCES security(id) ON DELETE CASCADE
);

CREATE TABLE insider_trade (
    id               UUID          NOT NULL DEFAULT RANDOM_UUID(),
    security_id      UUID          NOT NULL,
    trade_date       DATE          NOT NULL,
    insider_name     VARCHAR(255)  NOT NULL,
    title            VARCHAR(255),
    transaction_type VARCHAR(10)   NOT NULL,
    shares           BIGINT,
    price_per_share  DECIMAL(15,4),
    trade_value      DECIMAL(20,2),
    PRIMARY KEY (id),
    CONSTRAINT fk_insider_security
        FOREIGN KEY (security_id) REFERENCES security(id) ON DELETE CASCADE
);

CREATE TABLE app_user (
    id            UUID         NOT NULL DEFAULT RANDOM_UUID(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_email UNIQUE (email)
);

CREATE INDEX idx_user_email ON app_user(email);

CREATE TABLE portfolio (
    id          UUID         NOT NULL DEFAULT RANDOM_UUID(),
    user_id     UUID         NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(32767),
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_portfolio_user
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE INDEX idx_portfolio_user ON portfolio(user_id);

CREATE TABLE holding (
    id                 UUID          NOT NULL DEFAULT RANDOM_UUID(),
    portfolio_id       UUID          NOT NULL,
    symbol             VARCHAR(20)   NOT NULL,
    quantity           DECIMAL(15,6) NOT NULL,
    average_cost_basis DECIMAL(15,4),
    currency           VARCHAR(10),
    added_at           TIMESTAMP     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_holding_portfolio
        FOREIGN KEY (portfolio_id) REFERENCES portfolio(id) ON DELETE CASCADE
);

CREATE TABLE watchlist (
    id      UUID         NOT NULL DEFAULT RANDOM_UUID(),
    user_id UUID         NOT NULL,
    name    VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_watchlist_user
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE TABLE watchlist_item (
    id            UUID          NOT NULL DEFAULT RANDOM_UUID(),
    watchlist_id  UUID          NOT NULL,
    symbol        VARCHAR(20)   NOT NULL,
    mos_alert_min DECIMAL(5,2),
    mos_alert_max DECIMAL(5,2),
    added_at      TIMESTAMP     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_watchlist_item_watchlist
        FOREIGN KEY (watchlist_id) REFERENCES watchlist(id) ON DELETE CASCADE
);

CREATE INDEX idx_watchlist_item_watchlist ON watchlist_item(watchlist_id);

CREATE TABLE alert (
    id              UUID          NOT NULL DEFAULT RANDOM_UUID(),
    user_id         UUID          NOT NULL,
    alert_type      VARCHAR(50)   NOT NULL,
    symbol          VARCHAR(20)   NOT NULL,
    threshold       DECIMAL(15,4),
    status          VARCHAR(20)   NOT NULL,
    triggered_at    TIMESTAMP,
    acknowledged_at TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_alert_user
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE INDEX idx_alert_user_status ON alert(user_id, status);
