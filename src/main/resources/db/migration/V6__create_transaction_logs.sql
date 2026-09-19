CREATE TABLE transaction_logs (
    id             UUID        PRIMARY KEY,
    transaction_id UUID        NOT NULL,
    status         VARCHAR(20) NOT NULL,
    detail         TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_transaction_logs_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id)
);

CREATE INDEX idx_transaction_logs_transaction_id ON transaction_logs (transaction_id);