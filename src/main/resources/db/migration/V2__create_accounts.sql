CREATE TABLE accounts (
    id         UUID           PRIMARY KEY,
    owner_id   UUID           NOT NULL,
    balance    NUMERIC(19,2)  NOT NULL,
    status     VARCHAR(20)    NOT NULL,
    version    BIGINT         NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ    NOT NULL,
    updated_at TIMESTAMPTZ    NOT NULL,

    CONSTRAINT fk_accounts_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT chk_accounts_balance_non_negative CHECK (balance >= 0)
);

CREATE INDEX idx_accounts_owner_id ON accounts (owner_id);