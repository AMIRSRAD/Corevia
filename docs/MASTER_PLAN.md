# Corevia commercial banking core — master plan

Status: planning baseline
Prepared: 2026-07-12
Scope assumption: greenfield, multi-branch, multi-currency Iranian commercial bank; one
legal entity at first, extensible to multiple entities; web-based operations; API-first
integration. The jurisdiction-specific baseline is defined in
[IRAN_BASELINE.md](IRAN_BASELINE.md).

## 1. Executive decision

Build Corevia as a **ledger-first modular platform**, not as dozens of independently
deployed microservices.

The initial shape is:

1. A strongly consistent **transaction kernel** containing the ledger, accounts,
   products, balances, fees, interest, limits, and business-date controls. It is one
   deployable with strictly enforced internal module boundaries and one PostgreSQL
   cluster with schema-per-module ownership.
2. Independently deployable **business services** for customer/KYC, lending workflow,
   payments connectivity, teller operations, documents, notifications, reporting,
   and external integrations.
3. An event and workflow layer for post-commit propagation and long-running business
   processes. Kafka is not the source of truth for balances; the committed journal is.
4. A separate analytical plane. Operational reports may use read replicas/read models;
   regulatory and management analytics must never run uncontrolled queries against the
   posting primary.

This preserves ACID guarantees around money while allowing less tightly coupled
capabilities to scale and release independently. Modules can become services later only
when traffic, team ownership, data sovereignty, or release independence justifies it.

## 2. Decisions required before implementation

These are release-blocking product decisions, not technical details:

| Decision | Planning default | Why it matters |
|---|---|---|
| First operating jurisdiction | **Iran — confirmed** | Apply the Iran baseline and maintain a dated CBI circular register |
| Bank model | One legal entity, many branches | Changes chart-of-accounts hierarchy, consolidation, and data residency |
| First customer segment | SMEs and mid-market corporates | Drives party model, mandates, limits, lending, and channels |
| First currencies | **IRR as canonical ledger currency**, plus configurable foreign currencies | UI may display toman only as an explicit presentation conversion |
| Accounting basis | CBI/local requirements and usury-free banking contracts, with IFRS-compatible reporting mappings | Requires Iranian finance and Sharia/fiqh sign-off |
| Required payment rails | **Paya and Satna first**; Shetab/Shaparak, Sahab, Sayad/Chakavak in controlled waves | Exact CBI/NIC/Shaparak interface packs and certification are prerequisites |
| Card processing | Integrate a certified processor | Building an issuer/acquirer switch greatly expands PCI and certification scope |
| Deployment | **Self-hosted Iranian private cloud/on-premises across two domestic sites** | Reduces external dependency and supports local connectivity, custody, and resilience |
| Migration | **New bank / no legacy migration — confirmed** | Future bank adoption is a separate migration/coexistence workstream |
| Initial scale | 1M customers, 5M accounts, 1,000 financial TPS peak | Sizes partitioning, tests, and infrastructure without premature complexity |

Until the remaining decisions are answered, phase 0 may proceed, but production product
configuration and external certifications may not.

The first release is therefore a greenfield reference bank. It must not contain a future
bank's identifiers, chart of accounts, product codes, approval matrix, branch structure,
branding, or integration endpoints in application code. Those belong to signed,
effective-dated bank configuration packs. Financial invariants and jurisdiction controls
remain non-configurable.

## 3. Non-negotiable financial invariants

These rules sit above feature requirements and must be executable as tests:

- Every movement of value produces an immutable, balanced double-entry journal.
- A committed journal is never edited or deleted. Correction is a linked reversal and,
  when required, a replacement transaction.
- No application other than the posting engine may write ledger entries or balances.
- Every command that can move money has an idempotency key and deterministic replay
  result.
- Money uses fixed-precision decimal types and explicit currency metadata; binary
  floating point is forbidden.
- Journal balance is enforced per currency. FX uses explicit position/bridge entries
  and stores the applied rate, rate source, spread, and rounding entries.
- Booking time, value date, transaction date, settlement date, and business date are
  separate concepts.
- Ledger, cleared, available, blocked, pending, and projected balances are distinct.
- Holds, liens, limits, overdrafts, and authorization reservations cannot be bypassed
  by a channel.
- Fees, taxes, interest, schedules, and product terms are versioned and reproducible as
  of the original effective date.
- Closed periods cannot be posted into except through an explicitly authorized and
  audited adjustment process.
- End-of-day is restartable and idempotent. A failed step resumes without double
  accrual, duplicate posting, or missed work.
