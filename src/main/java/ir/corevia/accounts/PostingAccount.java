package ir.corevia.accounts;

import java.util.UUID;

public record PostingAccount(
        UUID id,
        String currency,
        NormalBalance normalBalance,
        AccountStatus status,
        long version) {
}
