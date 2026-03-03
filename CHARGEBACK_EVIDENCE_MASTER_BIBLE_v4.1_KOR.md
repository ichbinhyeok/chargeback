# Chargeback Evidence Pack Builder — Agent Master Bible (KOR) v4.1
작성일: 2026-03-03 (Asia/Seoul)  
검증 기준일: 2026-03-03  
기술 스택: Spring Boot, jte, htmx, Tailwind CSS, PDFBox, PostgreSQL, S3-compatible storage  
제품 정의: Stripe/Shopify 분쟁 증빙을 제출 규칙에 맞게 자동 정리·검수·패키징하는 Final Check & Packager

---

## 0. Executive Summary
1. 본 제품은 승소 컨설팅이 아닌 제출 실패 방지용 문서 자동화 유틸리티다.
2. 최종 산출물은 제출용 ZIP, 내부 보관용 Master Binder PDF, 제출 전 Checklist PDF(1p)다.
3. 과금은 Pay-per-case `$19`, 결제 시점은 `READY_TO_UPLOAD` 직전이다.
4. 월 순매출 100만원 목표를 위해 월 45건 결제를 운영 목표로 둔다.

## 1. 비즈니스 목표와 수익 모델
1. 목표 순매출: 월 `1,000,000 KRW`.
2. 가격: `$19 / case`.
3. 환율 가정(근사): `1 USD ~= 1,443 KRW`.
4. 건당 총매출: 약 `27,417 KRW`.
5. 결제수수료(2.9% + 30c 가정) 반영 건당 순매출: 약 `26,100 KRW`.
6. 순매출 기준 필요 결제건: `39건/월`.
7. 환불/실패 버퍼 포함 운영 목표: `45건/월`.
8. 무료 범위: 업로드, 분류, 검수 결과 미리보기.
9. 유료 범위: 최종 산출물 다운로드.

## 2. 절대 금지 (Non-negotiables)
1. 허위/조작 증빙 생성 금지.
2. 법률 조언/승소 보장/승소 확률 단정 금지.
3. Stripe/Shopify 자동 제출 대행(OAuth 포함) 금지.
4. 외부 링크, 오디오, 비디오, 추가 연락 요청 문구 포함 금지.
5. 고객사별 맞춤 컨설팅 모델 지양(제품형 셀프서브 우선).

## 3. 아키텍처 원칙
1. No-OAuth / No-Integration: 업로드 중심 워크플로우.
2. Rule-first Engine: 추론보다 규칙 준수 보장.
3. One Next Action: 사용자에게 한 번에 다음 행동 1개만 제시.
4. Evidence Type당 1파일: 플랫폼 제출 구조를 그대로 반영.
5. Privacy-first: 최소수집, 자동삭제, 즉시삭제.

## 4. DB 사용 근거 (왜 PostgreSQL이 필요한가)
1. 상태머신 영속화: `VALIDATING -> FIXING -> READY -> PAID -> DOWNLOADED`.
2. 결제 정합성: webhook 멱등 처리 및 중복 다운로드 방지.
3. 감사 가능성: 어떤 룰 실패/어떤 auto-fix 적용인지 추적.
4. 삭제 정책 실행: 7일 만료 삭제/즉시 삭제/접근 로그 관리.
5. 퍼널 최적화: 업로드->READY->결제 이벤트 분석.
6. 권장 구조: 파일 바이너리는 object storage, 메타데이터/트랜잭션은 PostgreSQL.

## 5. 사용자 흐름 (State Machine)
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

## 6. 증빙 타입 표준 스키마 (8 Types)
1. `order_receipt`
2. `customer_details`
3. `customer_communication`
4. `policies`
5. `fulfillment_delivery`
6. `digital_usage_logs`
7. `refund_cancellation`
8. `other_supporting`

원칙: 제출용 산출물은 타입별 PDF 1개.

## 7. 분류(Classification) 정의
1. 기본: 슬롯 기반 수동 분류(UI에서 8개 슬롯에 드래그 앤 드롭).
2. 보조: 파일명 키워드 + OCR 텍스트 기반 추천 라벨(신뢰도 점수 포함).
3. 확정 권한: 최종 분류는 사용자 확정만 반영.
4. 충돌 처리: 동일 파일 다중 슬롯 배정 금지.
5. 감사 로그: 분류 변경 이력(`old_type`, `new_type`, `actor`, `timestamp`) 저장.

