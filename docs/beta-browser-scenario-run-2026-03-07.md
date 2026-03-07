# Beta Browser Scenario Run - 2026-03-07

## Generated scenario set

- Source generator: `scripts/generate_beta_failure_scenarios.mjs`
- NPM command: `npm run generate:beta-failure-scenarios`
- Repo output: `output/pdf/beta-failure-scenarios-2026-03-07`
- Browser fixture copy: `C:/Users/tlsgu/AppData/Local/Programs/Antigravity/beta-failure-scenarios-2026-03-07`

## Scenario 1 - OCR-ready free summary

- Case token: `case_26413077e1614d11beea7c92e6db4d28`
- Flow: `Stripe -> STRIPE_DISPUTE -> PRODUCT_NOT_RECEIVED`
- Uploaded files:
  - `document_01.png`
  - `customer_profile.pdf`
  - `delivery_proof.pdf`
  - `chat_followup.png`

### What happened

- `document_01.png` mapped to `ORDER_RECEIPT` via `OCR text match`
- `customer_profile.pdf` mapped to `CUSTOMER_DETAILS` via `Content match`
- `delivery_proof.pdf` mapped to `FULFILLMENT_DELIVERY` via `Content match`
- `chat_followup.png` incorrectly mapped to `FULFILLMENT_DELIVERY` via `OCR text match`

### Result

- Auto suggestion failed to unlock export because the chat screenshot was misclassified.
- Export remained unavailable in the first run.

### Product finding

- OCR currently overweights shipping terms such as `tracking`, `delivered`, and `carrier`.
- A support chat screenshot that contains shipping language can be pushed into `FULFILLMENT_DELIVERY` even when the user intent is clearly `CUSTOMER_COMMUNICATION`.

## Scenario 1b - Same pack with one manual correction

- Case token: `case_974cb02d560b4d74856bf305c42957ed`
- Same files as Scenario 1
- Manual action:
  - changed `chat_followup.png` from `FULFILLMENT_DELIVERY` to `CUSTOMER_COMMUNICATION`

### Result

- Validation reached `READY`
- Export page loaded successfully
- Export page showed `Download Watermarked Guide PDF`
- Free summary route returned a real PDF at `/c/case_974cb02d560b4d74856bf305c42957ed/download/summary.pdf`

### PDF verification

- Downloaded file: `.tmp/beta-manual/case_974cb02d560b4d74856bf305c42957ed-summary.pdf`
- PDFBox text extraction confirmed:
  - `Chargeback Submission Guide`
  - checklist/disclaimer text
- First page content stream confirmed:
  - `FREE VERSION`
  - `WATERMARKED`

## Scenario 2 - Duplicate receipt auto-fix

- Case token: `case_fba2be9839534316a94d251ff241460a`
- Flow: `Stripe -> STRIPE_DISPUTE -> PRODUCT_NOT_RECEIVED`
- Uploaded files:
  - `receipt_a.pdf`
  - `receipt_b.pdf`
  - `customer_profile.pdf`
  - `delivery_proof.pdf`

### What happened

- Both receipt files were correctly suggested as `ORDER_RECEIPT`
- Validate page surfaced the duplicate-type issue
- `Run Auto-Fix` was available and executed

### Result

- Auto-fix finished with `SUCCEEDED`
- Case moved to `READY`
- Validation page showed:
  - `All Checks Passed`
  - `Review Export`

### Product finding

- This is the strongest currently monetizable flow.
- The user sees a real blocker, clicks one button, and gets back to `READY`.

## Scenario 3 - Shopify credit total size over limit

- Case token: `case_71b85a1deca84a298e9a354886d91f3c`
- Flow: `Shopify -> SHOPIFY_CREDIT_DISPUTE`
- Uploaded files:
  - `camera_roll_1.png`
  - `camera_roll_2.png`
  - `customer_capture.png`

### What happened

- Validate page blocked on total evidence size
- `Primary next action` correctly focused the user on the largest file
- `Priority order` listed the top replacement candidates with impact text

### Result

- Messaging was strong:
  - `Reduce one large file next.`
  - `replace or remove 'camera_roll_1.png' (7.31 MB) first`
  - per-file overage impact text was visible

### Auto-fix follow-up

- Running auto-fix did not resolve the issue
- The page returned:
  - `No supported auto-fix issue found`
  - case stayed `BLOCKED`

### Product finding

- The guidance is materially better than before.
- But the product still does not actually fix this common failure mode.
- The message also creates expectation tension because the page advertises Shopify image compression support.

## Scenario 4 - Mixed manual mapping bundle

