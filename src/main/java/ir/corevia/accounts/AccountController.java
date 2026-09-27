package ir.corevia.accounts;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
class AccountController {

    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accounts.create(new CreateAccountCommand(
                request.accountNumber(), request.name(), request.currency(), request.normalBalance()));
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.id()))
                .body(AccountResponse.from(account));
    }

    @GetMapping("/{id}")
    AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(accounts.get(id));
    }

    record CreateAccountRequest(
            @NotBlank @Size(max = 64) String accountNumber,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @NotNull NormalBalance normalBalance) {
    }

    record AccountResponse(
            UUID id,
            String accountNumber,
            String name,
            String currency,
            NormalBalance normalBalance,
            AccountStatus status,
            String debitTotal,
            String creditTotal,
            String bookBalance,
            long version,
            Instant createdAt) {

        static AccountResponse from(Account account) {
            return new AccountResponse(
                    account.id(), account.accountNumber(), account.name(), account.currency(),
                    account.normalBalance(), account.status(), account.debitTotal().toString(),
                    account.creditTotal().toString(), account.bookBalance().toString(),
                    account.version(), account.createdAt());
        }
    }
}
