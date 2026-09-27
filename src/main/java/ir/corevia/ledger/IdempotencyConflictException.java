package ir.corevia.ledger;

public class IdempotencyConflictException extends LedgerException {

    public IdempotencyConflictException(String key) {
        super("Idempotency key was already used for a different request: " + key);
    }
}
