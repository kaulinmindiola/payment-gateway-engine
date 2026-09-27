CREATE TABLE transactions (
    id                          UUID           PRIMARY KEY,
    source_account_id           UUID           NOT NULL,
    amount                      NUMERIC(19,2)  NOT NULL,
    idempotency_key             VARCHAR(255)   NOT NULL,
    status                      VARCHAR(20)    NOT NULL,
    failure_reason              VARCHAR(255),
    transfer_type               VARCHAR(10)    NOT NULL,
    target_account_id           UUID,
    target_provider_id          UUID,
    target_bank_id              UUID,
    target_external_reference   VARCHAR(255),
    created_at                  TIMESTAMPTZ    NOT NULL,
    updated_at                  TIMESTAMPTZ    NOT NULL,

    CONSTRAINT fk_transactions_source_account  FOREIGN KEY (source_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_transactions_target_account  FOREIGN KEY (target_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_transactions_target_provider FOREIGN KEY (target_provider_id) REFERENCES providers (id),
    CONSTRAINT fk_transactions_target_bank     FOREIGN KEY (target_bank_id) REFERENCES external_banks (id),

    CONSTRAINT uq_transactions_idempotency_key UNIQUE (idempotency_key),

    CONSTRAINT chk_transactions_target_exclusivity CHECK (
        (transfer_type = 'INTERNAL'
            AND target_account_id IS NOT NULL
            AND target_provider_id IS NULL
            AND target_bank_id IS NULL
            AND target_external_reference IS NULL)
        OR
        (transfer_type = 'EXTERNAL'
            AND target_account_id IS NULL
            AND target_provider_id IS NOT NULL
            AND target_bank_id IS NOT NULL
            AND target_external_reference IS NOT NULL)
    )
);

CREATE INDEX idx_transactions_source_account_id ON transactions (source_account_id);
CREATE INDEX idx_transactions_target_account_id ON transactions (target_account_id);