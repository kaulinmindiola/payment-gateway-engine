CREATE TABLE users (
    id         UUID PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    name       VARCHAR(255) NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_users_email UNIQUE (email)
);