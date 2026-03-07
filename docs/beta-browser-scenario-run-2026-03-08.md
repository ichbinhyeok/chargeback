# Beta Browser Scenario Run - 2026-03-08

## Scope

- App run: local `bootRun`
- Scenario generator: `scripts/generate_beta_failure_scenarios.mjs`
- Scenario output: `output/pdf/beta-failure-scenarios-2026-03-08`
- Browser upload root: `C:/Users/tlsgu/AppData/Local/Programs/Antigravity/beta-failure-scenarios-2026-03-08`
- Download output: `.tmp/beta-manual-20260308`
- Render verification output: `output/pdf/render-checks-20260308`

## Policy sources checked

- Stripe dispute evidence best practices:
  - https://docs.stripe.com/disputes/best-practices
  - Stripe states only `PDF`, `JPEG`, or `PNG` are accepted, combined file size must be `<= 4.5 MB`, and combined page count must be `< 50`.
- Stripe dispute response form guidance:
  - https://docs.stripe.com/disputes/responding
  - Stripe states one file per evidence type, combined size `4.5 MB`, Mastercard combined page limit `19`, and no external content links.
- Shopify chargebacks in admin:
  - https://help.shopify.com/en/manual/payments/chargebacks/chargebacks-in-admin
  - Shopify states only `PDF`, `JPEG`, or `PNG`, PDFs must be `PDF/A`, no PDF portfolios, each PDF `< 50` pages, each evidence file `<= 2 MB`, combined evidence `<= 4 MB`, one file per evidence type, and no external links.

## Important policy nuance

- Stripe's File Upload API documentation lists `dispute_evidence` uploads up to `5 MB`.
- Stripe's dispute Dashboard guidance and evidence best-practice pages still say the combined evidence packet should stay within `4.5 MB`.
- This product currently aligns to the stricter `4.5 MB` Dashboard/evidence-submission guidance, which is the safer rule for merchants assembling a counter package.

## Persona runs

### 1. Shopify ops manager with scanner-exported receipt PDF

- Persona:
  - A merchant assistant scans a paper receipt and exports a plain PDF that Shopify rejects with `PDF/A required`.
- Scenario:
  - `shopify_payments_pdfa_required_ready`
- Flow:
  - `SHOPIFY -> SHOPIFY_PAYMENTS_CHARGEBACK -> PRODUCT_NOT_RECEIVED`
- Uploaded files:
  - `scanner_export_non_pdfa.pdf -> ORDER_RECEIPT`
  - `delivery_proof.pdf -> FULFILLMENT_DELIVERY`
  - `customer_profile.pdf -> CUSTOMER_DETAILS`
- Pre-fix result:
  - state: `BLOCKED`
  - issues: `ERR_SHPFY_PDF_NOT_PDFA`
- Post-fix result:
  - state: `READY`
  - validation source: `AUTO_FIX`
  - issues cleared: yes
- Stored files after fix:
  - `scanner_export_non_pdfa_autofix.pdf` `98607 B`
  - `delivery_proof_autofix.pdf` `102185 B`
  - `customer_profile_autofix.pdf` `100717 B`
- Output:
  - free watermarked summary PDF downloaded
  - file: [.tmp/beta-manual-20260308/case_dd9bb2720996482889c26ddadb00a77e-shopify-pdfa-summary.pdf](/C:/Development/chargeback/.tmp/beta-manual-20260308/case_dd9bb2720996482889c26ddadb00a77e-shopify-pdfa-summary.pdf)
- Policy fit:
  - aligned with Shopify `PDF/A` rule
  - export only unlocked after the PDF/A blocker cleared

### 2. Shopify back-office user exporting an Office PDF portfolio

- Persona:
  - A support lead exports several attachments from Office into a portfolio PDF that Shopify won't accept.
- Scenario:
  - `shopify_payments_portfolio_ready`
- Flow:
  - `SHOPIFY -> SHOPIFY_PAYMENTS_CHARGEBACK -> PRODUCT_NOT_RECEIVED`
