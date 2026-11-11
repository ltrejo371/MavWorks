CREATE TABLE email_verifications (
    email VARCHAR(254) PRIMARY KEY,
    token UUID NOT NULL UNIQUE,
    code_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL,
    window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sends INTEGER NOT NULL,
    attempts INTEGER NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE
);
