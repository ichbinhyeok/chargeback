# Session Handoff (2026-03-06)

Reference docs:
- `README.md` (project definition, runbook, env, flow)
- `PRODUCT_IDENTITY_AND_INSIGHTS_2026-03-06.md` (positioning and strategic direction)
- `CODEX_PROGRESS_LOG_2026-03-06.md` (implementation progress and test status)
- `POLICY_SOURCE_TRACKING_2026-03-06.md` (official source traceability and review cadence)
- `PRELAUNCH_SMOKE_REPORT_2026-03-06.md` (platform smoke execution result)

## 1) Project Snapshot
- Product: `Chargeback Evidence Pack Builder`
- Goal: reduce evidence upload failures caused by Stripe/Shopify formatting policies.
- Primary UX:
  1. Create case
  2. Upload files
  3. Validate policy issues
  4. Auto-fix supported issues
  5. Export submission artifacts (free/paid gates)

## 2) Current Target Niche (Important)
- Current GTM focus is **individual sellers who already failed evidence upload due to policy constraints**.
- Positioning:
  - Not "generic dispute helper"
  - Yes "upload-failure recovery tool for policy/format issues"
- Promise boundary:
  - Helps formatting/compliance and packaging
  - Does **not** guarantee dispute outcomes

## 3) Latest Delivered State

### 3.1 Policy rules aligned by scope
- Stripe dispute:
  - PDF/JPEG/PNG only
  - one file per evidence type
  - no external links
  - total <= 4.5MB
  - pages < 50 total
  - Mastercard pages <= 19
- Shopify Payments chargeback:
  - PDF/JPEG/PNG only
  - one file per evidence type
  - no external links
  - each file <= 2MB
  - total <= 4MB
  - PDF pages < 50
  - no PDF portfolio
  - PDF/A required
- Shopify Credit dispute:
  - PDF/JPEG/PNG only
  - total <= 4.5MB
  - PDF pages < 50

### 3.2 Auto-fix behavior
- Supports:
  - merge duplicate files per evidence type
  - compress oversized images for Shopify **Payments** scope
- Does not currently do:
  - full PDF compression optimization
  - generic PDF/A conversion

### 3.3 Access, privacy, and recovery
- Case-token based access (no login required for MVP)
- Access key text download
- Token rotation supported
- Sensitive routes (`/c/*`, `/api/*`, `/webhooks/*`) send:
  - `X-Robots-Tag: noindex, nofollow, noarchive`
  - `Cache-Control: no-store`
  - `Pragma: no-cache`
  - `Expires: 0`
  - `Referrer-Policy: no-referrer`
- Recent case vault in browser is now **opt-in** with 30-day TTL.

### 3.4 Upload guardrails
- New case-level file cap:
  - env: `APP_CASE_MAX_FILES` (default: 100)
- Upload page now shows budget/readiness indicators:
  - uploaded file count vs cap
  - total bytes used vs policy limit
  - remaining budget
  - page headroom hints
- Client-side prechecks:
  - unsupported extension detection
  - slot cap warnings
  - total size budget warning before upload

### 3.5 Reason-code input hardening (launch-prep)
- `/new` now supports platform-aware reason preset selection.
- Custom reason input is still allowed when preset list does not match merchant context.
- `CaseService` now normalizes reason input:
  - trims whitespace
  - converts blank to null
  - rejects overlong values (`max 80 chars`)
- New reason preset/control regression checks added to integration tests.

### 3.6 Explanation draft (Step 5-lite) delivered
- New route: `/c/{caseToken}/explanation`
  - reason-aware dispute explanation draft generated from:
    - platform/scope/reason context
    - uploaded evidence inventory
    - checklist missing evidence and priority actions
- New route: `/c/{caseToken}/download/explanation.txt`
  - direct plain-text draft download
- Paid ZIP export now includes:
  - `dispute_explanation_draft.txt`
- Guardrail maintained:
  - explanation is writing aid only; no legal advice / no win guarantee

