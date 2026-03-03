# Chargeback Evidence Pack Builder ??Agent Master Bible (KOR) v4.1
?묒꽦?? 2026-03-03 (Asia/Seoul)  
寃利?湲곗??? 2026-03-03  
湲곗닠 ?ㅽ깮: Spring Boot, jte, htmx, Tailwind CSS, PDFBox, PostgreSQL, S3-compatible storage  
?쒗뭹 ?뺤쓽: Stripe/Shopify 遺꾩웳 利앸튃???쒖텧 洹쒖튃??留욊쾶 ?먮룞 ?뺣━쨌寃?샕룻뙣?ㅼ쭠?섎뒗 Final Check & Packager

---

## 0. Executive Summary
1. 蹂??쒗뭹? ?뱀냼 而⑥꽕?낆씠 ?꾨땶 ?쒖텧 ?ㅽ뙣 諛⑹???臾몄꽌 ?먮룞???좏떥由ы떚??
2. 理쒖쥌 ?곗텧臾쇱? ?쒖텧??ZIP, ?대? 蹂닿???Master Binder PDF, ?쒖텧 ??Checklist PDF(1p)??
3. 怨쇨툑? Pay-per-case `$19`, 寃곗젣 ?쒖젏? `READY_TO_UPLOAD` 吏곸쟾?대떎.
4. ???쒕ℓ異?100留뚯썝 紐⑺몴瑜??꾪빐 ??45嫄?寃곗젣瑜??댁쁺 紐⑺몴濡??붾떎.

## 1. 鍮꾩쫰?덉뒪 紐⑺몴? ?섏씡 紐⑤뜽
1. 紐⑺몴 ?쒕ℓ異? ??`1,000,000 KRW`.
2. 媛寃? `$19 / case`.
3. ?섏쑉 媛??洹쇱궗): `1 USD ~= 1,443 KRW`.
4. 嫄대떦 珥앸ℓ異? ??`27,417 KRW`.
5. 寃곗젣?섏닔猷?2.9% + 30c 媛?? 諛섏쁺 嫄대떦 ?쒕ℓ異? ??`26,100 KRW`.
6. ?쒕ℓ異?湲곗? ?꾩슂 寃곗젣嫄? `39嫄???.
7. ?섎텋/?ㅽ뙣 踰꾪띁 ?ы븿 ?댁쁺 紐⑺몴: `45嫄???.
8. 臾대즺 踰붿쐞: ?낅줈?? 遺꾨쪟, 寃??寃곌낵 誘몃━蹂닿린.
9. ?좊즺 踰붿쐞: 理쒖쥌 ?곗텧臾??ㅼ슫濡쒕뱶.

## 2. ?덈? 湲덉? (Non-negotiables)
1. ?덉쐞/議곗옉 利앸튃 ?앹꽦 湲덉?.
2. 踰뺣쪧 議곗뼵/?뱀냼 蹂댁옣/?뱀냼 ?뺣쪧 ?⑥젙 湲덉?.
3. Stripe/Shopify ?먮룞 ?쒖텧 ???OAuth ?ы븿) 湲덉?.
4. ?몃? 留곹겕, ?ㅻ뵒?? 鍮꾨뵒?? 異붽? ?곕씫 ?붿껌 臾멸뎄 ?ы븿 湲덉?.
5. 怨좉컼?щ퀎 留욎땄 而⑥꽕??紐⑤뜽 吏???쒗뭹????꾩꽌釉??곗꽑).

## 3. ?꾪궎?띿쿂 ?먯튃
1. No-OAuth / No-Integration: ?낅줈??以묒떖 ?뚰겕?뚮줈??
2. Rule-first Engine: 異붾줎蹂대떎 洹쒖튃 以??蹂댁옣.
3. One Next Action: ?ъ슜?먯뿉寃???踰덉뿉 ?ㅼ쓬 ?됰룞 1媛쒕쭔 ?쒖떆.
4. Evidence Type??1?뚯씪: ?뚮옯???쒖텧 援ъ“瑜?洹몃?濡?諛섏쁺.
5. Privacy-first: 理쒖냼?섏쭛, ?먮룞??젣, 利됱떆??젣.

