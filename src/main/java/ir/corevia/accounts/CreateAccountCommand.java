package ir.corevia.accounts;

public record CreateAccountCommand(
        String accountNumber,
        String name,
        String currency,
        NormalBalance normalBalance) {
}
