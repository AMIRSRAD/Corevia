CREATE TABLE account.bank_account (
    id UUID PRIMARY KEY,
    account_number VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    currency CHAR(3) NOT NULL,
    normal_balance VARCHAR(6) NOT NULL,
    status VARCHAR(16) NOT NULL,
    debit_total NUMERIC(38, 0) NOT NULL DEFAULT 0,
    credit_total NUMERIC(38, 0) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_bank_account_number UNIQUE (account_number),
    CONSTRAINT ck_bank_account_currency CHECK (currency = UPPER(currency)),
    CONSTRAINT ck_bank_account_normal_balance CHECK (normal_balance IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_bank_account_status CHECK (status IN ('OPEN', 'BLOCKED', 'CLOSED')),
    CONSTRAINT ck_bank_account_debit_total CHECK (debit_total >= 0),
    CONSTRAINT ck_bank_account_credit_total CHECK (credit_total >= 0)
);

CREATE INDEX ix_bank_account_status ON account.bank_account (status);