- Every privileged or financial action records actor, authority, reason, correlation,
  before/after state where appropriate, time, device/service, and approval chain.
- Maker-checker, segregation of duties, and transaction limits are policy-controlled,
  not UI conventions.
- Ledger-to-GL, settlement, suspense, and external-account reconciliations produce zero
  unexplained differences before a release is accepted.

## 4. Capability modules

### 4.1 Transaction kernel

| Module | Responsibilities | Owns |
|---|---|---|
| `ledger` | Journals, postings, posting rules, reversals, suspense, accounting events | Journal, entry, posting batch |
| `accounts` | Account/contract lifecycle, status, ownership links, balance views | Account, contract, balance snapshot |
| `products` | Versioned product definitions, eligibility, terms, pricing references | Product, product version, feature set |
| `fees-tax` | Fee assessment, waivers, tax rules, capitalization | Fee rule, assessment, waiver |
| `interest` | Accrual, compounding, capitalization, tiering, day-count rules | Rate plan, accrual, capitalization run |
| `limits-holds` | Limits, overdrafts, holds, liens, reservations, available funds | Limit, hold, lien, reservation |
| `calendar` | Business dates, holidays, cutoffs, value-date calculation | Calendar, business date, cutoff |
| `eod` | EOD/EOM/year-end orchestration, checkpoints, period controls | Run, step, checkpoint, period |
| `reference-data` | Currencies, countries, branches, codes, reason catalogs | Versioned reference values |

The kernel exposes use-case APIs; other modules never join its tables or invoke its
repositories. Compile-time architecture tests enforce that rule.

### 4.2 Customer and organization

| Module | Responsibilities |
|---|---|
| `party` | People, organizations, groups, relationships, addresses, contacts |
| `customer` | Customer lifecycle, segmentation, status, servicing branch |
| `kyc` | Onboarding cases, identity/business verification, beneficial ownership, reviews |
| `mandates` | Corporate signatories, signing groups, powers, account mandates |
| `consent-privacy` | Consents, purposes, retention, legal holds, subject requests |

AML/sanctions screening and identity verification should use provider-neutral ports.
Corevia owns workflow, decisions, evidence, and audit; specialized screening data and
models normally remain in certified external platforms.

### 4.3 Deposits and servicing

| Module | Responsibilities |
|---|---|
| `current-savings` | Demand/current/savings accounts and overdraft behavior |
| `term-deposits` | Placement, maturity, rollover, early break, payout instructions |
| `teller-cash` | Teller sessions, tills, vaults, cash movements, denomination counts |
| `servicing` | Freeze/unfreeze, dormancy, statements, certificates, account changes |

### 4.4 Lending

| Module | Responsibilities |
|---|---|
| `loan-origination` | Application, documents, assessment, approvals, offer, disbursement |
| `credit-limits` | Facilities, exposures, utilization, covenants, approval authorities |
| `collateral` | Collateral, valuations, liens, insurance, allocation to facilities |
| `loan-servicing` | Schedules, drawdowns, repayment allocation, repricing, payoff |
| `delinquency` | Arrears buckets, penalties, promises, collections, restructuring, write-off |
| `impairment` | Staging and provision inputs/outputs; integrate model engines as needed |

Credit decisioning must be explainable and versioned. AI may assist document extraction
or analysts but must not become an untraceable authority for credit or financial posting.
For Iran, the generic facility engine is specialized into approved Islamic banking
contracts and profit/fee/penalty treatments; see the Iran baseline.

### 4.5 Payments and treasury integration

| Module | Responsibilities |
|---|---|
| `payment-hub` | Initiation, validation, routing, status, returns, recalls, repair queues |
| `rail-adapters` | Domestic clearing, RTGS, instant payments, SWIFT or local schemes |
| `settlement` | Nostro/vostro positions, settlement batches, confirmations, exceptions |
| `fx` | Rates, quotes, spreads, conversions, revaluation, currency positions |
| `reconciliation` | Bank statement, switch/rail, suspense, GL and operational reconciliation |

Use an internal canonical payment model and versioned ISO 20022 mappings at integration
boundaries. Do not leak a particular rail's message schema throughout the domain.

### 4.6 Finance, risk, and operations

