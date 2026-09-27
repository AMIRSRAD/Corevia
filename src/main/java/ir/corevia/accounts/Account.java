package ir.corevia.accounts;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

public record Account(
        UUID id,
        String accountNumber,
        String name,
        String currency,
        NormalBalance normalBalance,
        AccountStatus status,
        BigInteger debitTotal,
        BigInteger creditTotal,
        long version,
        Instant createdAt) {

    public BigInteger bookBalance() {
        return normalBalance == NormalBalance.DEBIT
                ? debitTotal.subtract(creditTotal)
                : creditTotal.subtract(debitTotal);
    }
}
