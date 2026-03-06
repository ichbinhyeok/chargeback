# Product Identity and Insights (2026-03-06)

## 1) What This Project Is

- Name (working): `Chargeback Evidence Pack Builder`
- Category: `dispute evidence upload-failure recovery`
- Core value: prevent avoidable upload rejection by validating, fixing, and packaging evidence for Stripe/Shopify submission flows.

This project is not:
- a legal-advice product
- a dispute outcome prediction engine
- a win-rate guarantee product

## 2) Target User (Current)

- Merchants or operators who already failed evidence upload, or are at high risk of rejection due to format/policy issues.
- Main pain:
  - "What files are required?"
  - "Why is this rejected?"
  - "What should I fix first?"

## 3) Current Product Coverage vs Workflow

Typical workflow:
1. chargeback notification
2. reason code/context check
3. evidence collection
4. evidence formatting and packaging
5. explanation writing
6. submission

Current project coverage:
- Step 2: partial (reason code stored and used in policy resolution context)
- Step 3: yes (evidence intake by type)
- Step 4: strong (validation + auto-fix + deterministic export)
- Step 5: not yet (no full explanation generator in core UX)
- Step 6: partial (submission-ready artifact generation)

## 4) Strategic Insight Agreed

The highest-leverage next capability is:
- `Reason-Code Evidence Checklist Engine`

One-line definition:
- Automatically show required/recommended evidence by reason code + platform + scope, with weak-evidence warnings and next actions.

Why this is aligned:
- It directly addresses upload-failure users.
- It upgrades product perception from "file tool" to "submission advisor".
- It can be implemented with policy mapping first (without mandatory AI dependency).

## 5) Recommended Roadmap (Positioning-Safe)

Phase A (now): strengthen Step 2
1. reason-code checklist mapping
2. required/recommended/weak-evidence warnings in dashboard/upload flow
3. "fix first" priority output

Phase B (next): controlled Step 5
1. explanation generator lite (template-guided, evidence-referenced)
2. keep promise boundary strict: no outcome claims

Phase C (later)
1. richer explanation generation
2. SEO expansion by reason-code evidence pages

## 6) Positioning Guardrails

Keep:
- "reduce upload rejection"
- "reduce rework time"
- "submission-readiness"

Avoid:
- "win more disputes"
- "increase approval rate by X%"
- legal-result promises

Recommended positioning line:
- `Stripe/Shopify dispute evidence upload rejection prevention toolkit`

## 7) Product KPI Direction

Primary KPIs:
1. first-pass upload acceptance rate
2. average time from first upload to export-ready state
3. average count of blocked issues before and after auto-fix/checklist guidance

Secondary KPIs:
1. checklist completion rate
2. export completion rate
3. repeat-case usage