## 4. DB ?ъ슜 洹쇨굅 (??PostgreSQL???꾩슂?쒓?)
1. ?곹깭癒몄떊 ?곸냽?? `VALIDATING -> FIXING -> READY -> PAID -> DOWNLOADED`.
2. 寃곗젣 ?뺥빀?? webhook 硫깅벑 泥섎━ 諛?以묐났 ?ㅼ슫濡쒕뱶 諛⑹?.
3. 媛먯궗 媛?μ꽦: ?대뼡 猷??ㅽ뙣/?대뼡 auto-fix ?곸슜?몄? 異붿쟻.
4. ??젣 ?뺤콉 ?ㅽ뻾: 7??留뚮즺 ??젣/利됱떆 ??젣/?묎렐 濡쒓렇 愿由?
5. ?쇰꼸 理쒖쟻?? ?낅줈??>READY->寃곗젣 ?대깽??遺꾩꽍.
6. 沅뚯옣 援ъ“: ?뚯씪 諛붿씠?덈━??object storage, 硫뷀??곗씠???몃옖??뀡? PostgreSQL.

## 5. ?ъ슜???먮쫫 (State Machine)
1. `ONBOARDING`
2. `CASE_CREATED`
3. `UPLOADING`
4. `VALIDATING`
5. `FIXING`
6. `READY`
7. `BLOCKED`
8. `PREVIEW`
9. `PAYWALL`
10. `PAID`
11. `DOWNLOADED`
12. `ARCHIVED`
13. `DELETED`

## 6. 利앸튃 ????쒖? ?ㅽ궎留?(8 Types)
1. `order_receipt`
2. `customer_details`
3. `customer_communication`
4. `policies`
5. `fulfillment_delivery`
6. `digital_usage_logs`
7. `refund_cancellation`
8. `other_supporting`

?먯튃: ?쒖텧???곗텧臾쇱? ??낅퀎 PDF 1媛?

## 7. 遺꾨쪟(Classification) ?뺤쓽
1. 湲곕낯: ?щ’ 湲곕컲 ?섎룞 遺꾨쪟(UI?먯꽌 8媛??щ’???쒕옒洹????쒕∼).
2. 蹂댁“: ?뚯씪紐??ㅼ썙??+ OCR ?띿뒪??湲곕컲 異붿쿇 ?쇰꺼(?좊ː???먯닔 ?ы븿).
3. ?뺤젙 沅뚰븳: 理쒖쥌 遺꾨쪟???ъ슜???뺤젙留?諛섏쁺.
4. 異⑸룎 泥섎━: ?숈씪 ?뚯씪 ?ㅼ쨷 ?щ’ 諛곗젙 湲덉?.
5. 媛먯궗 濡쒓렇: 遺꾨쪟 蹂寃??대젰(`old_type`, `new_type`, `actor`, `timestamp`) ???

## 8. ?뚮옯??Rulebook (Canonical, 2026-03-03 湲곗?)
紐⑤뱺 猷곗? `rule_id`, `product_scope`, `source_url`, `last_verified_at`, `severity`, `autofix_supported`瑜???ν븳??

### 8.1 Stripe
1. ?덉슜 ?뺤떇: PDF/JPEG/PNG.
2. 珥??⑸웾: 4.5MB ?댄븯.
3. 珥??섏씠吏: 50p 誘몃쭔.
4. Mastercard ?듭뀡: 珥?19p ?쒗븳.
5. 利앸튃 ??낅떦 ?뚯씪 ?? 1媛?
6. 湲덉? 肄섑뀗痢? ?몃? 留곹겕/?ㅻ뵒??鍮꾨뵒??異붽? ?곕씫 ?붿껌.

### 8.2 Shopify Payments (product_scope=`shopify_payments_chargeback`)
1. ?덉슜 ?뺤떇: PDF/JPEG/PNG.
2. PDF 洹쒓꺽: PDF/A 以???꾩슂.
3. PDF Portfolio: 湲덉?.
4. ?뚯씪???⑸웾: 2MB ?댄븯.
5. ?⑹궛 ?⑸웾: 4MB ?댄븯.
6. PDF ?섏씠吏: 50p 誘몃쭔.
7. 利앸튃 ??낅떦 ?뚯씪 ?? 1媛?
8. 湲덉? 肄섑뀗痢? ?몃? 留곹겕/?ㅻ뵒??鍮꾨뵒??異붽? ?곕씫 ?붿껌.
9. 議곌린 ?쒖텧 寃쎄퀬: ?쒖텧 ???섏젙 遺덇? ?뚮떇 ?몄텧.

