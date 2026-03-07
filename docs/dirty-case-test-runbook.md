# Dirty Case Test Runbook

Use this when resuming work on another machine and you want the fastest path to testing messy, realistic uploads.

## Goal

Answer these questions with direct product interaction:

- Do the evidence categories make sense when filenames are bad?
- Is the next click obvious from `/new` to `/upload` to `/validate`?
- Does the product feel useful when inputs are screenshots, photos, and scans instead of already-clean PDFs?
- Where does the user still hesitate?

## Prerequisites

- JDK 21
- Node.js 20+
- `npm install` completed
- `npm run build:css` completed
- `npm run generate:demo-evidence` completed

Generated files live under:

`output/pdf/synthetic-evidence-sets/`

## Start The App

Windows:

```powershell
npm install
npm run build:css
npm run generate:demo-evidence
.\gradlew.bat clean test
.\gradlew.bat bootRun
```

macOS/Linux:

```bash
npm install
npm run build:css
npm run generate:demo-evidence
./gradlew clean test
./gradlew bootRun
```

Open:

`http://localhost:8080`

## Shortest Click Path

1. Open `/`.
2. Click `Start Free Validation`.
3. On `/new`, choose platform, scope, and reason.
4. Click `Continue to Free Validation`.
5. On the dashboard, click `Upload Evidence`.
6. On `/upload`, click `Browse Files`.
7. In `Map Files to Evidence Types`, confirm or change file types.
8. Click `Upload Files`.
9. Review `/validate`.
10. If needed, move to `/export` and check whether the product explains why export is still blocked.

Important:

- Creating a case does not upload files.
- Choosing files does not upload files.
- Upload starts only after clicking `Upload Files`.
- `Format checks passed` is not the same thing as `export-ready`.

## Recommended Scenario Order

### 1. `stripe_inr_phone_gallery_mix`

Folder:

`output/pdf/synthetic-evidence-sets/stripe_inr_phone_gallery_mix/`

Why first:

- best overall reality check
- mixed input types
- some filenames are usable, some are not

What to watch:

- does the mapping modal feel understandable?
- does the user understand why several screenshots belong to different evidence types?
- does validate/export make the next action obvious?

### 2. `edge_manual_mapping_needed`

Folder:

`output/pdf/synthetic-evidence-sets/edge_manual_mapping_needed/`

Why second:

- this is where the UI has to do real work
- generic names like `document_01.png` remove filename hints

What to watch:

- can a first-time user map these correctly without domain knowledge?
- which evidence-type label feels too abstract?

### 3. `edge_split_chat_screenshots`

Folder:

`output/pdf/synthetic-evidence-sets/edge_split_chat_screenshots/`

Why third:

- one conversation spread over multiple screenshots is common

What to watch:

- is it clear that multiple screenshots can all support one evidence category?
- does the app feel too rigid when evidence is fragmented?

### 4. `shopify_oversized_phone_photos`

Folder:

`output/pdf/synthetic-evidence-sets/shopify_oversized_phone_photos/`

Why fourth:

- exposes size-budget pain clearly
- strongest case for compression / clean-up value

What to watch:

- are size problems explained clearly enough?
- does the Shopify-specific guidance feel trustworthy?

### 5. `edge_camera_scan_credit_bundle`

Folder:

`output/pdf/synthetic-evidence-sets/edge_camera_scan_credit_bundle/`

Why fifth:

- realistic paperwork-and-inbox mess
- useful for refund / credit workflow testing

What to watch:

- can the product turn scan-like and photo-like inputs into something that still feels manageable?
- are policy/helper notes strong enough when evidence is ugly but technically acceptable?

## Failure / Repair Drills

### `edge_missing_required_minimal`

Purpose:

- confirm that one valid file can still leave the case far from export-ready

Expected:

- formatting can pass
- export readiness should still show missing required evidence

### `edge_duplicate_order_receipt`

Purpose:

- confirm duplicate/overlapping evidence feels visible rather than confusing

Expected:

- duplicate-type or merge-related guidance should appear

## Use `scenario.json` Every Time

Each scenario folder includes `scenario.json`.

Use it to answer:

- which file belongs to which evidence type
- what kind of merchant mess the scenario represents
- why that scenario should prove service value

## What To Write Down During Testing

For each scenario, write down only four things:

1. First moment where you hesitated
2. First label that felt ambiguous
3. First screen where the next click was not obvious
4. Whether the product felt valuable for this messy bundle or still felt optional

If you do this for five scenarios, the next UI pass will be much more grounded.