- Uploaded files:
  - `office_bundle_portfolio.pdf -> ORDER_RECEIPT`
  - `delivery_proof.pdf -> FULFILLMENT_DELIVERY`
  - `customer_profile.pdf -> CUSTOMER_DETAILS`
- Pre-fix result:
  - state: `BLOCKED`
  - issues: `ERR_SHPFY_PDF_NOT_PDFA`, `ERR_SHPFY_PDF_PORTFOLIO`
- Post-fix result:
  - state: `READY`
  - validation source: `AUTO_FIX`
- Stored files after fix:
  - `office_bundle_portfolio_autofix.pdf` `12765 B`
  - `delivery_proof_autofix.pdf` `101278 B`
  - `customer_profile_autofix.pdf` `99974 B`
- Output:
  - free watermarked summary PDF downloaded
  - file: [.tmp/beta-manual-20260308/case_00e9f72ef667431f8055e80001edf105-shopify-portfolio-summary.pdf](/C:/Development/chargeback/.tmp/beta-manual-20260308/case_00e9f72ef667431f8055e80001edf105-shopify-portfolio-summary.pdf)
- Policy fit:
  - aligned with Shopify `no PDF portfolios` and `PDF/A` rules
  - export stayed locked until both blockers were cleared

### 3. Phone-only merchant with a huge camera-roll receipt photo

- Persona:
  - A solo merchant uploads a large iPhone photo of the receipt because they don't have the original PDF.
- Scenario:
  - `shopify_payments_oversized_receipt_photo`
- Flow:
  - `SHOPIFY -> SHOPIFY_PAYMENTS_CHARGEBACK -> PRODUCT_NOT_RECEIVED`
- Uploaded files:
  - `iphone_receipt_photo.png -> ORDER_RECEIPT`
  - `delivery_proof.pdf -> FULFILLMENT_DELIVERY`
  - `customer_profile.pdf -> CUSTOMER_DETAILS`
- Pre-fix result:
  - state: `BLOCKED`
  - issues: `ERR_SHPFY_FILE_TOO_LARGE`, `ERR_SHPFY_PDF_NOT_PDFA`
- Post-fix result:
  - state: `READY`
  - validation source: `AUTO_FIX`
- Stored files after fix:
  - `iphone_receipt_photo_autofix.jpg` `794013 B`
  - `delivery_proof_autofix.pdf` `103711 B`
  - `customer_profile_autofix.pdf` `100310 B`
- Output:
  - free watermarked summary PDF downloaded
  - file: [.tmp/beta-manual-20260308/case_9c001c7d848241438009dfe53124358a-shopify-oversized-photo-summary.pdf](/C:/Development/chargeback/.tmp/beta-manual-20260308/case_9c001c7d848241438009dfe53124358a-shopify-oversized-photo-summary.pdf)
- Policy fit:
  - aligned with Shopify `<= 2 MB per evidence file`
  - the oversized PNG ended as a sub-2MB JPEG before export unlocked

### 4. Shopify user who pasted portal/help-center exports with links

- Persona:
  - A merchant exports a help-center page to PDF and Shopify rejects it because the PDF still contains external links.
- Scenario:
  - `shopify_payments_external_links_combo_ready`
- Flow:
  - `SHOPIFY -> SHOPIFY_PAYMENTS_CHARGEBACK -> PRODUCT_NOT_RECEIVED`
- Uploaded files:
  - `merchant_help_center_export.pdf -> ORDER_RECEIPT`
  - `delivery_proof.pdf -> FULFILLMENT_DELIVERY`
  - `customer_profile.pdf -> CUSTOMER_DETAILS`
- Pre-fix result:
  - state: `BLOCKED`
  - issues: `ERR_SHPFY_LINK_DETECTED`, `ERR_SHPFY_PDF_NOT_PDFA`
- Post-fix result:
  - state: `READY`
  - validation source: `AUTO_FIX`
