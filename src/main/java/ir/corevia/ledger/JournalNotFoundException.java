package ir.corevia.ledger;

import java.util.UUID;

public class JournalNotFoundException extends LedgerException {

    public JournalNotFoundException(UUID id) {
        super("Journal not found: " + id);
    }
}
