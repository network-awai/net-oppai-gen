CREATE TABLE IF NOT EXISTS site_errors (
 id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, route TEXT NOT NULL,
 code TEXT NOT NULL, status INTEGER NOT NULL, state TEXT NOT NULL DEFAULT 'open', resolution TEXT
);
CREATE INDEX IF NOT EXISTS site_errors_time ON site_errors(created_at);
CREATE TABLE IF NOT EXISTS feedback (
 id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, category TEXT NOT NULL,
 message TEXT NOT NULL, page TEXT NOT NULL, error_id TEXT,
 state TEXT NOT NULL DEFAULT 'open', resolution TEXT
);
CREATE INDEX IF NOT EXISTS feedback_time ON feedback(created_at);
CREATE TABLE IF NOT EXISTS feedback_limits (bucket TEXT PRIMARY KEY, used INTEGER NOT NULL, expires_at INTEGER NOT NULL);
