# Chargeback Evidence Pack Builder

Spring Boot SaaS MVP for Stripe/Shopify dispute evidence packaging.

## Project Identity (Current)

- This product is an `upload-failure recovery and submission-readiness engine` for dispute evidence.
- It helps merchants who failed (or are likely to fail) evidence upload due to formatting and policy constraints.
- It is not a dispute-outcome predictor and does not promise win-rate improvement.
- It does not provide legal advice.

## Current Positioning

- Primary promise: `turn messy merchant uploads into an upload-ready dispute evidence pack`.
- Practical scope:
  - Collect and organize evidence files
  - Show reason-code checklist (required/recommended evidence + missing warnings)
  - Provide platform-aware reason preset input at case creation
  - Validate against platform constraints
  - Auto-fix supported formatting issues
  - Export structured submission artifacts
- Positioning line:
  - `Upload-ready dispute evidence pack builder for Stripe/Shopify merchants`

Core flow:
1. Create case
2. Set context (platform, scope, optional reason code/network)
3. Review reason-code evidence checklist
4. Upload evidence files by type
5. Validate against platform rules
6. Generate dispute explanation draft (editable)
7. Run auto-fix (per-type merge + Shopify oversized image compression + Shopify PDF/A conversion + PDF portfolio flatten + Stripe oversized PDF compression + PDF external-link removal)
8. Pay (Stripe Checkout) only when validation is fresh and required evidence coverage is complete
9. Download submission ZIP (same required-evidence gate), explanation TXT, and one-page guide PDF

## Policy Baseline (as implemented)

- Stripe disputes:
  - Accepted format: PDF, JPEG, PNG
  - One file per evidence type
  - No external links in evidence files
  - Total upload size <= 4.5 MB
  - Total pages < 50
  - Mastercard: total pages <= 19

- Shopify Payments chargebacks:
  - Accepted format: PDF, JPEG, PNG
  - One file per evidence type
  - No external links
  - Each file <= 2 MB
  - Total upload size <= 4 MB
  - Each PDF < 50 pages
  - No PDF portfolio
  - PDF/A required for PDF files

- Shopify Credit disputes:
  - Accepted format: PDF, JPEG, PNG
  - Total upload size <= 4.5 MB
  - Each PDF < 50 pages

No-login access model:
- Access control is case-token based (`/c/{caseToken}`).
- Recent case links are stored only when users opt in on trusted devices (browser localStorage, 30-day TTL).
- Users can download an access key text file and rotate token if a link is exposed.

## Local Run (Dev Profile)

Requirements:
- JDK 21
- Node.js 20+ (for Tailwind CSS build)

Run:

```powershell
npm install
npm run build:css
.\gradlew.bat clean test
.\gradlew.bat bootRun
```

Open:
- `http://localhost:8080/`

Synthetic messy demo cases:
- `npm run generate:demo-evidence`
- see `docs/synthetic-evidence-sets.md`
- see `docs/dirty-case-test-runbook.md`

Dev defaults:
- H2 in-memory DB
- evidence storage: `./data/evidence`
- retention: 30 days default (extended to due date + buffer when later)

## Frontend Assets (Tailwind)

Build CSS:

```powershell
npm run build:css
```

Watch mode (during UI edits):

```powershell
npm run watch:css
```

Output:
- `src/main/resources/static/css/app.css`

## Environment Variables

General:
- `APP_STORAGE_ROOT` (default `./data/evidence`)
- `APP_CASE_MAX_FILES` (default `100`)
- `APP_RETENTION_DAYS` (default `30`)
- `APP_RETENTION_DUE_DATE_BUFFER_DAYS` (default `7`)
- `APP_RETENTION_CRON` (default `0 30 3 * * *`)
- `APP_API_ENFORCE_CASE_TOKEN` (default `true`)
- `APP_TRACE_HEADER_NAME` (default `X-Trace-Id`)
- `APP_PUBLIC_BASE_URL` (default `http://localhost:8080`)
- `APP_SEO_GUIDES_PATH` (default `seo/guides-v1.json`)