- Stored files after fix:
  - `merchant_help_center_export_autofix.pdf` `14864 B`
  - `delivery_proof_autofix.pdf` `104499 B`
  - `customer_profile_autofix.pdf` `102120 B`
- Output:
  - free watermarked summary PDF downloaded
  - file: [.tmp/beta-manual-20260308/case_f91c73fad6b347619bfa91f297560d00-shopify-links-summary.pdf](/C:/Development/chargeback/.tmp/beta-manual-20260308/case_f91c73fad6b347619bfa91f297560d00-shopify-links-summary.pdf)
- Policy fit:
  - aligned with Shopify `no external resources` rule
  - export stayed locked until the link-bearing PDF was normalized

### 5. Stripe merchant dealing with Mastercard's shorter page limit

- Persona:
  - An ops analyst exports a long carrier event log for a Mastercard dispute and gets blocked on page count.
- Scenario:
  - `stripe_mastercard_19_page_autofix_ready`
- Flow:
  - `STRIPE -> STRIPE_DISPUTE -> PRODUCT_NOT_RECEIVED -> MASTERCARD`
- Uploaded files:
  - `carrier_event_log.pdf -> FULFILLMENT_DELIVERY`
  - `receipt.pdf -> ORDER_RECEIPT`
  - `customer_profile.pdf -> CUSTOMER_DETAILS`
- Pre-fix result:
  - state: `BLOCKED`
  - issues: `ERR_STRIPE_MC_19P`
- Post-fix result:
  - state: `READY`
  - validation source: `AUTO_FIX`
- Stored files after fix:
  - `carrier_event_log_autofix.pdf` `11039 B`, `17 pages`
  - `receipt.pdf` `1 page`
  - `customer_profile.pdf` `1 page`
- Output:
  - free watermarked summary PDF downloaded
  - file: [.tmp/beta-manual-20260308/case_cec7296670914e3d9f72e558077a80c9-stripe-mastercard-summary.pdf](/C:/Development/chargeback/.tmp/beta-manual-20260308/case_cec7296670914e3d9f72e558077a80c9-stripe-mastercard-summary.pdf)
- Policy fit:
  - aligned with Stripe `19-page combined maximum for Mastercard`
  - auto-fix only unlocked export after the packet dropped to `19 total pages`

### 6. Stripe user with a scanner TIFF delivery proof

- Persona:
  - A merchant receives delivery proof from a scanner or copier as TIFF and Stripe refuses the file type.
- Scenario:
  - `stripe_scanner_tiff_auto_convert`
- Flow:
  - `STRIPE -> STRIPE_DISPUTE -> PRODUCT_NOT_RECEIVED -> VISA`
- Uploaded files:
  - `scanner_delivery_proof.tiff -> FULFILLMENT_DELIVERY`
  - `receipt.pdf -> ORDER_RECEIPT`
  - `customer_profile.pdf -> CUSTOMER_DETAILS`
- Pre-fix result:
  - state: `READY`
  - issues: none
  - modal showed `Auto-convert to JPEG`
- Stored files after upload:
  - `scanner_delivery_proof_autoconvert.jpg` `45958 B`
  - `receipt.pdf` `1246 B`
  - `customer_profile.pdf` `1269 B`
- Output:
  - free watermarked summary PDF downloaded
  - file: [.tmp/beta-manual-20260308/case_1d62512f2b61465bb9e853fedaadcb77-stripe-tiff-summary.pdf](/C:/Development/chargeback/.tmp/beta-manual-20260308/case_1d62512f2b61465bb9e853fedaadcb77-stripe-tiff-summary.pdf)
- Policy fit:
  - aligned with Stripe accepting only `PDF`, `JPEG`, or `PNG`
  - the TIFF was converted before stored validation, so export only unlocked on an accepted final format

### 7. Stripe user with a genuinely too-long evidence packet

- Persona:
  - A merchant pastes a full carrier export without trimming and expects the tool to save them.
