package ir.corevia.ledger;

import java.util.UUID;

public class JournalAlreadyReversedException extends LedgerException {

    public JournalAlreadyReversedException(UUID id) {
        super("Journal already has a reversal: " + id);
    }
}