Billing:
- `APP_BILLING_AMOUNT_CENTS` (default `1900`)
- `APP_BILLING_CURRENCY` (default `usd`)
- `APP_BILLING_SUCCESS_URL_TEMPLATE` (default `http://localhost:8080/c/{caseToken}/export?payment=success`)
- `APP_BILLING_CANCEL_URL_TEMPLATE` (default `http://localhost:8080/c/{caseToken}/export?payment=cancelled`)
- `APP_BILLING_PROVIDER` (default `stripe`, allowed: `stripe`, `lemonsqueezy`)
- `STRIPE_SECRET_KEY`
- `STRIPE_WEBHOOK_SECRET`
- `LEMONSQUEEZY_API_KEY`
- `LEMONSQUEEZY_WEBHOOK_SECRET`
- `LEMONSQUEEZY_STORE_ID`
- `LEMONSQUEEZY_VARIANT_ID`

## Billing Webhooks

Endpoints:
- `POST /webhooks/stripe`
- `POST /webhooks/lemonsqueezy`

Expected events:
- `checkout.session.completed`
- `order_created`

Signature:
- Verified with `Stripe-Signature` header using `STRIPE_WEBHOOK_SECRET`.
- Verified with `X-Signature` header using `LEMONSQUEEZY_WEBHOOK_SECRET`.

## Docker Compose (Prod Profile)

Run:

```powershell
docker compose up --build
```

Services:
- `app` (Spring Boot, profile `prod`)
- `postgres` (PostgreSQL 16)

Prod defaults:
- datasource: postgres
- `spring.jpa.hibernate.ddl-auto=validate`
- evidence storage mounted to persistent volume (`/data/evidence`)

## User Flow URLs

- `/` landing
- `/open-case` token/url resume endpoint
- `/guides` public evidence guides (indexable)
- `/guides/{platform}` platform hub pages with `Upload/Error Fix` + `Reason-Code` sections (indexable)
- `/guides/{platform}/{reasonCode}` detail pages for reason guides and error-message guides (indexable)
- `/seo/kpi` internal SEO KPI dashboard (`noindex`)
- `/terms` terms of use
- `/privacy` privacy policy
- `/new` create case
- `/c/{caseToken}` dashboard
- `/c/{caseToken}/upload` upload
- `/c/{caseToken}/validate` validation result
- `/c/{caseToken}/explanation` dispute explanation draft editor
- `/c/{caseToken}/report` case report
- `/c/{caseToken}/export` paywall + download
- `POST /c/{caseToken}/pay` start provider checkout (Stripe/Lemon Squeezy)
- `/c/{caseToken}/download/submission.zip` paid only
- `/c/{caseToken}/download/summary.pdf` free with watermark, paid without watermark
- `/c/{caseToken}/download/explanation.txt` explanation draft text download
- `/c/{caseToken}/access-key.txt` case recovery key download
- `/c/{caseToken}/rotate-token` rotate token and invalidate old URL
- `POST /api/seo/events` SEO funnel event tracking endpoint
- `GET /api/seo/kpi?days=7` SEO KPI aggregation endpoint
- `/robots.txt` crawler policy
- `/sitemap.xml` public sitemap

## SEO Guide Catalog (pSEO Data Source)

- Guide pages are data-driven from `src/main/resources/seo/guides-v1.json`.
- Current catalog target volume: 52 pages (32 reason + 20 error).
- Each entry defines:
  - platform + slug
  - `guideType` (`reason` or `error`)
  - title/meta description
  - target search phrases (error-intent long-tail queries)
  - checklist / common failures / next steps
  - explanation draft sample lines
  - source links and FAQ