## 8. 플랫폼 Rulebook (Canonical, 2026-03-03 기준)
모든 룰은 `rule_id`, `product_scope`, `source_url`, `last_verified_at`, `severity`, `autofix_supported`를 저장한다.

### 8.1 Stripe
1. 허용 형식: PDF/JPEG/PNG.
2. 총 용량: 4.5MB 이하.
3. 총 페이지: 50p 미만.
4. Mastercard 옵션: 총 19p 제한.
5. 증빙 타입당 파일 수: 1개.
6. 금지 콘텐츠: 외부 링크/오디오/비디오/추가 연락 요청.

### 8.2 Shopify Payments (product_scope=`shopify_payments_chargeback`)
1. 허용 형식: PDF/JPEG/PNG.
2. PDF 규격: PDF/A 준수 필요.
3. PDF Portfolio: 금지.
4. 파일당 용량: 2MB 이하.
5. 합산 용량: 4MB 이하.
6. PDF 페이지: 50p 미만.
7. 증빙 타입당 파일 수: 1개.
8. 금지 콘텐츠: 외부 링크/오디오/비디오/추가 연락 요청.
9. 조기 제출 경고: 제출 후 수정 불가 워닝 노출.

### 8.3 Shopify Credit (product_scope=`shopify_credit_dispute`)
1. 허용 형식: PDF/JPEG/PNG.
2. PDF 규격: PDF/A 준수 필요.
3. PDF Portfolio: 금지.
4. 파일당 용량: 2MB 이하.
5. 합산 용량: 4.5MB 이하.
6. PDF 페이지: 50p 미만.
7. 증빙 타입당 파일 수: 1개.
8. 금지 콘텐츠: 외부 링크/오디오/비디오/추가 연락 요청.
9. 조기 제출 경고: 제출 후 수정 불가 워닝 노출.

## 9. Auto-fix 정책
### 9.1 자동 처리 범위 (OK)
1. 동일 타입 다중 파일 병합.
2. JPG/PNG -> PDF 변환.
3. 이미지 리샘플링/압축(용량 최적화).
4. Shopify 대상 PDF/A 변환 시도.
5. 파일명 표준화(예: `01_order_receipt.pdf`).

### 9.2 수동 처리 필요 (BLOCKED)
1. PDF/A 변환 실패.
2. 페이지 제한 초과(핵심 발췌 필요).
3. 금지 콘텐츠 탐지.
4. 가독성 임계치 미달.

### 9.3 오류 노출 원칙
1. 한 번에 원인 1개만 표시.
2. 다음 행동 1개만 제시.
3. 항상 룰 ID와 근거 문장을 함께 표시.

## 10. htmx 인터랙션 패턴 (화면별)
1. 업로드:
`hx-post="/api/cases/{id}/files"`  
`hx-target="#slot-grid"`  
`hx-swap="innerHTML"`
2. 분류 변경:
`hx-patch="/api/cases/{id}/classifications"`  
`hx-target="#slot-grid"`  
`hx-swap="outerHTML"`
3. 검증 실행:
`hx-post="/api/cases/{id}/validate"`  
`hx-target="#validation-report"`  
`hx-swap="innerHTML"`
4. Auto-fix 실행:
`hx-post="/api/cases/{id}/fix"`  
`hx-target="#validation-report"`  
`hx-swap="innerHTML"`
5. 결제 버튼 영역:
`hx-get="/api/cases/{id}/checkout/button"`  
`hx-target="#paywall-panel"`  
`hx-swap="innerHTML"`

## 11. 출력물 규격
1. `Chargeback_Evidence_Kit_<case_id>.zip`
2. `01_Upload_Ready_Files/01_order_receipt.pdf` ... 타입별 1개
3. `02_Master_Binder.pdf` (Cover/TOC/Rule summary/Section)
4. `03_Checklist_Guide.pdf` (1p, pass/fail + missing + warning)

## 12. 데이터 모델 (ERD 요약)
| Table | 핵심 컬럼 |
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

## 13. API 계약 (MVP)
1. `POST /api/cases`
   요청 필수 필드: `platform`, `product_scope`, `reason_code`.
2. `POST /api/cases/{caseId}/files`
3. `PATCH /api/cases/{caseId}/classifications`
4. `POST /api/cases/{caseId}/validate`
5. `POST /api/cases/{caseId}/fix`
6. `GET /api/cases/{caseId}/report`
7. `POST /api/cases/{caseId}/checkout`
8. `POST /api/webhooks/stripe`
9. `GET /api/cases/{caseId}/downloads/{artifactType}`
10. `DELETE /api/cases/{caseId}`

