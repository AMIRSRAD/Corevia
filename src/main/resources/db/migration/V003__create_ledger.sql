CREATE TABLE ledger.journal (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    business_date DATE NOT NULL,
    value_date DATE NOT NULL,
    narrative VARCHAR(500) NOT NULL,
    reversal_of UUID NULL,
    created_by VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_journal_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_journal_reversal_of UNIQUE (reversal_of),
    CONSTRAINT fk_journal_reversal_of FOREIGN KEY (reversal_of) REFERENCES ledger.journal (id)
);

CREATE TABLE ledger.journal_entry (
    id UUID PRIMARY KEY,
    journal_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    account_id UUID NOT NULL,
    side VARCHAR(6) NOT NULL,
    amount NUMERIC(38, 0) NOT NULL,
    currency CHAR(3) NOT NULL,
    description VARCHAR(500) NOT NULL,
    CONSTRAINT fk_journal_entry_journal FOREIGN KEY (journal_id) REFERENCES ledger.journal (id),
    CONSTRAINT fk_journal_entry_account FOREIGN KEY (account_id) REFERENCES account.bank_account (id),
    CONSTRAINT uk_journal_entry_line UNIQUE (journal_id, line_number),
    CONSTRAINT ck_journal_entry_side CHECK (side IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_journal_entry_amount CHECK (amount > 0),
    CONSTRAINT ck_journal_entry_currency CHECK (currency = UPPER(currency))
);

CREATE INDEX ix_journal_business_date ON ledger.journal (business_date);
CREATE INDEX ix_journal_entry_account ON ledger.journal_entry (account_id, journal_id);
