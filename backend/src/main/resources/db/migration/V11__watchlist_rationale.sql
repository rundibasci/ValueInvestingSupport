ALTER TABLE watchlist_item
    ADD COLUMN monitoring_reason VARCHAR(40),
    ADD COLUMN rationale_note VARCHAR(500);
