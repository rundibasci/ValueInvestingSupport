-- Phase JC2: persisted job runtime controls and scoped run metadata

CREATE TABLE job_runtime_setting (
    job_name          VARCHAR(100) NOT NULL,
    enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
    cron_expression   VARCHAR(120),
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    PRIMARY KEY (job_name)
);

ALTER TABLE job_run_log DROP CONSTRAINT IF EXISTS job_run_log_status_check;
ALTER TABLE job_run_log ADD CONSTRAINT job_run_log_status_check CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'SKIPPED'));
ALTER TABLE job_run_log ADD COLUMN scope_symbols VARCHAR(1000);
ALTER TABLE job_run_log ADD COLUMN scope_exchange VARCHAR(40);
ALTER TABLE job_run_log ADD COLUMN scope_data_types VARCHAR(400);