| Module | Responsibilities |
|---|---|
| `general-ledger` | Chart of accounts, accounting periods, trial balance, financial feeds |
| `regulatory-reporting` | Jurisdiction packs, lineage, validation, submissions |
| `risk-data` | Exposure snapshots, classifications, liquidity and risk feeds |
| `case-management` | Investigations, exceptions, complaints, disputes, task queues |
| `workflow-approvals` | Maker-checker, authority matrix, SLA, escalation |
| `documents` | Templates, generated artifacts, evidence metadata, retention |
| `notifications` | Durable email/SMS/push requests, preferences, delivery status |
| `audit` | Append-only audit trail, evidence export, integrity verification |

### 4.7 Channels

- Operations portal for branch, back office, finance, risk, and administrators.
- Corporate banking portal for accounts, statements, beneficiaries, payment batches,
  approval groups, loans, and service requests.
- Public API gateway for partner/open-banking access where regulation requires it.
- Mobile channel is a separate product stream and consumes the same channel APIs.

No channel owns financial truth or embeds posting logic.

## 5. Architecture and deployment

```text
Operations UI / Corporate UI / Partner APIs
                    |
          API gateway + WAF + rate limits
                    |
     Channel BFFs / Identity / Entitlements
                    |
     +--------------+------------------+
     |              |                  |
Customer & KYC  Transaction kernel  Payments / Lending / Teller
services         (ACID boundary)       services and workflows
     |              |                  |
     +-------- post-commit events ------+
                    |
            Kafka + schema registry
                    |
 Reporting read models / warehouse / notifications / integrations
```

### 5.1 Consistency model

- Local financial changes use a single PostgreSQL ACID transaction.
- The transactional outbox is committed with domain state; change-data capture publishes
  integration events after commit.
- Consumers are idempotent and retain processing keys.
- Long-running flows use durable orchestration and compensating business actions.
- Two-phase commit across services is forbidden.
- Event delivery is at least once. Business effects are exactly once through idempotency,
  uniqueness constraints, and state-machine guards.
- Event sourcing is not the universal persistence model. The journal and audit log are
  immutable; normal aggregates use relational state plus history where required.

### 5.2 Data architecture

- PostgreSQL cluster per operational cell; schema ownership per bounded context.
- Ledger tables partition by legal entity and accounting/booking period after benchmark
  validation. Do not partition prematurely by arbitrary identifiers.
- Synchronous high availability inside the primary site/region; encrypted asynchronous
  copy to disaster-recovery site, plus tested point-in-time recovery.
- Read models for statements, search, dashboards, and channel views.
- Object storage with immutability/retention controls for statements, evidence, and exports.
- Warehouse/lakehouse fed by governed CDC/events, with a business glossary, lineage, data
  quality rules, and reconciliation back to source totals.
- PII classification and residency tags are part of the data catalog from the first schema.

### 5.3 API and event rules

- REST/JSON with OpenAPI for channel and partner APIs; gRPC only where measured internal
  latency or streaming needs justify it.
- AsyncAPI plus versioned schemas for events.
- ISO 20022 models at payment boundaries; exact market-practice versions are adapter
  configuration, not kernel types.
- All mutating APIs accept idempotency and correlation identifiers.
- APIs use additive evolution, explicit deprecation windows, and consumer contract tests.
- No generic CRUD API for ledger, approvals, KYC, limits, or product configuration.

## 6. Technology baseline

Versions are a **qualification baseline**, not an instruction to auto-upgrade production.
Each release uses pinned patch versions, an SBOM, compatibility tests, and a documented
upgrade decision.

| Area | Baseline | Rationale |
|---|---|---|
| Core language | Java 25 LTS | Mature ecosystem, virtual threads, long support horizon |
| Application | Spring Boot 4.1.x, Spring Modulith, Spring Security | Production conventions and enforceable modularity |
| Data access | jOOQ + explicit SQL; Flyway migrations | Financial paths need visible SQL and precise locking; avoid opaque ORM behavior |
| OLTP | PostgreSQL 18.x | ACID, mature replication, partitioning, strong ecosystem |
| Event backbone | Apache Kafka 4.3.x in KRaft mode | Durable integration/event distribution; not balance truth |
| Workflow | Temporal, qualified current stable release | Durable onboarding, payment, lending, and EOD orchestration |
| Cache | Redis/Valkey only for disposable data | Never authoritative for balances, limits, approvals, or journals |
| Identity | OIDC/OAuth 2.1 provider with phishing-resistant MFA; Keycloak or enterprise IdP | Standards-based workforce and customer identity separation |
| Policy | Application policy layer plus OPA/Cedar where appropriate | Centralized, testable entitlements and contextual authorization |
| Web | TypeScript, React, accessible design system | Typed operations and corporate portals |
| Contracts | OpenAPI, AsyncAPI, Protobuf where gRPC is selected | Machine-validated boundaries |
| Observability | OpenTelemetry, Prometheus, Grafana, logs and trace backend | Correlated metrics, logs, and traces without vendor lock-in |
| Runtime | OCI containers, Kubernetes 1.36.x | Portable scheduling and controlled rollouts |
| Delivery | GitHub Actions/GitLab CI, Argo CD, Helm/Kustomize | Reproducible GitOps delivery |
| Infrastructure | Terraform or OpenTofu; Vault/KMS/HSM | Auditable infrastructure and key custody |
| Testing | JUnit, Testcontainers, ArchUnit, Pact, Playwright, k6/Gatling, TLA+/property tests selectively | Test invariants, boundaries, contracts, UI, load, and concurrency |

