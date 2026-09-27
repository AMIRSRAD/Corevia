package ir.corevia.accounts;

import java.util.Optional;
import java.util.UUID;

interface AccountStore {

    void insert(Account account);

    Optional<Account> find(UUID id);
}
