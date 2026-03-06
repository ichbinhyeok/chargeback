# Prelaunch Smoke Report (2026-03-06)

## Scope Executed
- Task: `1) platform sandbox smoke scenarios with sample evidence sets`
- Execution mode: local integration smoke (real app stack + DB + file pipeline), not external Stripe/Shopify live API submission.

## Scenarios

### 1) Stripe dispute smoke
- Case: `platform=STRIPE`, `scope=STRIPE_DISPUTE`, `reasonCode=13.1`, `cardNetwork=VISA`
- Flow:
  1. Create case
  2. Upload evidence set (`ORDER_RECEIPT`, `CUSTOMER_DETAILS`, `FULFILLMENT_DELIVERY`)
  3. Run `/api/cases/{id}/validate-stored` -> pass
  4. Confirm checklist rendering on dashboard (`Stripe - Product not received`)
  5. Mark payment paid (sandbox simulation)
  6. Download `/c/{token}/download/submission.zip`
  7. Verify manifest contains canonical reason and policy context
- Result: `PASS`

### 2) Shopify Payments chargeback smoke
- Case: `platform=SHOPIFY`, `scope=SHOPIFY_PAYMENTS_CHARGEBACK`, `reasonCode=fraudulent`
- Flow:
  1. Create case
  2. Upload evidence set (`ORDER_RECEIPT`, `CUSTOMER_DETAILS`, `FULFILLMENT_DELIVERY`) as JPEG files
  3. Run `/api/cases/{id}/validate-stored` -> pass
  4. Confirm checklist rendering on dashboard (`Shopify - Fraudulent`)
  5. Mark payment paid (sandbox simulation)
  6. Download `/c/{token}/download/submission.zip`
  7. Verify manifest contains canonical reason and policy context
- Result: `PASS`

## Command Run
```powershell
$env:JAVA_HOME='C:\Users\Administrator\chargeback\.jdk\jdk-21.0.10+7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew test --tests "*PrelaunchSandboxSmokeIntegrationTest"
```

## Outcome
- `BUILD SUCCESSFUL`
- New smoke test file:
  - `src/test/java/com/example/demo/dispute/PrelaunchSandboxSmokeIntegrationTest.java`

## Remaining for external go-live
- Perform one final live checkout/webhook confirmation using real Stripe test keys in deployment-like environment.
- Perform one manual merchant-side submission dry-run in Stripe/Shopify dashboard with exported ZIP for UX confirmation.