- Case token: `case_6695680eb16f459ab33212d58b29e74a`
- Flow: `Stripe -> STRIPE_DISPUTE -> PRODUCT_NOT_RECEIVED`
- Uploaded files:
  - `document_01.png`
  - `document_02.png`
  - `tracking_update.pdf`
  - `refund_policy.pdf`
  - `camera_roll_3.png`

### What happened

- All 5 files received non-fallback suggestions
- `document_01.png` mapped to `ORDER_RECEIPT` via OCR
- `document_02.png` mapped to `FULFILLMENT_DELIVERY` via OCR
- `tracking_update.pdf` mapped to `FULFILLMENT_DELIVERY` via content
- `refund_policy.pdf` mapped to `POLICIES` via content
- `camera_roll_3.png` was pushed into `CUSTOMER_DETAILS` because that evidence gap remained open

### Result

- Validate page surfaced two issues:
  - `ERR_STRIPE_MULTI_FILE_PER_TYPE`
  - `ERR_STRIPE_TOTAL_SIZE`
- `Priority order` correctly pointed to `camera_roll_3.png` as enough to clear the size overage

### Product finding

- The mixed bundle no longer collapses into `OTHER_SUPPORTING`.
- That is a real improvement.
- But evidence-gap defaults can still assign noisy images to high-value evidence types when no stronger clue exists.

## Overall conclusions

- Free path works:
  - validation
  - export review
  - watermarked summary PDF
- Duplicate auto-fix works well and feels valuable.
- `Primary next action` plus `Priority order` materially improves blocked flows.
- The biggest remaining product gap is still `common failure that we explain well but do not actually fix`.

## Highest-priority issues from this run

1. Chat screenshots with shipping words can be misclassified by OCR as `FULFILLMENT_DELIVERY`.
2. Shopify and Stripe total-size failures are diagnosed better, but still largely remain manual.
3. Evidence-gap defaults can still over-assign noisy images to required evidence categories.

## Follow-up after fixes

- Rerun OCR-ready pack:
  - Case token: `case_e7dc8d5c039d46f2bb8bba853123cae1`
  - `chat_followup.png` now maps to `CUSTOMER_COMMUNICATION`
  - validate reaches `READY` without manual remapping
  - export shows `Download Watermarked Guide PDF`

- Rerun Shopify credit total-size pack:
  - Case token: `case_f854b4d85e2d4752a5db6214fab0f1bb`
  - validate still starts `BLOCKED`
  - `Priority order` still appears before auto-fix
  - after auto-fix the total-size blocker clears and the case is no longer `BLOCKED`
  - remaining work is evidence composition, not raw file-size failure

- Rerun mixed bundle mapping:
  - Case token: `case_06ed6bb718ff48018cc0a7bfa72b22b3`
  - `document_02.png` now maps to `CUSTOMER_COMMUNICATION`
  - `camera_roll_3.png` now stays `OTHER_SUPPORTING`
  - noisy files no longer get pushed into required evidence types by gap-fill alone

## Expanded browser run after validate guidance and scenario-generator updates

- Scenario set expanded to 10 folders:
  - `gif_auto_convert_free_summary`
  - `support_chat_tracking_conflict`
  - `noise_fallback_manual_review`
  - `stripe_total_size_autofix_then_missing_delivery`
  - `stripe_total_pages_over_limit`
  - `external_link_pdf_autofix`
  - plus the earlier OCR, duplicate, Shopify total-size, and mixed-bundle cases

### GIF auto-convert free summary

- Case token: `case_03224933818d4e32a07f68a097b42f92`
- Flow: `Stripe -> STRIPE_DISPUTE -> PRODUCT_NOT_RECEIVED`
- Uploaded files:
  - `receipt_capture.gif`
  - `customer_profile.pdf`
  - `delivery_proof.pdf`

#### Result

- Upload modal showed `Auto-convert to JPEG`
- Validation reached export-ready state immediately
- Export page showed `Download Watermarked Guide PDF`
- Download saved to `.tmp/beta-manual/case_03224933818d4e32a07f68a097b42f92-gif-summary.pdf`

### Support chat with tracking language

- Case token: `case_0c395a4132b0492e9de8495cd860874a`
- Flow: `Stripe -> STRIPE_DISPUTE -> PRODUCT_NOT_RECEIVED`
- Uploaded files:
  - `receipt.pdf`
  - `customer_profile.pdf`
  - `delivery_proof.pdf`
  - `chat_followup.png`

#### Result

- Upload modal suggested `chat_followup.png -> CUSTOMER_COMMUNICATION`
- Validate reached `Review Export`
- OCR weighting now correctly favors chat/support language over carrier wording

### Noisy fallback manual review

