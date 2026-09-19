CREATE TABLE providers (
    id     UUID         PRIMARY KEY,
    code   VARCHAR(50)  NOT NULL,
    name   VARCHAR(255) NOT NULL,
    status VARCHAR(20)  NOT NULL,

    CONSTRAINT uq_providers_code UNIQUE (code)
);