### 3.7 pSEO guide system upgraded (error-intent targeting)
- `SeoController` now loads guide content from `src/main/resources/seo/guides-v1.json`.
- Guide pages now cover both:
  - reason-code guides
  - upload-error guides (size limit, external-link rejection, PDF/A/profile errors, portfolio errors)
- `/guides/{platform}` now renders two sections:
  - Upload/Error Fix Guides
  - Reason-Code Evidence Guides
- Guide detail page now includes:
  - search-intent phrase block
  - structured sections (`Why Upload Fails -> Required Evidence Checklist -> Fix Steps`)
  - explanation sample teaser + CTA to `/new`
  - policy source links
  - related internal links (same platform prioritized)
- `/sitemap.xml` includes all catalog entries dynamically.

### 3.8 pSEO scaling + KPI instrumentation (launch growth prep)
- Guide catalog expanded to 40 entries:
  - 20 reason pages
  - 20 upload/error pages
- Catalog generation is now scriptable:
  - `scripts/generate_seo_guides.mjs`
- Quality gate is enforced by test:
  - `SeoGuideCatalogQualityTest`
- New SEO analytics pipeline:
  - `POST /api/seo/events` (track guide view / CTA click / new-case from guide)
  - `GET /api/seo/kpi?days=N` (funnel and top-guide aggregation)
- Internal KPI page:
  - `/seo/kpi` (noindex) for quick operational checks
- Persistence:
  - `seo_events` table via `V8__create_seo_events.sql`

## 4) Free vs Paid Output
- Free:
  - validation visibility
  - downloadable summary guide PDF with watermark
- Paid:
  - submission ZIP download
  - non-watermarked summary PDF

## 5) Key Files to Start From
- Validation rules:
  - `src/main/java/com/example/demo/dispute/service/ValidationService.java`
- Auto-fix:
  - `src/main/java/com/example/demo/dispute/service/AutoFixService.java`
- Upload service and file cap:
  - `src/main/java/com/example/demo/dispute/service/EvidenceFileService.java`
  - `src/main/java/com/example/demo/dispute/persistence/EvidenceFileRepository.java`
- Web flow/controller:
  - `src/main/java/com/example/demo/dispute/web/WebCaseController.java`
- Upload UI:
  - `src/main/jte/caseUpload.jte`
- Export/free vs paid UI:
  - `src/main/jte/caseExport.jte`
- Security headers:
  - `src/main/java/com/example/demo/support/NoIndexFilter.java`
- Browser case vault:
  - `src/main/resources/static/js/case-vault.js`

## 6) Test Coverage Added Recently
- Policy split and behavior tests:
  - `src/test/java/com/example/demo/dispute/ValidationServiceTest.java`
  - `src/test/java/com/example/demo/dispute/CaseControllerIntegrationTest.java`
- Submission artifacts tests:
  - `src/test/java/com/example/demo/dispute/SubmissionExportServiceIntegrationTest.java`
- Case file-limit guardrail test:
  - `src/test/java/com/example/demo/dispute/CaseFileLimitIntegrationTest.java`

## 7) Last Commits (Most Relevant)
- `c185ea4` Harden case pages and add opt-in recent case vault
- `12d9ecb` Align dispute policy rules and add upload guardrails
- `14ccf2e` Token recovery UX + readiness score + resilient uploads

## 8) How to Resume Quickly on Another Machine
1. Clone and checkout `main`.
2. Install deps:
   - `npm install`
   - `npm run build:css`
3. Run tests:
   - `./gradlew test` (Windows: `.\gradlew.bat test`)
4. Start app:
   - `./gradlew bootRun`
5. Open:
   - `http://localhost:8080`

## 9) What Is Next (Priority)
1. Conversion for target niche:
   - landing copy focused on "upload failed -> recover quickly"
   - show concrete before/after issue counts
2. Stripe payment final integration hardening:
   - webhook replay/idempotency checks
   - production key/config validation page
3. Launch checklist execution:
   - run platform sandbox smoke scenarios (Stripe/Shopify) with real sample evidence sets
   - verify policy-source review date before release tag
4. Agency mode (if B2B expansion starts):
   - multi-case dashboard
   - bulk actions