## 14. 결제/다운로드 규칙
1. `READY` 상태에서만 checkout 생성 가능.
2. webhook은 `event_id` 기준 멱등 처리.
3. 결제 성공 시 1회성 signed URL 발급(만료 15분).
4. 결제 실패 시 READY 상태 유지.
5. 산출물 생성 실패 시 2회 재시도 후 자동 환불 플로우.

## 15. 보안/프라이버시 정책
1. 업로드 allowlist + MIME + magic bytes 검증.
2. AV 스캔 실패 시 격리.
3. 랜덤 storage key 사용.
4. 기본 보관 7일, 즉시 삭제 버튼 제공.
5. 다운로드/변환/삭제 감사 로그 저장.
6. 약관/결제 직전 고지: 법률조언 아님, 승소보장 아님, 허위증빙 금지.

## 16. 에러 코드 사전
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

## 17. QA 테스트 기준 (경계값 우선)
1. Stripe 4.49MB 통과 / 4.51MB 실패.
2. Shopify 파일당 1.99MB 통과 / 2.01MB 실패.
3. Shopify Payments 합산 3.99MB 통과 / 4.01MB 실패.
4. Shopify Credit 합산 4.49MB 통과 / 4.51MB 실패.
5. 페이지 49p 통과 / 50p 실패.
6. Mastercard 19p 통과 / 20p 실패.
7. PDF 링크 포함 시 금지 콘텐츠 실패.
8. webhook 중복 전송 시 1회 처리 검증.
9. 즉시 삭제 후 파일 접근 차단 검증.

## 18. 로드맵과 Phase별 DoD
### Phase 1 (3주): 업로드 + 검증 기반
1. Done: 파일 업로드 후 플랫폼별 룰 검증 결과가 화면에 즉시 표시된다.
2. Done: 8개 슬롯 수동 분류와 분류 변경 이력 저장이 동작한다.

### Phase 2 (3주): Auto-fix + 산출물
1. Done: 병합/압축/변환/PDF-A 변환 시도가 동작한다.
2. Done: 제출용 ZIP, Master Binder, Checklist 생성 및 미리보기가 가능하다.

### Phase 3 (3주): 결제 + 보안/운영
1. Done: 결제 성공 시 산출물 다운로드가 가능하고 webhook 멱등성이 보장된다.
2. Done: 자동삭제/즉시삭제/감사로그가 운영 환경에서 검증된다.

### Phase 4 (3주): 수익 최적화
1. Done: 퍼널 대시보드(방문->업로드->READY->결제)가 동작한다.
2. Done: 가격/카피 A/B 테스트와 실패코드 상위 개선 루프가 운영된다.

## 19. 운영 KPI
1. 방문 -> 업로드 시작: 12% 이상.
2. 업로드 시작 -> READY: 70% 이상.
3. READY -> 결제: 35% 이상.
4. 월 결제건: 45건 이상.
5. 환불률: 3% 미만.

## 20. 에이전트 응답 규격
1. 매 답변은 3줄로 고정.
2. 1줄: 현재 상태.
3. 1줄: 지금 할 일 1개.
4. 1줄: 이유(플랫폼 룰 기반).

## 21. 에이전트 시스템 프롬프트 (붙여넣기용)
```text
너는 Dispute Evidence Assistant다.
목표: 업로드된 증빙 파일을 Stripe/Shopify 규칙에 맞게 정리·검수·패키징해 Ready-to-Upload 상태로 만든다.

절대 규칙:
- 승소 보장/법률 조언 금지.
- 허위/조작/사기성 증빙 생성 금지.
- 외부 링크/오디오/비디오/추가 연락 요청 포함 금지.
- 질문 폭탄 금지.
- 매 답변은 3줄: (1)상태 (2)다음 행동 1개 (3)이유.

플랫폼 룰 요약:
- Stripe: PDF/JPG/PNG, 총 4.5MB 이하, 총 50p 미만, Mastercard 19p 제한 옵션, 타입별 1파일.
- Shopify Payments: PDF/JPG/PNG, PDF/A 준수 필요, PDF Portfolio 금지, 파일당 2MB/합산 4MB, PDF 50p 미만, 타입별 1파일, 조기 제출 후 수정 불가 경고.
- Shopify Credit: PDF/JPG/PNG, PDF/A 준수 필요, PDF Portfolio 금지, 파일당 2MB/합산 4.5MB, PDF 50p 미만, 타입별 1파일, 조기 제출 후 수정 불가 경고.

출력물:
- Evidence Upload Kit ZIP
- Master Binder PDF
- Checklist PDF

거절 템플릿:
- 허위/조작 요청 시: “그건 도와드릴 수 없어요. 대신 실제 자료를 규칙에 맞게 정리해 제출 가능한 형태로 만드는 건 도와드릴게요.”
```

