ALTER TABLE security ADD COLUMN isin VARCHAR(12);
ALTER TABLE security ADD CONSTRAINT uq_security_isin UNIQUE (isin);
CREATE INDEX idx_security_isin ON security(isin);

CREATE TABLE portfolio_cash_balance (
    id UUID PRIMARY KEY,
    portfolio_id UUID NOT NULL REFERENCES portfolio(id) ON DELETE CASCADE,
    currency VARCHAR(3) NOT NULL,
    native_amount DECIMAL(20,4) NOT NULL,
    base_currency VARCHAR(3) NOT NULL,
    base_amount DECIMAL(20,4),
    source_import_id UUID,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_portfolio_cash_currency UNIQUE(portfolio_id, currency)
);

CREATE TABLE portfolio_import (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    portfolio_id UUID REFERENCES portfolio(id) ON DELETE CASCADE,
    original_filename VARCHAR(255) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    mode VARCHAR(10) NOT NULL,
    base_currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_row_count INTEGER NOT NULL,
    ready_row_count INTEGER NOT NULL,
    warning_count INTEGER NOT NULL,
    error_count INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    committed_at TIMESTAMP,
    CONSTRAINT portfolio_import_counts_valid CHECK (
        source_row_count >= 0 AND ready_row_count >= 0 AND warning_count >= 0 AND error_count >= 0
    )
);
CREATE INDEX idx_portfolio_import_user_created ON portfolio_import(user_id, created_at DESC);
CREATE INDEX idx_portfolio_import_checksum ON portfolio_import(user_id, portfolio_id, checksum, mode);

CREATE TABLE portfolio_import_row (
    id UUID PRIMARY KEY,
    import_id UUID NOT NULL REFERENCES portfolio_import(id) ON DELETE CASCADE,
    row_number INTEGER NOT NULL,
    product_name VARCHAR(500) NOT NULL,
    source_code VARCHAR(50),
    isin VARCHAR(12),
    quantity DECIMAL(20,6),
    source_last_price DECIMAL(20,6),
    native_currency VARCHAR(3),
    native_value DECIMAL(20,4),
    base_value DECIMAL(20,4),
    resolved_security_id UUID REFERENCES security(id),
    classification VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    warning_text VARCHAR(1000),
    error_text VARCHAR(1000),
    committed_outcome VARCHAR(30),
    CONSTRAINT uq_portfolio_import_row UNIQUE(import_id, row_number)
);
CREATE INDEX idx_portfolio_import_row_import ON portfolio_import_row(import_id, row_number);
