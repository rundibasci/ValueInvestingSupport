-- Phase F1: add fundamentalDegradeThreshold to watchlist_item and index watchlist.user_id

ALTER TABLE watchlist_item
    ADD COLUMN fundamental_degrade_threshold DECIMAL(10,4);

CREATE INDEX IF NOT EXISTS idx_watchlist_user
    ON watchlist(user_id);