- Scenario:
  - `stripe_total_pages_over_limit`
- Flow:
  - `STRIPE -> STRIPE_DISPUTE -> PRODUCT_NOT_RECEIVED -> VISA`
- Uploaded files:
  - `delivery_dump.pdf -> FULFILLMENT_DELIVERY`
  - `receipt.pdf -> ORDER_RECEIPT`
  - `customer_profile.pdf -> CUSTOMER_DETAILS`
- Pre-fix result:
  - state: `BLOCKED`
  - issues: `ERR_STRIPE_TOTAL_PAGES`
- Post-fix result:
  - state: `BLOCKED`
  - validation source: `AUTO_FIX`
  - issues still present: `ERR_STRIPE_TOTAL_PAGES`
- Stored files after fix attempt:
  - `delivery_dump.pdf` stayed `52 pages`
  - no free summary PDF link
- Output:
  - no downloadable free summary PDF
- Policy fit:
  - correct behavior
  - the product did not unlock output for an unresolved Stripe page-limit violation

## Free summary PDF verification

- Verified all downloaded summaries with `verifySummaryPdfRender`
- Result for every downloaded PDF:
  - `PAGES=1`
  - `HAS_GUIDE=true`
  - `HAS_FREE=true`
  - `HAS_WATERMARKED=true`
- Rendered PNGs:
  - [case_00e9f72ef667431f8055e80001edf105-shopify-portfolio-summary-page-1.png](/C:/Development/chargeback/output/pdf/render-checks-20260308/case_00e9f72ef667431f8055e80001edf105-shopify-portfolio-summary-page-1.png)
  - [case_1d62512f2b61465bb9e853fedaadcb77-stripe-tiff-summary-page-1.png](/C:/Development/chargeback/output/pdf/render-checks-20260308/case_1d62512f2b61465bb9e853fedaadcb77-stripe-tiff-summary-page-1.png)
  - [case_9c001c7d848241438009dfe53124358a-shopify-oversized-photo-summary-page-1.png](/C:/Development/chargeback/output/pdf/render-checks-20260308/case_9c001c7d848241438009dfe53124358a-shopify-oversized-photo-summary-page-1.png)
  - [case_cec7296670914e3d9f72e558077a80c9-stripe-mastercard-summary-page-1.png](/C:/Development/chargeback/output/pdf/render-checks-20260308/case_cec7296670914e3d9f72e558077a80c9-stripe-mastercard-summary-page-1.png)
  - [case_dd9bb2720996482889c26ddadb00a77e-shopify-pdfa-summary-page-1.png](/C:/Development/chargeback/output/pdf/render-checks-20260308/case_dd9bb2720996482889c26ddadb00a77e-shopify-pdfa-summary-page-1.png)
  - [case_f91c73fad6b347619bfa91f297560d00-shopify-links-summary-page-1.png](/C:/Development/chargeback/output/pdf/render-checks-20260308/case_f91c73fad6b347619bfa91f297560d00-shopify-links-summary-page-1.png)

## Product conclusions from this run

1. The strongest monetizable flows are now real Shopify upload failures:
   - PDF/A
   - portfolio PDFs
   - external-link PDFs
   - oversized image receipts
2. TIFF auto-convert is credible for scanner/copier workflows and works without forcing manual cleanup.
3. The Mastercard 19-page reduction path works and is worth highlighting more aggressively in Stripe messaging.
4. The product still behaves correctly when it cannot safely auto-fix a real blocker:
   - the 52-page Stripe packet stayed blocked
   - no free summary output link appeared

## Highest-priority follow-up

1. Add a first-class scenario and UX path for `Stripe 4.5MB vs File API 5MB` confusion so merchants understand why the stricter rule is enforced.
2. Extend page-limit auto-fix beyond exact duplicate/blank pages into safe excerpting suggestions for long carrier exports.
3. Add Shopify-specific messaging that a fix may touch multiple PDF constraints at once:
   - PDF/A
   - portfolio structure
   - external links
