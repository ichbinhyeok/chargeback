# Beta Manual Test - 2026-03-09

## Environment

- App base URL: `https://soft-pandas-shout.loca.lt`
- Billing provider under test: `Lemon Squeezy` test mode
- Retention: `7 days`
- Contact / refund review: `shinhyeok22@gmail.com`

## Scenario 1: Shopify PDF/A required

- Case token: `case_f8074e1330e34a6ab3a86995501df2e9`
- Input set:
  - `scanner_export_non_pdfa.pdf`
  - `delivery_proof.pdf`
  - `customer_profile.pdf`
- Result:
  - Initial validation blocked on `ERR_SHPFY_PDF_NOT_PDFA`
  - `Run Auto-Fix` cleared the blocker
  - Free watermarked summary PDF downloaded successfully
  - Paid export unlocked after signed webhook simulation
  - ZIP, paid summary PDF, and explanation TXT all downloaded successfully

## Scenario 2: Stripe total pages over limit

- Case token: `case_597a1da1385f422fa4af7f9ef50fbac6`
- Input set:
  - `delivery_dump.pdf`
  - `receipt.pdf`
  - `customer_profile.pdf`
- Result:
  - Initial validation blocked on `ERR_STRIPE_TOTAL_PAGES`
  - Auto-fix did not resolve the case
  - Export stayed blocked
  - Paid checkout was not available
  - Manual trim guidance rendered correctly

## Scenario 3: Missing required evidence

- Case token: `case_77d00b135a79418493734092ade17c2c`
- Input set:
  - `Screenshot_2026-02-09_1419.png`
  - `timeline_summary.pdf`
- Result:
  - Validation passed format checks
  - Required evidence stayed incomplete
  - Free watermarked summary PDF downloaded successfully
  - Paid checkout stayed blocked
  - Direct `POST /pay` also redirected back with `Missing required evidence before payment`

## Scenario 4: GIF auto-convert to JPEG

- Case token: `case_4867057a3bb34efab5c070eac11dd98d`
- Input set:
  - `receipt_capture.gif`
  - `delivery_proof.pdf`
  - `customer_profile.pdf`
- Result before fix:
  - Upload correctly showed `Auto-convert to JPEG`
  - Stored file was written as `.jpg`
  - Export page still treated the case as outside paid beta scope
- Fix applied:
  - Beta eligibility now accepts supported upload normalization, not only `AUTO_FIX`
  - Export page wording now separates `file checks passed` from `checkout-ready`
- Verification:
  - Added integration test coverage
  - Full `./gradlew.bat test` passed

## Findings

1. Supported auto-fix flow is strong for documented blockers like Shopify PDF/A.
2. Unresolved Stripe page-limit cases correctly stay blocked and do not expose checkout.
3. Missing required evidence is enforced at both UI and server payment endpoint layers.
4. Free summary PDF currently unlocks on minimum core evidence coverage, not full required evidence completion.
5. External Lemon checkout page loaded correctly, but automated submit hit anti-bot / checkout automation limits. App-side paid unlock was still verified through a signed webhook simulation.

## Artifacts

- Free summary PDF: `.tmp/beta-manual-20260309/shopify-pdfa-free-summary.pdf`
- Paid summary PDF: `.tmp/beta-manual-20260309/shopify-pdfa-paid-summary.pdf`
- Submission ZIP: `.tmp/beta-manual-20260309/shopify-pdfa-submission.zip`
- Explanation TXT: `.tmp/beta-manual-20260309/shopify-pdfa-explanation.txt`
- Missing-evidence free summary PDF: `.tmp/beta-manual-20260309/missing-required-free-summary.pdf`
