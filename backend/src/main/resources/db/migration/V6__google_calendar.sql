CREATE TABLE google_calendar_link (
 user_id BIGINT PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
 calendar_id VARCHAR(255) NOT NULL,
 refresh_token BYTEA NOT NULL,
 status VARCHAR(20) NOT NULL CHECK (status IN ('CONNECTE','A_RECONNECTER')),
 connected_at TIMESTAMPTZ NOT NULL,
 last_error_at TIMESTAMPTZ
);