## 22. 공식 출처
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

## 23. 즉시 실행 플랜 (정책 확정 반영)
1. Day 0: Rulebook Freeze
   산출물: `rulebook_version=2026-03-03` 확정, `shopify_payments_chargeback`와 `shopify_credit_dispute` 분기 확정.
2. Day 1: DB/도메인 반영
   산출물: `cases.product_scope` 컬럼 추가, 기본값 없음(필수 입력), 인덱스 추가.
3. Day 2: Validator 분기 구현
   산출물: Shopify Payments는 합산 4MB, Shopify Credit는 합산 4.5MB로 검사.
4. Day 3: Auto-fix/에러코드 정합화
   산출물: `ERR_SHPFY_TOTAL_TOO_LARGE`(4MB), `ERR_SHPFY_CREDIT_TOTAL_TOO_LARGE`(4.5MB) 분리.
5. Day 4: QA 경계값 테스트
   산출물: 3.99/4.01MB, 4.49/4.51MB 케이스 자동 테스트 통과.
6. Day 5: 배포 전 점검
   산출물: 결제/다운로드 플로우, 로그, 삭제 정책, 소스 링크 검증일 표시 확인.

진행 원칙: 정책 충돌이 생기면 코드 수정보다 Rulebook 버전 갱신을 우선한다.

## 24. Dev Handoff (2026-03-03)
### 24.1 오늘 완료한 개발 작업
1. Backend case workflow API 구현 완료
   - create/upload/list/reclassify/validate/validate-stored/report/delete
2. Validation history 영속화 완료
   - `validation_runs`, `validation_issues` 저장 및 report 노출
3. Auto-fix job 파이프라인 구현 완료
   - endpoint: `POST /api/cases/{caseId}/fix`, `GET /api/cases/{caseId}/fix/{jobId}`
   - state: `QUEUED -> RUNNING -> SUCCEEDED/FAILED`
   - 현재 지원 fix: 타입별 다중 파일 업로드 시 최신 1개만 유지
4. Audit log + retention cleanup 스케줄러 구현 완료
5. DB migration 추가 완료
   - `V1`~`V5` (cases, evidence_files, validation, audit_logs, fix_jobs)
6. 테스트 확장 완료
   - 총 27개 테스트
   - 실행 검증: `./gradlew.bat clean test` 성공

### 24.2 현재 코드 기준 주의사항
1. Shopify PDF/A 정책 방향은 문서 버전 간 충돌 이력이 있음.
2. 현재 구현은 `PDF/A required` 기준(`ERR_SHPFY_PDF_NOT_PDFA`)으로 동작함.
3. 정책 확정 전에는 룰/에러코드/Auto-fix 문구를 단일 기준으로 재정렬해야 함.

### 24.3 다음 작업 (Frontend 이전, Backend 우선)
1. Deliverable generator 구현
   - Evidence Upload Kit ZIP
   - Master Binder PDF
   - Checklist PDF
2. Download control and pre-payment flow 구현
   - READY 이후 토큰 기반 다운로드 제어
   - 만료/재발급 정책
3. Auto-fix 확장
   - dedupe 외의 용량/페이지 관련 fix 시나리오 단계적 추가
   - 실패 시 단일 원인(One reason) 반환 강제
4. 운영 안정화
   - webhook/idempotency 강화
   - structured logging + trace id

### 24.4 다른 로컬에서 작업 이어받기
1. clone
```powershell
git clone https://github.com/ichbinhyeok/chargeback.git
cd chargeback
git checkout main
```
2. JDK 21 설정 후 테스트
```powershell
$env:JAVA_HOME="C:\Path\To\JDK21"
.\gradlew.bat clean test
```
3. 앱 실행
```powershell
$env:JAVA_HOME="C:\Path\To\JDK21"
.\gradlew.bat bootRun
```
4. 참고 문서
   - `HANDOFF_2026-03-03_KOR.md` (상세 핸드오프)
