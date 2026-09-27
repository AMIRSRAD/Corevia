# Corevia — Iran jurisdiction baseline

Status: planning baseline
Prepared: 2026-07-12
Authority: product and architecture plan only; final legal interpretation, circular
applicability, technical interface specifications, and production approval belong to the
licensed bank, the Central Bank of the Islamic Republic of Iran (CBI), and the relevant
payment-network authorities.

## 1. Decisions now fixed

| Area | Iran baseline |
|---|---|
| Regulator | Central Bank of the Islamic Republic of Iran |
| Canonical domestic currency | Iranian rial (`IRR`) |
| User-facing denomination | Rial by default; toman may be an explicit derived display/input mode |
| Calendar | Solar Hijri for business/UI; ISO/Gregorian dates and instants retained internally |
| Time zone | IANA `Asia/Tehran`; never hard-code an offset or DST rule |
| Languages | Persian RTL required; English is an optional second locale |
| Banking model | Usury-free/Islamic banking contract model |
| Initial institution | Greenfield reference bank; no legacy migration or coexistence |
| First account rails | Paya and Satna |
| Later domestic rails | Shetab, Shaparak, Sahab, Sayad/Chakavak, and other CBI-mandated systems |
| Deployment | Self-hosted private cloud/on-premises in two Iranian sites |
| External services | Provider-neutral adapters and self-hosted fallbacks where operationally necessary |

## 2. Regulatory baseline and evidence register

Phase 0 must create a dated, version-controlled obligations register. At minimum it
tracks:

- The 1402 Central Bank Law and subsequent High Board decisions and CBI circulars.
- The current five-year development plan provisions applicable to banking.
- The Law for Usury-Free Banking Operations and its regulations.
- Anti-money-laundering and counter-terrorist-financing laws, regulations, instructions,
  reporting duties, and customer-identification requirements.
- The current Cheque Issuance Law and CBI cheque-ecosystem rules.
- CBI foreign-exchange, prudential, credit, provisioning, customer, fee, advertising,
  and reporting circulars applicable to the bank and product.
- CBI/National Informatics Corporation/payment-network technical and security rules.
- Shaparak and Shetab participation, certification, cryptographic, operational, dispute,
  and reconciliation requirements where card services are in scope.
- Iranian accounting, tax, electronic-commerce, evidence, records-retention, cyber,
  privacy, and employment requirements identified by local counsel.

Each obligation records the official source, publication and effective dates, affected
products/data/processes, control owner, implementation control, test/evidence, review
frequency, and superseding circular. A regulation watcher reviews the CBI laws,
circulars, payment regulations, and High Board/Fiqh Council decisions on a fixed cadence.

The public CBI site is a starting index, not a complete implementation specification.
Production integration requires the bank's current controlled documents and certification
packs.

## 3. Iran-specific domain model

### 3.1 Money and denomination safety

- All domestic journals, balances, limits, statements, regulatory extracts, and API
  contracts use `IRR` unless a contract explicitly specifies another currency.
- Monetary storage uses integer rial minor units/operational units according to the
  approved currency metadata; never `float`/`double`.
- Toman is not silently stored as a second currency. It is a presentation conversion of
  1 toman = 10 rials while that convention remains applicable.
- Every amount input shows a persistent denomination label. Toman entry requires an
  explicit mode, displays the resulting rial amount before authorization, and records
  the entered denomination in audit metadata.
- APIs reject unlabelled monetary values. Contracts contain amount, currency, scale,
  denomination where relevant, and rounding rule.
- The currency model is versionable so a future redenomination can run in dual-display,
  conversion, reconciliation, and cutover modes without rewriting historic journals.

### 3.2 Dates, language, and identity data

- Store event instants in UTC and business/local dates as explicit date values. Render
  Solar Hijri dates for Persian users and retain unambiguous ISO dates in integrations.
- Business calendars cover Iranian public holidays, CBI settlement calendars, branch
  calendars, cutoff times, and exceptional closures. Calendar changes are effective-dated.
- Normalize Persian/Arabic character variants for search while preserving the submitted
  legal spelling. Handle Persian and Latin digits and right-to-left/bidirectional text.
- The UI, generated documents, tables, currency fields, and print/PDF output receive RTL
  visual regression testing.
- Party identifiers support, subject to current official specifications: national code
  for natural persons, national identifier and registration number for legal persons,
  postal code/address, residency/nationality, tax identifiers, and bank/customer identity
  keys required by CBI systems.
- Account identifiers support internal account number and validated Iranian IBAN/Sheba.
  Card PAN and sensitive authentication data remain tokenized and isolated from the core.
