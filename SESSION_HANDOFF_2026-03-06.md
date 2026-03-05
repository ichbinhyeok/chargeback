# Session Handoff (2026-03-06)

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
3. Agency mode (if B2B expansion starts):
   - multi-case dashboard
   - bulk actions
4. Optional technical hardening:
   - policy-version stamping in validation result
   - richer PDF diagnostics and error-to-fix guidance

## 10) Risk Notes
- No-login token model is good for low-friction MVP, but token leakage remains the main risk.
- File handling is robust for MVP, but policy docs can evolve; keep rule review cadence.
- Revenue target (KRW 1,000,000/month) is realistic only with consistent acquisition loop, not product alone.