- Case token: `case_0cf5b885e3f04a08a115af84c3fa789e`
- Flow: `Stripe -> STRIPE_DISPUTE -> PRODUCT_NOT_RECEIVED`
- Uploaded files:
  - `receipt.pdf`
  - `chat_thread.png`
  - `camera_noise.png`

#### Result

- Upload modal suggested `camera_noise.png -> OTHER_SUPPORTING`
- Validate still showed missing required evidence, which is correct for this pack
- Noisy files are no longer promoted into required evidence categories by gap-fill alone

### Stripe total size -> auto-fix -> missing delivery

- Case token: `case_60de95add3814e10abd7b18c529d33ca`
- Flow: `Stripe -> STRIPE_DISPUTE -> PRODUCT_NOT_RECEIVED`
- Uploaded files:
  - `camera_roll_receipt.png` mapped manually to `ORDER_RECEIPT`
  - `camera_roll_customer.png` mapped manually to `CUSTOMER_DETAILS`

#### Result

- Validate started in `Issues Detected`
- `Run Auto-Fix` cleared the size blocker
- Validate then showed:
  - `Auto-fix changed: Reduced pack by ...`
  - `Upload delivery proof or carrier tracking page`
- After auto-fix, the case was no longer blocked on format or size. The remaining issue was evidence composition only.

### Stripe total pages over limit

- Case token: `case_652fb7cfc85f440585b23084837bb411`
- Flow: `Stripe -> STRIPE_DISPUTE -> PRODUCT_NOT_RECEIVED`
- Uploaded files:
  - `delivery_dump.pdf` (52 pages)
  - `receipt.pdf`
  - `customer_profile.pdf`

#### Result

- Validate blocked on total page count
- `Primary next action` pointed to replacing one long PDF
- `Priority order` referenced `delivery_dump.pdf` directly

### Stripe total pages over limit rerun after page auto-fix

- Case token: `case_4f0e4136eff24f47816e376a3de6127e`
- Flow: `Stripe -> STRIPE_DISPUTE -> PRODUCT_NOT_RECEIVED`
- Uploaded files:
  - `delivery_dump_duplicates.pdf` (50 pages with duplicate tail pages)
  - `receipt.pdf`
  - `customer_profile.pdf`

#### Result

- Validate now shows `Primary next action -> Run auto-fix next.`
- The supporting copy explicitly says blank pages and exact duplicate PDF pages can be removed automatically
- `Run Auto-Fix` succeeded
- The case moved from `BLOCKED` to `READY`
- Export unlocked and the free summary PDF route remained available
- Download saved to `.tmp/beta-manual/case_4f0e4136eff24f47816e376a3de6127e-page-limit-summary.pdf`

### External-link PDF auto-fix

- Case token: `case_001f0df5a02d49cca5a11a937c350beb`
- Flow: `Stripe -> STRIPE_DISPUTE -> PRODUCT_NOT_RECEIVED`
- Uploaded files:
  - `receipt.pdf`
  - `customer_profile.pdf`
  - `delivery_proof.pdf`
  - `merchant_portal_export.pdf`

#### Result

- Validate started blocked
- `Run Auto-Fix` cleared the external-link blocker
- Export page unlocked and showed `Download Watermarked Guide PDF`
- Download saved to `.tmp/beta-manual/case_001f0df5a02d49cca5a11a937c350beb-external-link-rerun.pdf`

### PDF verification for downloaded free summaries

- Verified with PDFBox source-file probe against:
  - `.tmp/beta-manual/case_03224933818d4e32a07f68a097b42f92-gif-summary.pdf`
  - `.tmp/beta-manual/case_001f0df5a02d49cca5a11a937c350beb-external-link-rerun.pdf`
  - `.tmp/beta-manual/case_4f0e4136eff24f47816e376a3de6127e-page-limit-summary.pdf`
- Both files returned:
  - `PAGES=1`
  - `HAS_GUIDE=true`
  - `HAS_FREE=true`
  - `HAS_WATERMARKED=true`
- The downloaded filenames also include `FREE`, which matches the unpaid summary route.
- Rendered PNG outputs were generated under `output/pdf/render-checks/` via `scripts/verify_summary_pdf_render.ps1`.

## Current conclusion after expanded run

- The product now handles several realistic arrival states end-to-end:
  - invalid-but-decodable image receipt
  - support chat with delivery language
  - noisy camera-roll fallback
  - total-size blocker cleared by auto-fix
  - total-page blocker cleared by duplicate-page cleanup
  - external-link blocker cleared by auto-fix
- The new validate guidance is doing the right job:
  - it now routes blocked-but-auto-fixable issues to `Run auto-fix next`
  - it shows what auto-fix changed
  - it tells the user the next evidence action when the pack is no longer technically blocked
