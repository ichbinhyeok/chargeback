# Persona Priority Debate (2026-03-07)

Purpose:
- Turn beta persona observations into a concrete revenue-oriented product backlog.
- Focus on what helps a blocked user reach `upload-ready` fastest.

Participants:
- Jiwoo: solo Shopify seller, phone-photo heavy, low confidence
- Minho: operations assistant, weak filenames, needs classification help
- Sora: Stripe dispute operations lead, wants speed and confidence
- Hana: CX lead, fragmented screenshots, wants cleanup automation
- PM lens: monetization and trust

## Shared conclusion

All personas agree on one point:

`Users do not come here to organize files. They come here because Stripe or Shopify already rejected them or is likely to reject them.`

Therefore the product promise should be:

`We resolve supported upload blockers and make the case submission-ready.`

Not:

`We give you a nicer evidence organizer.`

## What each persona argues for

### Jiwoo

Main argument:
- The product must reduce fear before upload and after partial fixes.

What she cares about:
- No contradictory error wording before actual upload
- Exact next instruction when auto-fix helps but does not fully unblock the case
- Trust that the product is not silently damaging evidence quality

Decision impact:
- Partial auto-fix guidance is not polish. It directly affects conversion.

### Minho

Main argument:
- Classification is the hardest human step, not the upload button.

What he cares about:
- Preview or thumbnail inside mapping
- Better evidence-type guesses for obvious files
- Fewer abstract labels that default everything to `OTHER_SUPPORTING`

Decision impact:
- If mapping feels blind, users will not trust later validation or export states.

### Sora

Main argument:
- Speed matters, but only if readiness signals are consistent.

What she cares about:
- Clear `blocked` vs `ready` status
- Fast handling of common platform blockers
- Minimal unnecessary clicks after validation

Decision impact:
- State inconsistency is a trust bug, not a UX detail.

### Hana

Main argument:
- The strongest product value is when messy evidence becomes reviewer-ready automatically.

What she cares about:
- Merge fragmented screenshots into one coherent file
- Explain why the fix worked
- Show the exact blocker that disappeared

Decision impact:
- Auto-fix is the monetizable core when it changes the case from blocked to ready.

### PM lens

Main argument:
- The paid product is not `remove watermark`.
- The paid product is `resolved blocker + clean final export`.

What matters:
- Free should prove that the system can help
- Paid should unlock the clean submission artifacts
- Automation should be prioritized by frequency, safety, and conversion impact

Decision impact:
- Do not over-invest in cosmetic polish before common blocker automation.

## Debate outcomes

### Outcome 1: Error-first positioning wins

Reason:
- All personas derive value only when a platform blocker is identified or removed.

Decision:
- Reframe the main flow around `what error did the platform show?`
- Make validation read like an error-resolution dashboard, not a generic checklist page

### Outcome 2: Safe automation beats broad automation

Reason:
- Fully automatic content trimming can weaken evidence quality or create liability

Decision:
- Prioritize mechanical, low-risk fixes first
- Keep high-judgment edits as guided manual actions

Safe automation examples:
- invalid format conversion
- duplicate-type merge
- external-link removal
- image compression under platform limits
- PDF/A conversion
- PDF portfolio flattening
- blank-page removal
- exact duplicate image/page detection

Guided manual examples:
- removing pages that might contain context
- shortening long chats to only "relevant" excerpts
- reclassifying ambiguous evidence without preview

### Outcome 3: Partial fixes need hard next-step guidance

Reason:
- A succeeded auto-fix that still leaves the case blocked currently creates confusion instead of relief

Decision:
- After every auto-fix, show one primary next action
- Example:
  - `Remove one POLICY image to get under Shopify total size limit`
  - `Replace this PDF with a smaller export`
  - `Map this file to ORDER_RECEIPT to satisfy a required evidence slot`

### Outcome 4: Mapping is part of blocker resolution

Reason:
- Wrong or weak mapping turns a solvable case into an apparently incomplete case

Decision:
- Treat mapping preview and stronger suggestions as core workflow work, not a secondary enhancement

## Priority framework

Score each candidate by:
- frequency of occurrence
- confidence that automation is correct
- risk of damaging evidence meaning
- rate of `blocked -> ready` improvement
- effect on paid conversion

## Prioritized backlog

### P0: already addressed in current branch

1. Fix false `READY` display when required evidence is still missing
2. Replace premature `Upload failed` wording during file selection

### P1: next revenue-critical work

1. Invalid format auto-conversion
- Why:
  - common mobile-export problem
  - highly automatable
  - strong user-perceived magic
- Scope:
  - HEIC, WEBP, and other unsupported image formats to JPEG
  - unsupported documents to PDF where safely possible

2. Partial auto-fix next-step guidance
- Why:
  - strongest trust gap after current fixes
  - directly affects whether users continue toward payment
- Scope:
  - one primary action
  - one reason
  - one affected file or evidence type

3. Error-first onboarding
- Why:
  - users arrive from platform rejection, not from a desire to organize files
- Scope:
  - ask platform and shown error first
  - preload likely rules and likely fix path

4. Mapping modal preview
- Why:
  - biggest blocker for weak filenames
- Scope:
  - thumbnail for images
  - first-page preview for PDFs
  - quick file metadata

### P2: high-value support for messy real bundles

1. Stronger evidence-type suggestions
2. Safe duplicate screenshot detection
3. Blank-page removal
4. Orientation and readability normalization
5. Better explanation of what changed after auto-fix

### P3: guided manual resolution helpers

1. Suggest which file to remove when over total size
2. Suggest which pages are duplicated or low-signal
3. Suggest which required evidence slot is easiest to satisfy next
4. Offer replace-file flow directly from blocker card

## What not to do next

Do not prioritize these before P1:
- cosmetic landing refresh with no error-resolution gain
- expanding paywall UI before fix success improves
- aggressive automatic page trimming
- advanced explanation writing improvements before blocker resolution improves

## Success metrics

Track these after each release:
- blocked-to-ready conversion rate
- auto-fix success rate by issue type
- paid conversion rate by issue type
- percentage of cases still blocked after successful auto-fix
- average number of manual steps remaining after validation

## Recommended execution order

1. Implement invalid format auto-conversion
2. Implement partial auto-fix next-step guidance
3. Add mapping preview
4. Reframe onboarding around platform error selection
5. Improve safe duplicate detection and size-reduction helpers
