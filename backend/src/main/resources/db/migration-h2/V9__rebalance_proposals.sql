CREATE TABLE rebalance_proposal (
    id UUID NOT NULL DEFAULT RANDOM_UUID(), portfolio_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL, holdings_fingerprint VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL, applied_at TIMESTAMP, PRIMARY KEY (id),
    CONSTRAINT fk_rebalance_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolio(id) ON DELETE CASCADE
);
CREATE TABLE rebalance_line (
    id UUID NOT NULL DEFAULT RANDOM_UUID(), proposal_id UUID NOT NULL, symbol VARCHAR(20) NOT NULL,
    captured_price DECIMAL(15,4) NOT NULL, current_quantity DECIMAL(15,6) NOT NULL,
    target_quantity DECIMAL(15,6) NOT NULL, PRIMARY KEY (id),
    CONSTRAINT fk_rebalance_line FOREIGN KEY (proposal_id) REFERENCES rebalance_proposal(id) ON DELETE CASCADE
);
CREATE INDEX idx_rebalance_portfolio ON rebalance_proposal(portfolio_id);