### 8.3 Shopify Credit (product_scope=`shopify_credit_dispute`)
1. ?덉슜 ?뺤떇: PDF/JPEG/PNG.
2. PDF 洹쒓꺽: PDF/A 以???꾩슂.
3. PDF Portfolio: 湲덉?.
4. ?뚯씪???⑸웾: 2MB ?댄븯.
5. ?⑹궛 ?⑸웾: 4.5MB ?댄븯.
6. PDF ?섏씠吏: 50p 誘몃쭔.
7. 利앸튃 ??낅떦 ?뚯씪 ?? 1媛?
8. 湲덉? 肄섑뀗痢? ?몃? 留곹겕/?ㅻ뵒??鍮꾨뵒??異붽? ?곕씫 ?붿껌.
9. 議곌린 ?쒖텧 寃쎄퀬: ?쒖텧 ???섏젙 遺덇? ?뚮떇 ?몄텧.

## 9. Auto-fix ?뺤콉
### 9.1 ?먮룞 泥섎━ 踰붿쐞 (OK)
1. ?숈씪 ????ㅼ쨷 ?뚯씪 蹂묓빀.
2. JPG/PNG -> PDF 蹂??
3. ?대?吏 由ъ깦?뚮쭅/?뺤텞(?⑸웾 理쒖쟻??.
4. Shopify ???PDF/A 蹂???쒕룄.
5. ?뚯씪紐??쒖????? `01_order_receipt.pdf`).

### 9.2 ?섎룞 泥섎━ ?꾩슂 (BLOCKED)
1. PDF/A 蹂???ㅽ뙣.
2. ?섏씠吏 ?쒗븳 珥덇낵(?듭떖 諛쒖톸 ?꾩슂).
3. 湲덉? 肄섑뀗痢??먯?.
4. 媛?낆꽦 ?꾧퀎移?誘몃떖.

### 9.3 ?ㅻ쪟 ?몄텧 ?먯튃
1. ??踰덉뿉 ?먯씤 1媛쒕쭔 ?쒖떆.
2. ?ㅼ쓬 ?됰룞 1媛쒕쭔 ?쒖떆.
3. ??긽 猷?ID? 洹쇨굅 臾몄옣???④퍡 ?쒖떆.

## 10. htmx ?명꽣?숈뀡 ?⑦꽩 (?붾㈃蹂?
1. ?낅줈??
`hx-post="/api/cases/{id}/files"`  
`hx-target="#slot-grid"`  
`hx-swap="innerHTML"`
2. 遺꾨쪟 蹂寃?
`hx-patch="/api/cases/{id}/classifications"`  
`hx-target="#slot-grid"`  
`hx-swap="outerHTML"`
3. 寃利??ㅽ뻾:
`hx-post="/api/cases/{id}/validate"`  
`hx-target="#validation-report"`  
`hx-swap="innerHTML"`
4. Auto-fix ?ㅽ뻾:
`hx-post="/api/cases/{id}/fix"`  
`hx-target="#validation-report"`  
`hx-swap="innerHTML"`
5. 寃곗젣 踰꾪듉 ?곸뿭:
`hx-get="/api/cases/{id}/checkout/button"`  
`hx-target="#paywall-panel"`  
`hx-swap="innerHTML"`

## 11. 異쒕젰臾?洹쒓꺽
1. `Chargeback_Evidence_Kit_<case_id>.zip`
2. `01_Upload_Ready_Files/01_order_receipt.pdf` ... ??낅퀎 1媛?3. `02_Master_Binder.pdf` (Cover/TOC/Rule summary/Section)
4. `03_Checklist_Guide.pdf` (1p, pass/fail + missing + warning)

