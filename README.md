# Chargeback Evidence Pack Builder

Spring Boot SaaS MVP for Stripe/Shopify dispute evidence packaging.

Core flow:
1. Create case
2. Upload evidence files by type
3. Validate against platform rules
4. Run auto-fix (per-type merge + Shopify oversized image compression)
5. Pay (Stripe Checkout)
6. Download submission ZIP and one-page guide PDF

No-login access model:
- Access control is case-token based (`/c/{caseToken}`).
- Recent cases are stored in browser localStorage for quick return.
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

Dev defaults:
- H2 in-memory DB
- evidence storage: `./data/evidence`
- retention: 7 days

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
- `APP_RETENTION_DAYS` (default `7`)
- `APP_RETENTION_CRON` (default `0 30 3 * * *`)
- `APP_API_ENFORCE_CASE_TOKEN` (default `true`)
- `APP_TRACE_HEADER_NAME` (default `X-Trace-Id`)
- `APP_PUBLIC_BASE_URL` (default `http://localhost:8080`)

Billing:
- `APP_BILLING_AMOUNT_CENTS` (default `1900`)
- `APP_BILLING_CURRENCY` (default `usd`)
- `APP_BILLING_SUCCESS_URL_TEMPLATE` (default `http://localhost:8080/c/{caseToken}/export?paid=1`)
- `APP_BILLING_CANCEL_URL_TEMPLATE` (default `http://localhost:8080/c/{caseToken}/export?payment=cancelled`)
- `STRIPE_SECRET_KEY`
- `STRIPE_WEBHOOK_SECRET`

## Stripe Webhook

Endpoint:
- `POST /webhooks/stripe`

Expected event:
- `checkout.session.completed`

Signature:
- Verified with `Stripe-Signature` header using `STRIPE_WEBHOOK_SECRET`.

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
- `/guides/{platform}` platform guide hub pages (indexable)
- `/guides/{platform}/{reasonCode}` public guide detail pages (indexable)
- `/terms` terms of use
- `/privacy` privacy policy
- `/new` create case
- `/c/{caseToken}` dashboard
- `/c/{caseToken}/upload` upload
- `/c/{caseToken}/validate` validation result
- `/c/{caseToken}/report` case report
- `/c/{caseToken}/export` paywall + download
- `/c/{caseToken}/download/submission.zip` paid only
- `/c/{caseToken}/download/summary.pdf` free with watermark, paid without watermark
- `/c/{caseToken}/access-key.txt` case recovery key download
- `/c/{caseToken}/rotate-token` rotate token and invalidate old URL
- `/robots.txt` crawler policy
- `/sitemap.xml` public sitemap

## Retention / Privacy

- Default retention is 7 days, then scheduled cleanup removes case and files.
- User can delete case immediately from dashboard.

## Manual QA Checklist

1. Create Stripe case and open dashboard by token URL.
2. Upload PDF/JPEG/PNG files successfully.
3. Upload unsupported format and confirm validation fails.
4. Upload two files for same evidence type and confirm FIXABLE issue appears.
5. Run auto-fix and confirm merge/compression behavior is applied when issues are fixable.
6. Run validation and confirm READY/BLOCKED transitions.
7. Verify unpaid export route shows paywall.
8. Complete Stripe checkout and confirm export unlocks.
9. Download `submission.zip` and verify normalized filenames.
10. Download `summary.pdf` and verify case/issue summary contents.
11. Delete case and confirm files and DB records are removed.
12. Verify retention cleanup removes expired cases.

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

## Notes

- This product helps formatting and organizing evidence files.
- It does not provide legal advice and does not guarantee dispute outcomes.
- All API/web responses include trace id header (`X-Trace-Id` by default) for support and incident tracing.
- Case pages (`/c/*`), API (`/api/*`), and webhook (`/webhooks/*`) responses include `X-Robots-Tag: noindex, nofollow, noarchive`.