5. Optional technical hardening:
   - policy-version stamping in validation result
   - richer PDF diagnostics and error-to-fix guidance

## 10) Risk Notes
- No-login token model is good for low-friction MVP, but token leakage remains the main risk.
- File handling is robust for MVP, but policy docs can evolve; keep rule review cadence.
- Revenue target (KRW 1,000,000/month) is realistic only with consistent acquisition loop, not product alone.

## 11) Remote Session Delta (Latest Update)

This section captures what was changed in the latest remote work session so local continuation can start immediately.

### 11.1 What changed (logic hardening)
- Added payment policy snapshot persistence:
  - `payments.policy_version`
  - `payments.required_evidence_snapshot`
  - migration: `src/main/resources/db/migration/V9__add_payment_policy_snapshot.sql`
- Payment gate now checks:
  - export-ready state
  - fresh passed validation
  - missing required evidence (policy-aware)
- On checkout session creation/reuse, snapshot is stored/backfilled on payment row.
- ZIP download gate now checks required evidence against the **paid snapshot** (`missingRequiredEvidenceForPaidExport`) instead of relying on mutable current policy.
- Legacy paid rows without snapshot are handled in compatibility mode (no retroactive lock).
- Stripe webhook verifier now supports multiple `v1` signatures in `Stripe-Signature` header (accept if any one matches).
- Export page UX now hides checkout action when required evidence is missing and shows explicit missing list.
- Payment error message text was aligned with actual allowed states (`READY/PAID/DOWNLOADED`).

### 11.2 Files touched in this delta
- `src/main/java/com/example/demo/dispute/persistence/PaymentEntity.java`
- `src/main/java/com/example/demo/dispute/persistence/PaymentRepository.java`
- `src/main/resources/db/migration/V9__add_payment_policy_snapshot.sql`
- `src/main/java/com/example/demo/dispute/service/PaymentService.java`
- `src/main/java/com/example/demo/dispute/service/StripeWebhookVerifier.java`
- `src/main/java/com/example/demo/dispute/web/WebCaseController.java`
- `src/main/jte/caseExport.jte`
- `src/test/java/com/example/demo/dispute/StripeWebhookVerifierTest.java`
- `src/test/java/com/example/demo/dispute/WebExportPageIntegrationTest.java`

### 11.3 Tests executed after these changes
- `.\gradlew.bat test --tests "*StripeWebhookVerifierTest" --tests "*WebExportPageIntegrationTest" --tests "*SubmissionExportServiceIntegrationTest"` -> `BUILD SUCCESSFUL`
- `.\gradlew.bat test` -> `BUILD SUCCESSFUL`

### 11.4 Local machine resume checklist (recommended)
1. Open repo and verify branch + worktree:
   - `git status --short`
2. Ensure Java 21 path (if not globally configured):
   - `$env:JAVA_HOME='C:\Users\Administrator\chargeback\.jdk\jdk-21.0.10+7'`
   - `$env:Path="$env:JAVA_HOME\bin;$env:Path"`
3. Run tests once:
   - `.\gradlew.bat test`
4. Start app:
   - `.\gradlew.bat bootRun`
5. Quick manual checks:
   - Missing required evidence -> checkout button hidden on `/c/{caseToken}/export`
   - Stripe webhook with multi-`v1` signature still validates
   - Paid ZIP path uses payment-time snapshot gate and does not regress on policy drift

### 11.5 Next concrete tasks
1. Token leak reduction (high priority):
   - remove case token from export filenames and generated artifact text where feasible
   - prefer `publicCaseRef` for external-facing artifacts
2. PDF/A trust messaging:
   - surface heuristic-warning copy in UI when PDF/A check is used
3. True live payment smoke:
   - one deployment-like Stripe checkout/webhook E2E
4. Merchant dashboard dry-run:
   - one Stripe/Shopify upload dry-run with generated ZIP

### 11.6 Notes for handoff safety
- Current worktree is intentionally non-clean and includes prior session changes (SEO/explanation tracks plus this payment/webhook hardening).
- Do not reset/revert unrelated modified files when continuing; continue from current state.

