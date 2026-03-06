# Policy Source Tracking (2026-03-06)

## Purpose
- Keep policy mappings traceable to official sources.
- Prevent stale or incorrect rule drift before and after launch.

## Last Verified
- Verified at: `2026-03-06`
- Reviewer: `Codex + owner review pending`
- Scope:
  - reason-code evidence checklist mapping
  - platform-level validation constraints already implemented in code

## Official Sources (Primary)

### Stripe
- Dispute reason-code defense requirements:
  - https://docs.stripe.com/disputes/reason-codes-defense-requirements
- Dispute object / reason enum:
  - https://docs.stripe.com/api/disputes/object

### Shopify
- Respond to chargebacks by dispute type:
  - https://help.shopify.com/en/manual/payments/chargebacks/responding
- Resolve chargebacks overview:
  - https://help.shopify.com/en/manual/payments/chargebacks/resolve-chargeback

## Mapping Artifacts In Repo
- Policy coverage map:
  - `src/main/resources/policy/catalog-v1.json`
- Reason checklist metadata:
  - `src/main/resources/policy/reason-checklists-v1.json`
- Canonicalization / alias logic:
  - `src/main/java/com/example/demo/dispute/service/PolicyCatalogService.java`
- Checklist resolution logic:
  - `src/main/java/com/example/demo/dispute/service/ReasonCodeChecklistService.java`

## Review Cadence
- Pre-launch: full review before production cut.
- Post-launch:
  - quick monthly source sanity check
  - immediate check when Stripe/Shopify dispute docs announce updates

## Update Procedure
1. Re-read the four official source URLs above.
2. Compare changed policy points to:
   - `catalog-v1.json`
   - `reason-checklists-v1.json`
3. Update mappings and labels.
4. Run regression tests:
   - `PolicyCatalogServiceTest`
   - `ReasonCodeChecklistServiceTest`
   - `ValidationServiceTest`
   - `CaseControllerIntegrationTest`
5. Update `Last Verified` date in this file and `SESSION_HANDOFF_2026-03-06.md`.

## Guardrails
- Do not claim guaranteed dispute outcomes.
- Treat reason mappings as submission guidance, not legal advice.
- Keep policy source URLs in UI checklist output for operator traceability.
