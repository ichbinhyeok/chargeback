# CODEX Progress Log (2026-03-06)

## Completed in this session

### Truth hardening (already in working tree, now verified)
- [x] Payment success UI is tied to real payment status (no `paid=1` spoof unlock).
- [x] Checkout duplication mitigation via Stripe idempotency key and session reuse handling.
- [x] Shopify Credit export keeps all files (including repeated evidence types).
- [x] State transition legality guard in `CaseService`.
- [x] Stale validation blocks payment/export.
- [x] Secure `manifest.json` in submission ZIP (no token/storage path leakage).
- [x] Issue target/fix strategy contract (`IssueTargetScope`, `FixStrategy`, resolver wiring).
- [x] FIXABLE truth hardening for Shopify size/PDF-A rules.

### New work completed now
- [x] Added `InputFingerprintService` (SHA-256 over case context + validation inputs + earlySubmit).
- [x] Added `validation_runs.input_fingerprint` persistence column (`V7__add_validation_input_fingerprint.sql`).
- [x] `ValidationHistoryService.record(...)` now stores input fingerprint from actual validation inputs.
- [x] `ValidationFreshnessService` now uses fingerprint comparison (legacy timestamp fallback retained).
- [x] Added stale guard regression when case context changes after validation.
- [x] Readiness calculation moved from controller to `ReadinessService`.
- [x] Missing evidence is now policy-aware by `ProductScope` (not full enum blanket).
- [x] Added `PolicyCatalogService` with precedence merge (`defaults -> platform -> scope -> reason -> network`).
- [x] Added externalized policy file `src/main/resources/policy/catalog-v1.json`.
- [x] Dashboard readiness now exposes missing required vs missing recommended separately.
- [x] Export `manifest.json` now includes `policyVersion` and `policyContextKey`.
- [x] Validation total-size rules now use policy catalog resolved limits (with fallback defaults).
- [x] Added validation precedence regression (`reason` limit overridden by `network` limit).
- [x] Updated product docs for current positioning and insight direction (`README.md`, `PRODUCT_IDENTITY_AND_INSIGHTS_2026-03-06.md`).
- [x] Added reason-code canonicalization + alias mapping in `PolicyCatalogService` (e.g. `13.1`, `4855`, `product-not-received`).
- [x] Added platform-specific reason override support (`STRIPE:<REASON>`, `SHOPIFY:<REASON>`) in policy merge.
- [x] Expanded `catalog-v1.json` with reason-specific required/recommended evidence mappings.
- [x] Added `ReasonCodeChecklistService` and metadata catalog (`policy/reason-checklists-v1.json`).
- [x] Wired reason checklist into dashboard/upload pages (`caseDashboard.jte`, `caseUpload.jte`).
- [x] Export manifest now includes `canonicalReasonKey`.
- [x] Added regression tests for alias resolution/precedence and reason checklist service.
- [x] Added platform-aware reason preset UX on `/new` with custom fallback.
- [x] Added server-side reason input hardening (trim, blank->null, max length 80).
- [x] Added policy source tracking doc (`POLICY_SOURCE_TRACKING_2026-03-06.md`).
- [x] Added launch-prep integration tests for reason preset UI and reason length validation.
- [x] Added prelaunch sandbox smoke integration test for Stripe/Shopify upload->validate->paid ZIP flow.
- [x] Added smoke execution report (`PRELAUNCH_SMOKE_REPORT_2026-03-06.md`).

## Tests executed and passing
- `com.example.demo.dispute.ReadinessServiceTest`
- `com.example.demo.dispute.InputFingerprintServiceTest`
- `com.example.demo.dispute.ValidationServiceTest`
- `com.example.demo.dispute.CaseControllerIntegrationTest`
- `com.example.demo.dispute.SubmissionExportServiceIntegrationTest`
- `com.example.demo.dispute.WebExportPageIntegrationTest`
- `com.example.demo.dispute.CaseServiceStateTransitionIntegrationTest`
- `com.example.demo.dispute.PolicyCatalogServiceTest`
- `com.example.demo.dispute.ReasonCodeChecklistServiceTest`
- `com.example.demo.dispute.ValidationServiceTest`
- `com.example.demo.dispute.CaseControllerIntegrationTest` (expanded with `/new` + reason validation assertions)
- `com.example.demo.dispute.PrelaunchSandboxSmokeIntegrationTest`

## Next candidate batch
- [ ] Add reason-code selector UX helpers in `/new` (reason suggestions per platform).
- [ ] Add explanation generator skeleton (reason + evidence-aware draft text).
- [ ] Add API endpoint for machine-readable reason checklist output.
