# Beta Launch Plan

Last updated: 2026-03-09

## Positioning

- Product type: Stripe/Shopify dispute evidence file preflight
- Promise: detect and fix supported evidence-file blockers before submission
- Non-promise: final platform acceptance, dispute win rate, or legal outcome
- Support email: `shinhyeok22@gmail.com`

## Pricing

- Free: validation, readiness metrics, watermarked guide
- Paid: `$9` one-time beta export unlock
- Provider: Lemon Squeezy
- Refund rule: if the same documented file blocker still fails on the first retry with the same file set, review and refund on request

## Storage

- Server retention: 7 days by default
- Browser recent-case vault: 7 days, opt-in only
- Users can delete a case immediately from the dashboard

## Scope Matrix

### Supported today

- Shopify PDF/A required
- Shopify PDF portfolio not accepted
- Shopify external links not allowed
- Shopify single evidence file over 2MB
- Duplicate evidence type cleanup and per-type merge
- Supported image normalization to JPEG
- Some blank or exact-duplicate PDF page cleanup

### Conditional

- Total pack size overages
- Total page overages
- Ambiguous evidence classification
- Missing evidence quality or weak evidence composition
- Platform-specific UI edge cases outside documented file rules

### Not promised

- Final upload acceptance guarantee
- Chargeback outcome improvement
- Legal advice or filing strategy

## Operating Rules

1. Free preflight stays open to everyone.
2. Beta export is positioned as a clean-download unlock, not an outcome guarantee.
3. Home and checkout copy must stay inside documented file-rule claims.
4. Support requests use `shinhyeok22@gmail.com`.
5. Review the first 10 to 20 paid cases manually and log the actual retry outcome.

## Metrics

- Supported blocker share
- Export unlock conversion rate
- Same-blocker retry success rate
- Refund rate
- Most common raw uploader error strings

## Next Work

1. Add explicit supported/conditional labeling to validation output.
2. Gate payment more tightly when unresolved conditional risks remain.
3. Log actual post-export retry outcomes from users.
4. Raise price only after refund rate and retry-success rate are stable.
