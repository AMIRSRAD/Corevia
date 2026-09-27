package ir.corevia.accounts;

import java.util.UUID;

public class AccountNotFoundException extends AccountException {

    public AccountNotFoundException(UUID id) {
        super("Account not found: " + id);
    }
}
