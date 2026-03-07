# Synthetic Evidence Sets

These files are fictional demo artifacts for product testing only.

- Every document is synthetic and marked as demo-only.
- No file should be used in a real dispute.
- The goal is to test the product flow when you do not have real merchant cases yet.
- The newer sets intentionally look messier than the original clean PDF examples.

## File types included

- `PDF` for clean exports and timelines
- `PNG` for phone screenshots and screen captures
- `JPEG` for camera photos, scans, and oversized image cases

## Output location

Generated demo files are written to:

`output/pdf/synthetic-evidence-sets/`

If you move to another machine, assume this folder needs to be regenerated.

Each scenario folder contains:

- mixed `PDF / PNG / JPEG` files
- `scenario.json` mapping each file to an `EvidenceType`
- a generated root `README.md` with scenario summaries

## Clean baseline sets

1. `stripe_product_not_received`
   Mostly clean exported PDFs. Useful as a happy-path reference.

2. `stripe_fraudulent_digital_access`
   Mostly clean PDFs with digital usage logs.

3. `stripe_credit_not_processed`
   Clean refund-focused export set.

4. `shopify_product_not_received`
   Clean Shopify PDF export set.

5. `shopify_product_unacceptable`
   Clean Shopify quality-issue PDF set.

## Messy mixed-format sets

6. `stripe_inr_phone_gallery_mix`
   Phone screenshots, one camera photo, and one PDF timeline. Closer to what a real merchant may actually have.

7. `shopify_oversized_phone_photos`
   Oversized PNG and JPEG inputs designed to stress Shopify image-size handling and compression.

8. `edge_camera_scan_credit_bundle`
   Refund-dispute bundle made of phone photos, scan-like JPEGs, and inbox screenshots.

## Failure and repair sets

9. `edge_missing_required_minimal`
   Small realistic set that can pass format checks while still missing required evidence.

10. `edge_duplicate_order_receipt`
   Merchant uploaded both a receipt screenshot and a receipt photo for the same order.

11. `edge_manual_mapping_needed`
   Generic filenames like `document_01.png` and `document_02.jpeg`.

12. `edge_split_chat_screenshots`
   One conversation split across three screenshot files.

## Why these are more useful

These newer sets are intentionally closer to cases where the product helps:

- files are not neatly exported already
- filenames are weak or generic
- evidence arrives in mixed formats
- one evidence type is split across many screenshots
- image files are too large or redundant

## Regeneration

Run:

```powershell
$env:PATH='C:\Users\Administrator\chargeback\.nodejs;' + $env:PATH
npm run generate:demo-evidence
```

On a normal home machine with Node already installed, `npm run generate:demo-evidence` is enough.

## How to use

1. Pick one scenario folder.
2. Upload the files into the app in whatever order feels natural.
3. Map each file to the evidence type shown in `scenario.json`.
4. Run validation, auto-fix, explanation draft, and export.
5. Note where the workflow feels unclear or unrealistic.

For a faster manual drill sequence, use `docs/dirty-case-test-runbook.md`.

## First upload walkthrough

1. Open `/`.
2. Click `Start Free Validation`.
3. Create the case. No files are uploaded on the `/new` page.
4. On the dashboard, click `Upload Evidence`.
5. On the upload page, click `Browse Files`.
6. In the mapping modal, confirm or change the evidence type for each file.
7. Click `Upload Files`.
8. The app redirects to `Validate & Fix` after validation completes.
9. `Format checks passed` is not the same as export-ready. Export is only ready when required evidence is complete.

## Edge-case drills

1. `edge_missing_required_minimal`
   Confirm that one clean screenshot can still leave the case far from export-ready.

2. `edge_duplicate_order_receipt`
   Confirm that duplicate receipt evidence is surfaced and can be auto-fixed or merged.

3. `edge_manual_mapping_needed`
   Confirm that the mapping modal stays understandable with weak filenames.

4. `edge_split_chat_screenshots`
   Confirm that multiple screenshots for one conversation still feel manageable.

5. `shopify_oversized_phone_photos`
   Confirm that large PNG/JPEG uploads expose the intended Shopify size behavior.

## What this validates

- likely raw file formats users really upload
- evidence-type mapping UX
- validation and export flow
- how the product behaves on messy bundles instead of only clean exports

## What this does not validate

- real merchant upload habits at scale
- real Stripe or Shopify dashboard acceptance
- actual dispute win rates