## 12. ?곗씠??紐⑤뜽 (ERD ?붿빟)
| Table | ?듭떖 而щ읆 |
|---|---|
| `cases` | id, case_token, platform, product_scope, reason_code, due_at, card_network, state, rulebook_version, created_at |
| `evidence_files` | id, case_id, evidence_type, original_name, mime_type, size_bytes, page_count, storage_key, status |
| `classifications` | id, case_id, file_id, suggested_type, confirmed_type, confidence, confirmed_by, confirmed_at |
| `validations` | id, case_id, run_no, is_passed, blocking_rule_id, summary_json, created_at |
| `validation_items` | id, validation_id, rule_id, status, message, autofix_supported |
| `fix_jobs` | id, case_id, status, action, input_file_ids, output_file_id, fail_reason |
| `artifacts` | id, case_id, artifact_type, storage_key, sha256, expires_at |
| `payments` | id, case_id, provider, checkout_session_id, amount, currency, status |
| `webhook_events` | id, provider, event_id, type, payload_hash, processed_at |
| `audit_logs` | id, case_id, actor_type, action, metadata_json, created_at |

## 13. API 怨꾩빟 (MVP)
1. `POST /api/cases`
   ?붿껌 ?꾩닔 ?꾨뱶: `platform`, `product_scope`, `reason_code`.
2. `POST /api/cases/{caseId}/files`
3. `PATCH /api/cases/{caseId}/classifications`
4. `POST /api/cases/{caseId}/validate`
5. `POST /api/cases/{caseId}/fix`
6. `GET /api/cases/{caseId}/report`
7. `POST /api/cases/{caseId}/checkout`
8. `POST /api/webhooks/stripe`
9. `GET /api/cases/{caseId}/downloads/{artifactType}`
10. `DELETE /api/cases/{caseId}`