- Corporate parties support beneficial owners, directors, authorized signatories,
  mandate groups, company seals where applicable, powers, signature rules, and expiry.

### 3.3 Deposits and resource mobilization

Initial product families:

- Current and savings Qard al-Hasan (`قرض‌الحسنه جاری` and `قرض‌الحسنه پس‌انداز`).
- Term investment deposits (`سپرده سرمایه‌گذاری مدت‌دار`).
- Corporate current accounts with multi-signature/approval mandates.
- Blocked, pledged, dormant, judicially restricted, and special-purpose accounts.

The product engine must separate principal guarantee rules, provisional profit, final
profit, bank agency fee, incentive awards, fees, tax, withholding, and early termination.
Profit allocation must be reproducible from the approved pool, weights, duration,
contract terms, and final declared results. A generic fixed-interest calculator is not
acceptable as the domain model.

### 3.4 Facilities and Islamic contracts

The lending capability becomes a contract/facility engine supporting the exact products
approved for the bank. Candidate contract families include:

- Qard al-Hasan (`قرض‌الحسنه`).
- Murabaha (`مرابحه`).
- Civil partnership (`مشارکت مدنی`).
- Mudarabah (`مضاربه`).
- Installment sale (`فروش اقساطی`).
- Lease-to-own (`اجاره به شرط تملیک`).
- Ju'alah (`جعاله`).
- Salam/forward purchase (`سلف`).

Do not implement these as labels over one amortizing-loan table. Each contract version
defines parties and roles, permitted use/purpose, asset or project, ownership and cash
flows, profit method, fees, guarantees/collateral, evidence/documents, disbursement
conditions, schedule, settlement, early closure, delinquency, penalty/commitment charge,
restructuring, accounting events, and regulatory classification.

Every product and contract template needs bank legal, finance, risk, compliance, and
Sharia/Fiqh approval. The system retains the approved template/version and signed
documents used for every booking.

### 3.5 Commercial-banking capabilities

In addition to deposits and facilities, the target capability map includes:

- Credit facilities and customer/group exposure limits.
- Collateral, valuation, insurance, perfection, and allocation.
- Rial and foreign-currency guarantees.
- Domestic letters of credit and documentary workflows when included in launch scope.
- Supply-chain finance and receivables/payables instruments approved by CBI.
- Corporate bulk payments, payroll, collections, virtual/alias account services where
  permitted, statements, and multi-user approval mandates.
- Foreign-exchange processes only under current CBI rules and authorized interfaces.

## 4. Domestic integration roadmap

All rail adapters sit behind a canonical payment/check/card contract. Exact message
formats, cryptography, network paths, timings, limits, error codes, and certification
cases come from controlled authority specifications.

| Wave | Integration | Core behavior required |
|---|---|---|
| 1 | Paya | Single/bulk account credit transfers, cycles/cutoffs, rejects/returns, settlement and reconciliation |
| 1 | Satna | High-value/urgent individual transfers, immediate status, liquidity controls, settlement finality and repair |
| 1 | Sheba validation | Format/check-digit, account ownership/status inquiry where authorized, beneficiary controls |
| 2 | Sayad/Chakavak cheque ecosystem | Issue/register/confirm/transfer/inquiry, image workflow, clearing, return and blocked-funds handling |
| 2 | Shetab/Sahab | Card switch authorization and card-to-card transfer through a certified switch boundary |
| 2 | Shaparak | Card acceptance/payment integration through certified PSP/network arrangements |
| 3 | Securities/other CBI systems | TABA and any required financing, FX, reporting, or supervisory interfaces |

Each adapter includes duplicate detection, durable inbound/outbound queues, cutoff and
business-day logic, cryptographic key ceremony support, HSM integration, schema/version
negotiation, timeout/unknown-state handling, repair queues, end-to-end correlation, daily
control totals, automated reconciliation, certification fixtures, and a simulator for
non-production development.

No timeout is treated as a failed financial transaction until the rail state is resolved.
Unknown outcomes are quarantined and reconciled to prevent duplicate payment.

## 5. Iran deployment and supply-chain profile

- Two independently operable Iranian data centers/sites with separate failure domains,
  network paths, power, storage, HSM/key custody, and operations teams.
- Primary financial database uses synchronous HA inside the active site. The DR copy and
  failover model must meet the bank/CBI-approved RPO and RTO and be exercised regularly.
- Run Kubernetes, PostgreSQL, Kafka, workflow, identity, observability, source control,
  CI runners, artifact/container registries, dependency proxies, documentation, secrets,
  and security scanning within bank-controlled infrastructure where practical.
