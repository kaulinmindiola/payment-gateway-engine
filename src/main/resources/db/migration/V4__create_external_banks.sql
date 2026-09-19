CREATE TABLE external_banks (
    id          UUID         PRIMARY KEY,
    provider_id UUID         NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    country     VARCHAR(2)   NOT NULL,
    currency    VARCHAR(3)   NOT NULL,
    status      VARCHAR(20)  NOT NULL,

    CONSTRAINT fk_external_banks_provider FOREIGN KEY (provider_id) REFERENCES providers (id)
);

CREATE INDEX idx_external_banks_provider_id ON external_banks (provider_id);