## 14. 寃곗젣/?ㅼ슫濡쒕뱶 洹쒖튃
1. `READY` ?곹깭?먯꽌留?checkout ?앹꽦 媛??
2. webhook? `event_id` 湲곗? 硫깅벑 泥섎━.
3. 寃곗젣 ?깃났 ??1?뚯꽦 signed URL 諛쒓툒(留뚮즺 15遺?.
4. 寃곗젣 ?ㅽ뙣 ??READY ?곹깭 ?좎?.
5. ?곗텧臾??앹꽦 ?ㅽ뙣 ??2???ъ떆?????먮룞 ?섎텋 ?뚮줈??

## 15. 蹂댁븞/?꾨씪?대쾭???뺤콉
1. ?낅줈??allowlist + MIME + magic bytes 寃利?
2. AV ?ㅼ틪 ?ㅽ뙣 ??寃⑸━.
3. ?쒕뜡 storage key ?ъ슜.
4. 湲곕낯 蹂닿? 7?? 利됱떆 ??젣 踰꾪듉 ?쒓났.
5. ?ㅼ슫濡쒕뱶/蹂????젣 媛먯궗 濡쒓렇 ???
6. ?쎄?/寃곗젣 吏곸쟾 怨좎?: 踰뺣쪧議곗뼵 ?꾨떂, ?뱀냼蹂댁옣 ?꾨떂, ?덉쐞利앸튃 湲덉?.

## 16. ?먮윭 肄붾뱶 ?ъ쟾
### 16.1 Stripe
1. `ERR_STRIPE_TOTAL_SIZE`
2. `ERR_STRIPE_TOTAL_PAGES`
3. `ERR_STRIPE_MC_19P`
4. `ERR_STRIPE_MULTI_FILE_PER_TYPE`
5. `ERR_STRIPE_LINK_DETECTED`

### 16.2 Shopify
1. `ERR_SHPFY_PDF_NOT_PDFA`
2. `ERR_SHPFY_PDF_PORTFOLIO`
3. `ERR_SHPFY_FILE_TOO_LARGE`
4. `ERR_SHPFY_TOTAL_TOO_LARGE`
5. `ERR_SHPFY_PDF_PAGES_EXCEEDED`
6. `ERR_SHPFY_MULTI_FILE_PER_TYPE`
7. `WARN_SHPFY_EARLY_SUBMIT`
8. `ERR_SHPFY_CREDIT_TOTAL_TOO_LARGE`

## 17. QA ?뚯뒪??湲곗? (寃쎄퀎媛??곗꽑)
1. Stripe 4.49MB ?듦낵 / 4.51MB ?ㅽ뙣.
2. Shopify ?뚯씪??1.99MB ?듦낵 / 2.01MB ?ㅽ뙣.
3. Shopify Payments ?⑹궛 3.99MB ?듦낵 / 4.01MB ?ㅽ뙣.
4. Shopify Credit ?⑹궛 4.49MB ?듦낵 / 4.51MB ?ㅽ뙣.
5. ?섏씠吏 49p ?듦낵 / 50p ?ㅽ뙣.
6. Mastercard 19p ?듦낵 / 20p ?ㅽ뙣.
7. PDF 留곹겕 ?ы븿 ??湲덉? 肄섑뀗痢??ㅽ뙣.
8. webhook 以묐났 ?꾩넚 ??1??泥섎━ 寃利?
9. 利됱떆 ??젣 ???뚯씪 ?묎렐 李⑤떒 寃利?

## 18. 濡쒕뱶留듦낵 Phase蹂?DoD
### Phase 1 (3二?: ?낅줈??+ 寃利?湲곕컲
1. Done: ?뚯씪 ?낅줈?????뚮옯?쇰퀎 猷?寃利?寃곌낵媛 ?붾㈃??利됱떆 ?쒖떆?쒕떎.
2. Done: 8媛??щ’ ?섎룞 遺꾨쪟? 遺꾨쪟 蹂寃??대젰 ??μ씠 ?숈옉?쒕떎.

### Phase 2 (3二?: Auto-fix + ?곗텧臾?1. Done: 蹂묓빀/?뺤텞/蹂??PDF-A 蹂???쒕룄媛 ?숈옉?쒕떎.
2. Done: ?쒖텧??ZIP, Master Binder, Checklist ?앹꽦 諛?誘몃━蹂닿린媛 媛?ν븯??

### Phase 3 (3二?: 寃곗젣 + 蹂댁븞/?댁쁺
1. Done: 寃곗젣 ?깃났 ???곗텧臾??ㅼ슫濡쒕뱶媛 媛?ν븯怨?webhook 硫깅벑?깆씠 蹂댁옣?쒕떎.
2. Done: ?먮룞??젣/利됱떆??젣/媛먯궗濡쒓렇媛 ?댁쁺 ?섍꼍?먯꽌 寃利앸맂??

### Phase 4 (3二?: ?섏씡 理쒖쟻??1. Done: ?쇰꼸 ??쒕낫??諛⑸Ц->?낅줈??>READY->寃곗젣)媛 ?숈옉?쒕떎.
2. Done: 媛寃?移댄뵾 A/B ?뚯뒪?몄? ?ㅽ뙣肄붾뱶 ?곸쐞 媛쒖꽑 猷⑦봽媛 ?댁쁺?쒕떎.

## 19. ?댁쁺 KPI
1. 諛⑸Ц -> ?낅줈???쒖옉: 12% ?댁긽.
2. ?낅줈???쒖옉 -> READY: 70% ?댁긽.
3. READY -> 寃곗젣: 35% ?댁긽.
4. ??寃곗젣嫄? 45嫄??댁긽.
5. ?섎텋瑜? 3% 誘몃쭔.

## 20. ?먯씠?꾪듃 ?묐떟 洹쒓꺽
1. 留??듬?? 3以꾨줈 怨좎젙.
2. 1以? ?꾩옱 ?곹깭.
3. 1以? 吏湲?????1媛?
4. 1以? ?댁쑀(?뚮옯??猷?湲곕컲).

## 21. ?먯씠?꾪듃 ?쒖뒪???꾨＼?꾪듃 (遺숈뿬?ｊ린??
```text
?덈뒗 Dispute Evidence Assistant??
紐⑺몴: ?낅줈?쒕맂 利앸튃 ?뚯씪??Stripe/Shopify 洹쒖튃??留욊쾶 ?뺣━쨌寃?샕룻뙣?ㅼ쭠??Ready-to-Upload ?곹깭濡?留뚮뱺??

?덈? 洹쒖튃:
- ?뱀냼 蹂댁옣/踰뺣쪧 議곗뼵 湲덉?.
- ?덉쐞/議곗옉/?ш린??利앸튃 ?앹꽦 湲덉?.
- ?몃? 留곹겕/?ㅻ뵒??鍮꾨뵒??異붽? ?곕씫 ?붿껌 ?ы븿 湲덉?.
- 吏덈Ц ??깂 湲덉?.
- 留??듬?? 3以? (1)?곹깭 (2)?ㅼ쓬 ?됰룞 1媛?(3)?댁쑀.

?뚮옯??猷??붿빟:
- Stripe: PDF/JPG/PNG, 珥?4.5MB ?댄븯, 珥?50p 誘몃쭔, Mastercard 19p ?쒗븳 ?듭뀡, ??낅퀎 1?뚯씪.
- Shopify Payments: PDF/JPG/PNG, PDF/A 以???꾩슂, PDF Portfolio 湲덉?, ?뚯씪??2MB/?⑹궛 4MB, PDF 50p 誘몃쭔, ??낅퀎 1?뚯씪, 議곌린 ?쒖텧 ???섏젙 遺덇? 寃쎄퀬.
- Shopify Credit: PDF/JPG/PNG, PDF/A 以???꾩슂, PDF Portfolio 湲덉?, ?뚯씪??2MB/?⑹궛 4.5MB, PDF 50p 誘몃쭔, ??낅퀎 1?뚯씪, 議곌린 ?쒖텧 ???섏젙 遺덇? 寃쎄퀬.

異쒕젰臾?
- Evidence Upload Kit ZIP
- Master Binder PDF
- Checklist PDF

嫄곗젅 ?쒗뵆由?
- ?덉쐞/議곗옉 ?붿껌 ?? ?쒓렇嫄??꾩??쒕┫ ???놁뼱?? ????ㅼ젣 ?먮즺瑜?洹쒖튃??留욊쾶 ?뺣━???쒖텧 媛?ν븳 ?뺥깭濡?留뚮뱶??嫄??꾩??쒕┫寃뚯슂.??```

## 22. 怨듭떇 異쒖쿂
1. https://docs.stripe.com/disputes/responding
2. https://docs.stripe.com/disputes/best-practices
3. https://docs.stripe.com/disputes/how-disputes-work
4. https://docs.stripe.com/disputes/measuring
5. https://docs.stripe.com/disputes/smart-disputes
6. https://stripe.com/pricing
7. https://help.shopify.com/en/manual/payments/shopify-payments/managing-chargebacks/chargebacks-shopify-admin
8. https://help.shopify.com/en/manual/payments/chargebacks/chargeback-process
9. https://help.shopify.com/en/manual/payments/chargebacks/resolve-chargeback
10. https://changelog.shopify.com/posts/uplift-disputes-evidence-form

## 23. 利됱떆 ?ㅽ뻾 ?뚮옖 (?뺤콉 ?뺤젙 諛섏쁺)
1. Day 0: Rulebook Freeze
   ?곗텧臾? `rulebook_version=2026-03-03` ?뺤젙, `shopify_payments_chargeback`? `shopify_credit_dispute` 遺꾧린 ?뺤젙.
2. Day 1: DB/?꾨찓??諛섏쁺
   ?곗텧臾? `cases.product_scope` 而щ읆 異붽?, 湲곕낯媛??놁쓬(?꾩닔 ?낅젰), ?몃뜳??異붽?.
3. Day 2: Validator 遺꾧린 援ы쁽
   ?곗텧臾? Shopify Payments???⑹궛 4MB, Shopify Credit???⑹궛 4.5MB濡?寃??
4. Day 3: Auto-fix/?먮윭肄붾뱶 ?뺥빀??   ?곗텧臾? `ERR_SHPFY_TOTAL_TOO_LARGE`(4MB), `ERR_SHPFY_CREDIT_TOTAL_TOO_LARGE`(4.5MB) 遺꾨━.
5. Day 4: QA 寃쎄퀎媛??뚯뒪??   ?곗텧臾? 3.99/4.01MB, 4.49/4.51MB 耳?댁뒪 ?먮룞 ?뚯뒪???듦낵.
6. Day 5: 諛고룷 ???먭?
   ?곗텧臾? 寃곗젣/?ㅼ슫濡쒕뱶 ?뚮줈?? 濡쒓렇, ??젣 ?뺤콉, ?뚯뒪 留곹겕 寃利앹씪 ?쒖떆 ?뺤씤.

吏꾪뻾 ?먯튃: ?뺤콉 異⑸룎???앷린硫?肄붾뱶 ?섏젙蹂대떎 Rulebook 踰꾩쟾 媛깆떊???곗꽑?쒕떎.

## 24. Dev Handoff (2026-03-03)
### 24.1 ?ㅻ뒛 ?꾨즺??媛쒕컻 ?묒뾽
1. Backend case workflow API 援ы쁽 ?꾨즺
   - create/upload/list/reclassify/validate/validate-stored/report/delete
2. Validation history ?곸냽???꾨즺
   - `validation_runs`, `validation_issues` ???諛?report ?몄텧
3. Auto-fix job ?뚯씠?꾨씪??援ы쁽 ?꾨즺
   - endpoint: `POST /api/cases/{caseId}/fix`, `GET /api/cases/{caseId}/fix/{jobId}`
   - state: `QUEUED -> RUNNING -> SUCCEEDED/FAILED`
   - ?꾩옱 吏??fix: ??낅퀎 ?ㅼ쨷 ?뚯씪 ?낅줈????理쒖떊 1媛쒕쭔 ?좎?
4. Audit log + retention cleanup ?ㅼ?以꾨윭 援ы쁽 ?꾨즺
5. DB migration 異붽? ?꾨즺
   - `V1`~`V5` (cases, evidence_files, validation, audit_logs, fix_jobs)
6. ?뚯뒪???뺤옣 ?꾨즺
   - 珥?27媛??뚯뒪??   - ?ㅽ뻾 寃利? `./gradlew.bat clean test` ?깃났

### 24.2 ?꾩옱 肄붾뱶 湲곗? 二쇱쓽?ы빆
1. Shopify PDF/A ?뺤콉 諛⑺뼢? 臾몄꽌 踰꾩쟾 媛?異⑸룎 ?대젰???덉쓬.
2. ?꾩옱 援ы쁽? `PDF/A required` 湲곗?(`ERR_SHPFY_PDF_NOT_PDFA`)?쇰줈 ?숈옉??
3. ?뺤콉 ?뺤젙 ?꾩뿉??猷??먮윭肄붾뱶/Auto-fix 臾멸뎄瑜??⑥씪 湲곗??쇰줈 ?ъ젙?ы빐????

### 24.3 ?ㅼ쓬 ?묒뾽 (Frontend ?댁쟾, Backend ?곗꽑)
1. Deliverable generator 援ы쁽
   - Evidence Upload Kit ZIP
   - Master Binder PDF
   - Checklist PDF
2. Download control and pre-payment flow 援ы쁽
   - READY ?댄썑 ?좏겙 湲곕컲 ?ㅼ슫濡쒕뱶 ?쒖뼱
   - 留뚮즺/?щ컻湲??뺤콉
3. Auto-fix ?뺤옣
   - dedupe ?몄쓽 ?⑸웾/?섏씠吏 愿??fix ?쒕굹由ъ삤 ?④퀎??異붽?
   - ?ㅽ뙣 ???⑥씪 ?먯씤(One reason) 諛섑솚 媛뺤젣
4. ?댁쁺 ?덉젙??   - webhook/idempotency 媛뺥솕
   - structured logging + trace id

### 24.4 ?ㅻⅨ 濡쒖뺄?먯꽌 ?묒뾽 ?댁뼱諛쏄린
1. clone
```powershell
git clone https://github.com/ichbinhyeok/chargeback.git
cd chargeback
git checkout main
```
2. JDK 21 ?ㅼ젙 ???뚯뒪??```powershell
$env:JAVA_HOME="C:\Path\To\JDK21"
.\gradlew.bat clean test
```
3. ???ㅽ뻾
```powershell
$env:JAVA_HOME="C:\Path\To\JDK21"
.\gradlew.bat bootRun
```
4. Single source of truth: this Master Bible is the only handoff document.
