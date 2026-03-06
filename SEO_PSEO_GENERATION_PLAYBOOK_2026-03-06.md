# pSEO Generation Playbook (2026-03-06)

## Goal

Scale guide pages without falling into thin-page or doorway patterns.

Current target:
- 40 guide pages total
- 20 `reason` pages
- 20 `error` pages

## Data Source

- Canonical catalog: `src/main/resources/seo/guides-v1.json`
- Generator: `scripts/generate_seo_guides.mjs`

## Generation Model

Each guide entry must define:
1. `platformSlug` (`stripe` or `shopify`)
2. `slug` (unique per platform)
3. `guideType` (`reason` or `error`)
4. `title` and `metaDescription` (unique and intent-specific)
5. `targetSearchQueries` (long-tail intent phrases)
6. `keyChecks`, `commonErrors`, `nextSteps` (actionable sections)
7. `explanationPreviewLines` (teaser for `/new` conversion)
8. `sourceUrls` (official policy sources only)
9. `faq` (at least 2 high-intent Q/A)

## Quality Gates (Enforced by Test)

`src/test/java/com/example/demo/dispute/SeoGuideCatalogQualityTest.java`

Checks:
1. unique slug/title/meta
2. min content depth per section
3. title length range
4. meta description length range
5. allowed source host check (`docs.stripe.com`, `help.shopify.com`)
6. disallowed claim phrases in title/meta
7. minimum platform/type coverage

## Regeneration Command

```powershell
$env:PATH = "c:\Users\Administrator\chargeback\.nodejs\node-v20.18.0-win-x64;" + $env:PATH
node scripts/generate_seo_guides.mjs
```

Then validate:

```powershell
.\gradlew.bat test --tests "*SeoGuideCatalogQualityTest"
```

## KPI Event Taxonomy

Tracked events:
1. `guide_view`
2. `guide_start_case_click`
3. `new_case_view_from_guide`

Collection/API:
1. `POST /api/seo/events`
2. `GET /api/seo/kpi?days=7`
3. Dashboard: `/seo/kpi` (noindex)

Core funnel rates:
1. `guide_start_case_click / guide_view`
2. `new_case_view_from_guide / guide_start_case_click`

## Expansion Rule for Next Batch (40 -> 100)

1. Add reason-code + error matrix rows first, not random keyword pages.
2. Keep one clear user-intent per page.
3. Do not publish near-duplicate title/meta combinations.
4. Add only pages with distinct fix steps and source-backed constraints.
5. Keep conversion path explicit (`guide -> /new`) with measurable events.