Use managed PostgreSQL/Kafka/Kubernetes only where the provider can meet residency,
audit, encryption, HSM, backup, failover, and regulator-access requirements. Maintain an
exit plan and portable backups.

## 7. Security, privacy, and compliance by design

- Separate workforce, customer, service, and machine identities.
- Phishing-resistant MFA for privileged users; step-up authentication for sensitive acts.
- RBAC plus contextual ABAC, transaction limits, branch/legal-entity scope, maker-checker,
  and segregation-of-duties conflict checks.
- TLS everywhere, mTLS for sensitive service paths, encrypted storage/backups, field-level
  protection for selected identifiers, and HSM-backed keys for payment cryptography.
- Tokenize card data and keep it outside the core wherever possible to minimize PCI scope.
- Append-only audit export to tamper-evident/WORM storage; clocks synchronized and monitored.
- Secrets never enter repositories, images, logs, traces, events, or support bundles.
- Data minimization, purpose/consent tracking, retention schedules, legal holds, deletion or
  anonymization workflows, and non-production masking.
- Secure SDLC aligned to NIST SSDF: threat models, signed builds, protected branches,
  dependency/container/IaC/secret scanning, SAST/DAST, SBOM, provenance, patch SLAs, and
  independent penetration tests.
- Map controls to the selected jurisdiction, Basel expectations, PCI DSS 4.0.1 when card
  data is in scope, privacy law, local cyber frameworks, accounting standards, and payment
  scheme rulebooks.
- Treat compliance as testable evidence: each requirement has an owner, control, automated
  evidence where possible, exception process, and review frequency.

Software alone does not make a bank operational. Licensing, governance, capital/liquidity,
policies, model validation, scheme membership, vendor contracts, incident response,
business continuity, trained operators, and independent assurance are parallel workstreams.

## 8. Provisional service objectives

Finalize these after product volumes and jurisdiction are known.

| Objective | Initial target |
|---|---|
| Financial posting availability | 99.99% monthly, excluding approved maintenance |
| Posting latency | p95 <= 250 ms and p99 <= 500 ms at qualified peak |
| Initial qualified throughput | 1,000 financial transactions/second, with 2x headroom test |
| In-site database RPO | 0 for an acknowledged posting |
| Disaster-site RPO / RTO | <= 5 minutes / <= 30 minutes |
| Balance correctness | Zero unexplained imbalance or duplicate financial effect |
| Audit coverage | 100% of privileged and financial state transitions |
| EOD | Completes inside agreed window and safely resumes after injected failures |
| Critical vulnerability remediation | Emergency SLA defined by risk; deployable without feature release |
| Accessibility | WCAG 2.2 AA for web channels |

Availability must be measured at the business operation level, not merely pod uptime.

## 9. Delivery roadmap

Phases overlap after the architecture runway. Estimates assume an experienced banking
product group of roughly 35–55 people; a smaller team should reduce launch scope rather
than compress controls.

### Phase 0 — bank definition and executable architecture (8–12 weeks)

Deliver:

- Jurisdiction and product-scope decision record.
- Banking glossary, bounded-context map, account and journal model.
- Regulatory/control matrix and data classification.
- Threat model, fraud-abuse cases, availability and DR model.
- Architecture decision records and dependency/version policy.
- Walking skeleton: authenticated request -> approval -> balanced test posting -> outbox
  event -> read model -> audit evidence.
- Automated build, signed artifact, SBOM, ephemeral environment, migrations, telemetry.
- Property-based ledger invariant suite and concurrency test harness.

Exit gate: an independent architecture/security review accepts the financial invariants,
failure model, and evidence strategy.

### Phase 1 — transaction kernel and platform foundation (4–6 months)

