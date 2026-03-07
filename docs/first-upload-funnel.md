# First Upload Funnel

This is the shortest path from landing page to first successful upload.

## Click path

1. Open `/`.
2. Click `Start Free Validation`.
3. On `/new`, select platform, product scope, and reason code.
4. Click `Continue to Free Validation`.
5. On the case dashboard, click `Upload Evidence`.
6. On `/upload`, click `Browse Files`.
7. In `Map Files to Evidence Types`, confirm each file type.
8. Click `Upload Files`.
9. The app redirects to `Validate & Fix` after validation completes.

## Important distinctions

- Creating the case does not upload any files.
- Choosing files does not upload them yet.
- Upload starts only after clicking `Upload Files` in the mapping modal.
- `Format checks passed` does not always mean `export-ready`.
- Export becomes ready only when required evidence for the chosen reason is present.

## Best messy starter scenarios

1. `stripe_inr_phone_gallery_mix`
   Best first realistic case. Mixed screenshot, camera photo, and PDF inputs.

2. `edge_manual_mapping_needed`
   Best for testing whether the mapping modal makes sense with bad filenames.

3. `edge_split_chat_screenshots`
   Best for seeing whether fragmented conversation evidence still feels manageable.

4. `shopify_oversized_phone_photos`
   Best for seeing whether large PNG and JPEG cases expose the value of compression and pack cleanup.

## Why these starters matter

These are closer to cases where the service has real value:

- the merchant did not already export neat PDFs
- files come from screenshots or camera roll
- filenames are generic
- one evidence type may be split across several files
- image size can become part of the problem

If you are resuming on another machine, generate the scenarios first with `npm run generate:demo-evidence` and then follow `docs/dirty-case-test-runbook.md`.