- `SeoController` loads this catalog at startup and auto-populates:
  - `/guides`
  - `/guides/{platform}`
  - `/guides/{platform}/{slug}`
  - `/sitemap.xml`
- Regenerate catalog:
  - `$env:PATH = "c:\\Users\\Administrator\\chargeback\\.nodejs\\node-v20.18.0-win-x64;" + $env:PATH`
  - `node scripts/generate_seo_guides.mjs`
- Quality gate test:
  - `.\gradlew.bat test --tests "*SeoGuideCatalogQualityTest"`

## Router No-Match Loop (What "No-Match" Means)

- `No-Match` means: user-entered error text did not strongly match an existing guide.
- This is a useful signal, not a failure:
  - it captures real user error wording you did not cover yet,
  - it feeds the next pSEO guide backlog with evidence.

Current behavior:
- `/guides/router` tries best-match routing first.
- If match score is strong:
  - error-guide query -> `/new?src=guide...`
  - reason-guide query -> `/guides/{platform}/{slug}`
- If no strong match:
  - route to direct fix flow (`/new?src=guide_router_nomatch...`) when platform is known,
  - preserve original query text for attribution and analytics.

Tracked events for this loop:
- `guide_router_nomatch`
- `new_case_view_from_router_nomatch`
- `case_created_from_router_nomatch`

KPI additions:
- no-match volume
- no-match -> new-case-view rate
- no-match -> case-created rate
- top no-match queries (`routerOpportunities`) with suggested slug/title for new guide creation

## Retention / Privacy

- Default retention is 30 days.
- If due date is set, expiry extends to `due date + APP_RETENTION_DUE_DATE_BUFFER_DAYS` when that is later than created+retention.
- User can delete case immediately from dashboard.

## Manual QA Checklist

1. Create Stripe case and open dashboard by token URL.
2. Upload PDF/JPEG/PNG files successfully.
3. Upload unsupported format and confirm validation fails.
4. Upload two files for same evidence type and confirm FIXABLE issue appears.
5. Run auto-fix and confirm merge/compression behavior is applied when issues are fixable.
6. Run validation and confirm READY/BLOCKED transitions.
7. Verify unpaid export route shows paywall.
8. Confirm payment/ZIP are blocked when required evidence types are missing.
9. Complete Stripe checkout and confirm export unlocks.
10. Download `submission.zip` and verify normalized filenames.
11. Download `summary.pdf` and verify case/issue summary contents.
12. Delete case and confirm files and DB records are removed.
13. Verify retention cleanup removes expired cases.

## Prelaunch Checklist (No Billing)

1. Run `npm run build:css` and verify `app.css` is updated.
2. Run `.\gradlew.bat clean test` and confirm all tests pass.
3. Start app with `.\gradlew.bat bootRun` and perform one full flow:
   - create case
   - upload valid files
   - validate
   - run auto-fix on a duplicate-type case
   - delete case
4. Confirm export page shows validation guidance when case state is not `READY`.
5. Confirm no Korean/garbled text exists in `src/main`, `src/test`, `README.md`.
6. Confirm retention notice is visible in Terms/Privacy and dashboard flow.
7. Confirm prod config variables are set for storage and database before deploy.
8. Run `/guides/router` no-match query and confirm direct-fix redirect to `/new?src=guide_router_nomatch...`.
9. Check `/seo/kpi` and confirm router no-match metrics are populated.

## Notes

- This product helps formatting and organizing evidence files.
- It does not provide legal advice and does not guarantee dispute outcomes.
- All API/web responses include trace id header (`X-Trace-Id` by default) for support and incident tracing.
- Case pages (`/c/*`), API (`/api/*`), and webhook (`/webhooks/*`) responses include `X-Robots-Tag: noindex, nofollow, noarchive`.
- Case/API/webhook responses also send `Cache-Control: no-store` and `Referrer-Policy: no-referrer`.
- Policy source tracking is documented in `POLICY_SOURCE_TRACKING_2026-03-06.md`.
