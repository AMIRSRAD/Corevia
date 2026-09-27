package ir.corevia.ledger;

import java.util.Optional;
import java.util.UUID;

interface JournalRepository {

    Optional<Journal> find(UUID id);

    Optional<Journal> findByIdempotencyKey(String idempotencyKey);

    Optional<Journal> findByReversalOf(UUID originalJournalId);

    void insert(Journal journal);
}