Deliver:

- Ledger, accounts, product versions, reference data, calendar, balances.
- Fees, tax hooks, interest engine, holds/limits, reversals and adjustments.
- Identity, entitlements, maker-checker, audit, case/work queues.
- Restartable EOD framework, period controls, trial balance and basic reconciliation.
- Operations portal foundation and support tooling.
- HA, backup/PITR, restore automation, observability, on-call runbooks.

Exit gate: randomized and model-based tests preserve all invariants under retries,
concurrency, failover, clock changes, partial outages, and EOD restarts.

### Phase 2 — deposits, customer, teller, and first payments (4–6 months)

Deliver:

- Party/customer, organization structures, beneficial owners, mandates, KYC cases.
- Current/savings and term-deposit lifecycle.
- Teller/till/vault and branch operations.
- Statements, documents, notifications, service requests.
- Payment hub and the first two jurisdiction-specific rails.
- Settlement, returns/recalls, exception repair, and external reconciliation.
- Corporate portal MVP with multi-user payment approvals.

Exit gate: end-to-end certification in scheme sandboxes, finance reconciliation, branch
day simulation, privacy review, and operational acceptance.

### Phase 3 — commercial lending and advanced finance (5–8 months)

Deliver:

- Loan origination, approval authorities, facilities and drawdowns.
- Collateral and valuation, schedules, repricing, payments, payoff.
- Delinquency, collections, restructure, write-off and recovery.
- Impairment/provision integration and complete GL mappings.
- Regulatory/risk data marts with lineage and data-quality controls.

Exit gate: golden loan schedules, independent finance validation, model governance,
scenario tests for arrears/restructure, and regulator-report reconciliation.

### Phase 4 — production proving and first-bank rollout (4–6 months)

Deliver:

- Production-scale performance, soak, chaos, penetration, and DR tests.
- Data migration tooling and at least three full dress rehearsals if migration is needed.
- Parallel run and daily product/account/ledger/GL/reconciliation sign-off.
- Operator training, support model, incident/problem/change processes.
- Phased rollout: employees/pilot branch -> limited customers -> controlled cohorts -> full.
- Hypercare dashboards, rollback/forward-fix plans, and executive go/no-go evidence.

Exit gate: business, finance, risk, security, operations, compliance, and the accountable
executive all sign the production-readiness dossier.

### Phase 5 — expansion (continuous)

Possible additions: multiple legal entities, trade finance, treasury products, additional
rails/countries, open banking, cards, syndication, advanced liquidity, and service
extraction where operating evidence supports it.

## 10. Team and ownership

Minimum durable structure:

- Product leadership: chief product owner plus deposit, payment, lending, finance, and
  operations domain owners.
- Transaction kernel team: senior engineers with accounting and concurrency expertise.
- Customer/deposits, payments, lending, and channels teams.
- Platform/SRE team with database reliability and DR ownership.
- Security engineering, IAM, fraud/financial-crime specialists.
- Data architecture/governance and regulatory reporting team.
- QA engineering embedded in teams plus independent performance, resilience, and security
  validation.
- Bank SMEs: finance/controller, treasury, credit, branch operations, compliance, privacy,
  internal audit, and legal.

Each module has one business owner, one technical owner, a data owner, SLOs, runbook,
threat model, control mapping, and on-call path.

## 11. Engineering operating model

- Monorepo initially, with directory-level ownership and one-way dependency rules.
- Separate immutable kernel code from versioned `jurisdiction-packs/iran` and
  `bank-packs/reference-bank` configuration; packs may specialize only documented
  extension points and may never bypass ledger or control invariants.
- Trunk-based development, short-lived branches, mandatory review, protected release tags.
- Architecture decision records for consequential choices.
- Database changes are expand/migrate/contract, backward compatible, rehearsed at scale.
- Feature flags cannot bypass controls or change accounting behavior without approval.
- Every financial feature ships with examples reviewed by finance and invariant tests.
- Contract, migration, reconciliation, security, and rollback tests are release gates.
- Production support has read-only-by-default tooling; no ad hoc database repairs.
- Emergency financial corrections use supported commands with approval and immutable audit.

Proposed repository shape after phase-0 approval:

