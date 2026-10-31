ALTER TABLE app_users ADD COLUMN banned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE reports ADD COLUMN reviewed_by_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL;
ALTER TABLE reports ADD COLUMN reviewed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE reports ADD COLUMN action VARCHAR(255) CHECK (action IN ('WARNING', 'BAN'));
ALTER TABLE reports ADD COLUMN decision_note VARCHAR(2000);
CREATE INDEX idx_reports_reported_status ON reports(reported_user_id, status);
