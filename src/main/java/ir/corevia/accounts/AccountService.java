package ir.corevia.accounts;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountStore store;
    private final Clock clock;

    public AccountService(AccountStore store) {
        this.store = store;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public Account create(CreateAccountCommand command) {
        String accountNumber = required(command.accountNumber(), "accountNumber", 64);
        String name = required(command.name(), "name", 200);
        String currency = required(command.currency(), "currency", 3).toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new AccountException("currency must be a three-letter code");
        }
        if (command.normalBalance() == null) {
            throw new AccountException("normalBalance is required");
        }

        Account account = new Account(
                UUID.randomUUID(), accountNumber, name, currency, command.normalBalance(),
                AccountStatus.OPEN, java.math.BigInteger.ZERO, java.math.BigInteger.ZERO,
                0L, Instant.now(clock));
        store.insert(account);
        return account;
    }

    @Transactional(readOnly = true)
    public Account get(UUID id) {
        return store.find(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new AccountException(field + " is required");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new AccountException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}
