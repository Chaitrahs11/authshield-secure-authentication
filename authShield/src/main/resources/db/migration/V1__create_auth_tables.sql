CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE otp_challenges (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    otp_hash VARCHAR(100) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    resend_available_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT otp_challenges_purpose_check CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD'))
);

CREATE INDEX otp_challenges_user_purpose_created_idx
    ON otp_challenges (user_id, purpose, created_at DESC);