- Mirror every production dependency, container, OS package, JDK, build plugin, and
  vulnerability feed required to build/recover the system. Produce offline-verifiable
  checksums, signatures, SBOMs, licenses, source references, and restore media.
- Qualify more than one source or a rebuild path for critical commercial components.
  Vendor or network unavailability must not prevent a security patch, rebuild, restore,
  deployment, or DR invocation.
- External SaaS is not placed on the critical posting, identity, key-management, audit,
  build, or incident-response path without an approved continuity and data-governance case.
- Production management has an isolated, strongly authenticated access path, just-in-time
  privilege, session recording, dual control for sensitive changes, and no direct internet
  dependency.
- Encrypt links and storage with bank-approved algorithms/providers. Payment keys live in
  certified HSMs and follow documented dual-control ceremonies and rotation/retirement.

## 6. Iran-adjusted delivery sequence

### Phase 0 additions

- Obtain a licensed-bank sponsor and named CBI/payment-network liaison.
- Collect the controlled circulars and current integration/certification packs.
- Establish a bilingual banking glossary and Iran accounting-event catalog.
- Model rial/toman safety, Solar Hijri business dates, Persian identity data, and RTL UX.
- Define Qard al-Hasan, term-investment-deposit, and two initial facility contracts with
  finance/legal/fiqh-approved examples.
- Build Paya and Satna simulators before access to certification environments.
- Decide whether card issuing/acquiring is launch scope; default is integration with an
  existing certified switch/processor rather than building one.

### Recommended first production slice

1. Natural/legal-person onboarding and KYC workflow.
2. Corporate mandates and maker-checker approvals.
3. Qard al-Hasan current/savings and one term investment deposit.
4. Rial ledger, GL feed, fees, provisional/final profit allocation, limits/holds.
5. Paya and Satna outbound/inbound processing and reconciliation.
6. Statements, branch/back-office operations, audit, AML case/reporting integration.
7. One simple approved facility contract after deposit/payment proving.

Cards, full cheque processing, advanced facilities, guarantees, letters of credit, and FX
follow after the first slice unless a sponsoring bank makes one a launch requirement.

### Future bank adoption

The initial build is not coupled to an existing bank, but adoption by one must be a
supported program rather than a fork. Keep these concerns outside kernel code:

- Bank identity, branding, legal-entity and branch hierarchy.
- Chart of accounts, accounting-event mappings, cost/profit centers, and report mappings.
- Product catalog, approved contract/document templates, rates, profit rules, fees, taxes,
  limits, eligibility, effective dates, and approval authorities.
- Workforce roles, segregation-of-duties matrix, service levels, working calendars, and
  operational reason codes.
- Rail member identifiers, endpoints, certificates, HSM key aliases, cutoff schedules,
  file/message variants, and reconciliation mappings.
- Data retention, reporting, document, notification, and localization policies.

Adoption tooling will later import parties, accounts, contracts, balances, accrued profit,
holds, collateral, mandates, payment state, and historical/audit data through versioned
staging schemas. Every migration produces control totals and balanced opening journals,
then passes repeated dress rehearsals, parallel reconciliation, and formal sign-off. It
does not write directly into production domain tables.

## 7. Remaining decisions

Iran is fixed, but these still change the architecture and program plan:

1. Which bank/institution will eventually sponsor CBI and network access and provide
   controlled specifications?
2. Is the first segment SMEs/corporates, retail, a Qard al-Hasan institution, or mixed?
3. Which products are mandatory on launch day?
4. Are card issuing, acquiring, ATM/POS, cheque, guarantees, LC, and FX launch scope?
5. What are the starting account/customer volumes, peak TPS, branch count, and EOD window?
6. Which two domestic sites and infrastructure/HSM vendors are approved?

Legacy migration and coexistence are explicitly out of the first release. They become a
bank-adoption workstream when a target institution is selected.

## 8. Official starting references

- [CBI reference laws](https://www.cbi.ir/datedlist/1531.aspx), including the 1402 Central
  Bank Law, AML law, CTF law, Cheque Issuance Law, and Law for Usury-Free Banking Operations.
- [CBI settlement and payment systems](https://www.cbi.ir/page/15722.aspx), describing
  Shetab, Shaparak, Paya, Sahab, Satna, TABA, and Chakavak.
- [CBI payment-system regulations](https://www.cbi.ir/simplelist/15726.aspx), including
  Shetab, payment service provider, fintech/payment facilitator, cheque, and account-based
  payment regulation collections.
- [CBI AML/CFT area](https://www.cbi.ir/page/8086.aspx), indexing laws, regulations,
  instructions, and circulars.
- [CBI currency statement](https://www.cbi.ir/section/1555.aspx), identifying rial as the
  currency of Iran.
