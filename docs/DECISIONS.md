# Corevia planning decision register

This register records product decisions that materially shape the architecture. A later
change requires an architecture decision record and impact assessment; it does not erase
the original decision.

| ID | Date | Decision | Status | Consequence |
|---|---|---|---|---|
| D-001 | 2026-07-12 | Build a fully operational, modular commercial banking core | Accepted | Ledger, controls, operations, resilience, and certification are all in scope |
| D-002 | 2026-07-12 | Use a strongly consistent modular transaction kernel with independently deployable satellites | Accepted | Avoid distributed money transactions; extract services only with evidence |
| D-003 | 2026-07-12 | Iran is the first jurisdiction | Accepted | Apply CBI, Iranian payment, rial, Islamic-contract, Persian/RTL, and domestic deployment requirements |
| D-004 | 2026-07-12 | The first institution is a greenfield reference bank | Accepted | No legacy migration, coexistence, or existing-bank customization in the initial release |
| D-005 | 2026-07-12 | Preserve later bank adoption through versioned jurisdiction and bank packs | Accepted | Keep bank codes, products, accounting mappings, policies, branding, and endpoints outside kernel code |

## Guardrails for D-005

Configuration may select approved behavior; it may not redefine financial truth. The
following remain kernel-enforced and cannot be disabled by a bank pack:

- Balanced immutable journals and reversal-only correction.
- Idempotency and duplicate financial-effect prevention.
- Currency/denomination safety and explicit financial dates.
- Authorization, maker-checker, segregation of duties, and audit requirements.
- Closed-period controls, restartable processing, and reconciliation evidence.
- Schema and contract validation, version history, and effective dating.

The first bank pack is `reference-bank`. When an institution is selected, create a new
pack and migration program; do not modify or copy the kernel into a bank-specific fork.

