package ir.corevia.ledger;

import java.math.BigInteger;
import java.util.UUID;

public record PostingLineCommand(
        UUID accountId,
        EntrySide side,
        BigInteger amount,
        String currency,
        String description) {
}