```text
/apps                 deployable applications and portals
/modules              domain modules, each with API/application/domain/infrastructure
/platform             identity, audit, workflow, messaging, observability libraries
/jurisdiction-packs   Iran rules, reference data, reports, adapters, and localization
/bank-packs           versioned per-bank products, accounting mappings, policies, branding
/contracts            OpenAPI, AsyncAPI, ISO adapters, generated compatibility reports
/database             migrations, reference packs, reconciliation queries
/deploy               local dev, Kubernetes, GitOps, infrastructure modules
/tests                 system, model, performance, chaos, certification suites
/docs                  ADRs, domain glossary, controls, runbooks, operator manuals
```

## 12. Quality gates and definition of “usable”

A module is not complete because its happy-path API works. It is releasable only when:

- Domain behavior and exceptions are signed off by the responsible bank SME.
- Authorization, limits, maker-checker, audit, privacy, retention, and error handling work.
- Unit, property, architecture, integration, contract, migration, concurrency, performance,
  and recovery tests appropriate to its risk pass.
- Every financial outcome reconciles to journals and the GL mapping.
- Idempotent retry, reversal, repair, and operator workflows are demonstrated.
- Metrics, alerts, trace correlation, dashboards, runbooks, and support permissions exist.
- Backups/restores and DR behavior have been tested, not just documented.
- Threat model findings and vulnerabilities meet the release policy.
- User and operations documentation is current and versioned.
- Evidence is retained for audit and external certification.

The whole platform is “operational” only after a licensed bank's jurisdiction-specific
requirements, payment certifications, independent assurance, production rehearsals, and
accountable approvals are complete.

## 13. Principal risks and controls

| Risk | Control |
|---|---|
| Scope attempts to replicate every universal-bank product | Launch charter and deferred capability list |
| Premature microservices create inconsistent money state | Single transaction kernel and extraction criteria |
| Product configurability becomes an unsafe rules language | Typed/versioned product model, simulation, approval, effective dating |
| EOD becomes a fragile batch monolith | Checkpointed workflows, partitioned work, idempotency, continuous accrual where suitable |
| Reporting damages OLTP | Read models/warehouse and workload isolation |
| Generic admin access bypasses controls | Supported operations, least privilege, JIT access, immutable audit |
| External provider outage loses business work | Durable workflow, retries, circuit breakers, repair queues, reconciliation |
| Latest dependencies destabilize production | Qualification branch, pinned patches, canary/rollback, scheduled upgrades |
| Regulatory retrofit forces redesign | Jurisdiction decision in phase 0 and control/data lineage from day one |
| Migration corrupts balances | Immutable source extracts, repeated rehearsals, control totals, parallel run |

## 14. First implementation backlog

Once section 2 is resolved, execute in this order:

1. Create domain glossary and 20–30 canonical accounting scenarios.
2. Write ADRs for consistency boundary, journal model, identifiers, business time,
   multi-currency, module rules, event delivery, and tenancy/legal entities.
3. Scaffold the monorepo, dependency rules, CI, SBOM/provenance, and local environment.
4. Implement currency/money types and balanced journal aggregate with property tests.
5. Implement idempotent command handling and immutable posting/reversal path.
6. Add account/balance/hold model and adversarial concurrency tests.
7. Add outbox/event read model and reconciliation proof.
8. Add identity, entitlements, maker-checker, audit, and privileged support path.
9. Add business calendar, product versions, fee/interest calculation harness.
10. Run the walking-skeleton production-readiness review before expanding features.

## 15. Reference baseline

- [Java 25](https://docs.oracle.com/iaas/releasenotes/java-management/jdk-25-release-note.htm)
  was released as an LTS line in September 2025.
- [Spring Boot 4.1](https://spring.io/projects/spring-boot/) is the current stable
  generation and supports Java 25.
- [PostgreSQL 18](https://www.postgresql.org/docs/current/) is the current supported
  major line.
- [Apache Kafka 4.3](https://kafka.apache.org/community/downloads/) is the current
  supported generation at the planning date.
- [Kubernetes 1.36](https://kubernetes.io/releases/) is current; Kubernetes supports only
  the latest three minor lines, so platform upgrades must be routine.
- [ISO 20022](https://www.iso20022.org/about-iso-20022) provides the financial-message
  methodology, shared dictionary, and published message catalogue, but each market
  practice still requires its own mapping/certification.
- [Basel BCBS 239](https://www.bis.org/publ/bcbs239.htm) makes governance, architecture,
  accuracy, completeness, timeliness, and lineage first-class data requirements.
- [NIST SP 800-218 SSDF](https://csrc.nist.gov/pubs/sp/800/218/final) is the
  secure-development baseline; [PCI DSS 4.0.1](https://www.pcisecuritystandards.org/standards/pci-dss/)
  applies when the implementation handles cardholder data.
