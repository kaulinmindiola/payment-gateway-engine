-- Guided Swagger tour: two identities (ownership demo) and fixed-id accounts,
-- so every Swagger example runs with a single click. Docker profile only.

UPDATE users
SET name = 'Alice Demo', email = 'alice@payment-gateway-engine.local', updated_at = now()
WHERE id = '99999999-9999-9999-9999-999999999999';

INSERT INTO users (id, email, name, status, created_at, updated_at) VALUES
('88888888-8888-8888-8888-888888888888', 'bob@payment-gateway-engine.local', 'Bob Demo', 'ACTIVE', now(), now());

INSERT INTO accounts (id, owner_id, balance, status, version, created_at, updated_at) VALUES
('30000000-0000-0000-0000-000000000001', '99999999-9999-9999-9999-999999999999', 1000.00, 'ACTIVE', 0, now(), now()),
('30000000-0000-0000-0000-000000000002', '99999999-9999-9999-9999-999999999999',  250.00, 'ACTIVE', 0, now(), now()),
('30000000-0000-0000-0000-000000000003', '88888888-8888-8888-8888-888888888888',  500.00, 'ACTIVE', 0, now(), now());