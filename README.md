# Corevia

Corevia is a ledger-first modular commercial banking core. V1 provides the executable
financial foundation: accounts, immutable balanced journals, idempotent posting,
reversal-only correction, balance totals, audit evidence, and a transactional outbox.

## Run V1 locally

Requirements: Java 23 or newer. Maven is downloaded by the checked-in wrapper.

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

The default `local` profile uses an ephemeral H2 database in PostgreSQL compatibility
mode. Health is available at `http://localhost:8080/actuator/health`.

For a persistent PostgreSQL 18 environment, use Docker/Podman Compose:

```powershell
docker compose up --build
```

## V1 API

- `POST /api/v1/accounts`
- `GET /api/v1/accounts/{id}`
- `POST /api/v1/journals` with `Idempotency-Key`
- `GET /api/v1/journals/{id}`
- `POST /api/v1/journals/{id}/reversals` with `Idempotency-Key`

Amounts are JSON strings containing positive integers. For `IRR`, they are rials. The
complete contract is [corevia-v1.yaml](contracts/openapi/corevia-v1.yaml).

## Documentation

- [V1 implementation and operation](docs/V1.md)
- [Master delivery plan](docs/MASTER_PLAN.md)
- [Iran jurisdiction baseline](docs/IRAN_BASELINE.md)
- [Decision register](docs/DECISIONS.md)

The local profile deliberately disables authentication for development. The `prod`
profile requires an OIDC issuer and PostgreSQL credentials. V1 is an executable kernel,
not yet a licensed bank production release; the remaining regulatory, product, rail,
operations, and certification work is tracked in the master plan.
