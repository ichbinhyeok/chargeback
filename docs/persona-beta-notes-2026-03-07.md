# Persona Beta Notes (2026-03-07)

These notes were gathered by running the local app as if I were a real beta user, not just a test operator.

Method:
- Use synthetic evidence scenarios from `output/pdf/synthetic-evidence-sets/`
- Follow the real UI flow: `/ -> /new -> /upload -> /validate -> /export`
- Judge each run by four questions:
  - first hesitation
  - first ambiguous label
  - first screen where next click was unclear
  - whether the product felt worth paying for

## Persona 1: Jiwoo, solo Shopify seller

Scenario:
- `shopify_oversized_phone_photos`

Mindset:
- Not a dispute specialist
- Uploading directly from a phone-heavy archive
- Nervous about breaking something before files are actually submitted

Observed behavior:
- `/new` did a good job reducing fear. The copy clearly says no files are uploaded yet.
- `/upload` also does a good job saying nothing uploads until the mapping modal is confirmed.
- After selecting the oversized files, the app showed `Upload failed` before the user actually uploaded anything.
- The same modal still allowed upload, and the upload then succeeded and moved to validation.
- That sequence feels contradictory and undermines trust.
- Auto-fix reduced the file sizes, but the case still stayed blocked on total size.
- After auto-fix succeeded, the next action was not clear enough. The page mostly says the fix succeeded, but not what exact human action should happen next.

Beta-user notes:
- First hesitation: the early `Upload failed` message during file selection.
- First ambiguous label: `Upload failed` when the system really means `This bundle will fail validation unless reduced`.
- First unclear next click: after auto-fix succeeded but total size still exceeded the budget.
- Value moment: the product did feel useful once it compressed the images and made the remaining blocker concrete.

## Persona 2: Minho, operations assistant uploading on behalf of the owner

Scenario:
- `edge_manual_mapping_needed`

Mindset:
- Understands the task, but does not understand evidence taxonomy deeply
- Has weak filenames and wants the product to guide classification

Observed behavior:
- The mapping modal opened with all three generic files set to `OTHER_SUPPORTING`.
- There was no preview, inline thumbnail, or quick peek that would help classify `document_01.png`, `document_02.jpeg`, and `document_03.png`.
- This makes the UI feel like it assumes the user already knows the answer.
- After upload, validation correctly explained that format checks passed while required evidence was still missing.
- But the page header badge showed `READY` while the body said required evidence was still incomplete.
- The export page also showed `READY` at the top while the readiness cards correctly showed `2 / 3 required evidence files ready`.

Beta-user notes:
- First hesitation: the mapping modal with generic filenames and no visual cue.
- First ambiguous label: `OTHER_SUPPORTING` becomes the effective default for everything unknown, which is too easy to misuse.
- First unclear next click: not the click itself, but whether the case was actually ready or not because the top-level badge and the body disagreed.
- Value moment: low. In this scenario the product mostly surfaced the gap, but did not help enough with the hardest human task, which is classification.

## Persona 3: Sora, Stripe dispute operations lead

Scenario:
- `stripe_inr_phone_gallery_mix`

Mindset:
- More experienced
- Wants speed, clear readiness, and confidence that the pack is organized correctly

Observed behavior:
- The flow from case creation to upload was straightforward.
- The mapping modal guessed `billing_profile_capture.png` correctly, but most other files still defaulted to `OTHER_SUPPORTING`.
- For an ops lead this is survivable, but still more manual work than expected.
- Validation passed cleanly once the files were mapped.
- The export page did a good job showing:
  - `100%` readiness
  - `0` actionable now
  - `3 / 3 required evidence files ready`
- This was the cleanest high-confidence path in the session.
- The `Setup required` payment environment message is fine for local beta, but it prevents a full willingness-to-pay test.

Beta-user notes:
- First hesitation: mild hesitation in the mapping modal because auto-detection was weaker than expected.
- First ambiguous label: not a label problem; this was mostly a guidance-strength problem.
- First unclear next click: none after validation. `Review Export` was clear.
- Value moment: strong. This is a credible happy path for a messy but workable merchant bundle.

## Persona 4: Hana, CX lead with fragmented chat screenshots

Scenario:
- `edge_split_chat_screenshots`

Mindset:
- Has enough proof, but it is fragmented across multiple screenshots
- Wants the product to turn a messy folder into something reviewer-friendly

Observed behavior:
- Uploading six files led to one clear Stripe issue: multiple files for one evidence type.
- The auto-fix call succeeded and converted the three chat screenshots into a single `CUSTOMER_COMMUNICATION` PDF with `Pages: 3`.
- The uploaded file list dropped from six files to four files.
- The case moved from blocked to ready without needing extra manual cleanup.

Beta-user notes:
- First hesitation: whether multiple screenshots should all be mapped to one evidence type.
- First ambiguous label: none once the validation issue explained the Stripe rule.
- First unclear next click: none. `Run Auto-Fix Engine` was obvious.
- Value moment: strongest of the session. This scenario makes the product feel genuinely useful.

## Cross-Scenario Findings

### Critical

1. Top-level case state can disagree with the actual readiness explanation.
- In the manual-mapping Stripe scenario, the page header said `READY` while validation/export content still said a required evidence type was missing.
- This is trust-breaking and should be treated as a product bug, not just copy polish.

2. The oversized Shopify flow uses the wrong emotional framing for pre-upload warnings.
- `Upload failed` appears before the user confirms upload.
- The product should describe the selection as risky or non-compliant, not as already failed.

### High-value UX improvements

1. The mapping modal needs a quick preview path.
- Even a small thumbnail, file preview drawer, or `open file` action would materially improve classification confidence.

2. Auto-fix needs stronger post-fix guidance when it only partially resolves problems.
- A succeeded auto-fix that still leaves the case blocked needs a concrete next instruction such as `remove one policy image` or `replace with smaller export`.

3. Auto-classification is currently too conservative for the scenarios that should feel easy.
- The strongest realistic mixed bundle still required a lot of manual mapping.

4. Navigation naming is inconsistent.
- Some screens say `Upload Evidence`, others say `Upload & Validate`.
- The difference is understandable internally, but it reads like two different steps.

5. The home page is slightly biased toward returning users.
- `Recent Cases` is prominent enough that first-time users may scan it before the main CTA.

## Product Value Verdict

Most convincing value:
- `edge_split_chat_screenshots`
- `stripe_inr_phone_gallery_mix`

Weakest trust points:
- premature `Upload failed` wording
- `READY` badge shown while required evidence is still missing
- no preview help for generic filenames

If I were prioritizing the next UI pass, I would fix these in order:
1. Resolve the `READY` vs missing-required inconsistency everywhere.
2. Replace the pre-upload `Upload failed` message with selection-risk language.
3. Add preview support inside the mapping modal.
4. Improve partial auto-fix follow-up guidance.
5. Increase filename-based or content-based evidence-type guesses for obvious merchant files.
