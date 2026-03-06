# CODEX MASTER PLAN v2.1 (2026-03-06)

## 0) 이 문서의 목적
- 목적: `chargeback` 레포를 "정책 기반 Submission Readiness + Packaging Engine"으로 안전하게 완성한다.
- 원칙: 확장보다 정합성. 새로운 제품축 추가 금지.
- 사용법: 구현 중 판단이 흔들리면 이 문서의 `불변 규칙`과 `Phase 순서`를 우선한다.

## 1) 미션 한 줄 정의
Chargeback는 증빙 파일을 정책 기준으로 검증하고, readiness를 계산하고, 제출 가능한 pack을 안전하게 생성하는 엔진이다.

## 2) 불변 규칙 (절대 위반 금지)
1. `Score is advisory only` - 점수는 UX 요약이며 권한을 직접 결정하지 않는다.
2. `Pay/Export 권한`은 `state + payment + fresh validation` truth로만 결정한다.
3. `Stale validation`이면 pay/export를 잠근다.
4. `FIXABLE`은 "지금 자동수정 가능한 항목"에만 부여한다.
5. `manifest.json`에 `caseToken`/secret/internal path 절대 포함 금지.
6. readiness 계산은 controller가 아니라 service/facade가 담당한다.
7. missing evidence는 full enum 기준이 아니라 `policy-aware` 기준으로 계산한다.
8. 이 단계에서 OAuth/subscription/AI rebuttal/team/multi-tenant 확장 금지.

## 3) 현재 우선순위 (P0/P1/P2)

### P0 (즉시)
1. Shopify Credit export 누락 가능성 제거 (export 규칙 분기 정합화)
2. 결제 성공 메시지와 실제 paid/unlock truth 일치
3. checkout 중복 생성 방지 (idempotency 또는 재사용)
4. stale validation 차단 로직으로 pay/export 보호
5. secure manifest 도입 (`caseToken` 완전 배제)

### P1 (바로 다음)
1. Issue target contract 도입 (`GLOBAL/EVIDENCE_TYPE/FILE/GROUP`)
2. FIXABLE 재분류 (`AUTO_FIXABLE` vs `MANUAL`)
3. state transition guard 강제
4. controller readiness 계산 제거 + facade 서비스화
5. checklist 중심 UI/Export 설명 강화

### P2 (안정화)
1. policy catalog 해상도 개선 (reason/card override)
2. snapshot 재현성 강화 (policy/validation/pack)
3. 테스트 밀도 확장 (payment-export 통합 회귀)
4. SEO dataization은 optional phase로 분리

## 4) Phase 실행 계획 (고정 순서)

## Phase 0 - Audit Freeze
- 산출물:
1. issue code inventory
2. fixability matrix
3. state transition inventory
4. export asset inventory
- 완료 기준:
1. 현재 truth와 v2.1 target 간 gap 목록 확정
2. 파일별 수정 범위 확정

## Phase 1 - Truth Hardening Foundations
- 구현:
1. `IssueTarget`/`FixStrategy` 도입
2. `InputFingerprintService` 도입
3. `ValidationFreshnessService` 도입
4. `CaseService` legal transition guard 도입
- 완료 기준:
1. stale 판단이 파일/컨텍스트/정책 변경에 반응
2. illegal state transition이 예외 처리됨

## Phase 2 - Policy Catalog
- 구현:
1. policy schema 정의
2. loader/resolver 구현
3. precedence 적용 (global -> platform -> scope -> reason -> network)
- 완료 기준:
1. 하드코딩 분기 감소
2. policyVersion/contextKey를 일관되게 획득

## Phase 3 - Coverage / Readiness
- 구현:
1. evidence coverage 서비스
2. scoring 서비스 (advisory)
3. checklist 서비스
4. controller-local readiness 로직 제거
- 완료 기준:
1. required/recommended/optional 구분 노출
2. freshness가 summary/payReady/exportReady에 반영

## Phase 4 - Export Pack v2.1
- 구현:
1. `checklist.pdf` 추가
2. `manifest.json` secure snapshot 구조화
3. stale 상태 pack build 차단
4. deterministic ordering 보장
- 완료 기준:
1. ZIP = summary + checklist + manifest + normalized files
2. manifest에 민감정보 0건

## Phase 5 - Optional SEO Dataization
- 구현:
1. guides data 외부화
2. route surface 유지
- 완료 기준:
1. core delivery와 분리된 상태로 배포 가능

## 5) 파일별 작업 타깃
1. `src/main/java/com/example/demo/dispute/service/ValidationService.java`
2. `src/main/java/com/example/demo/dispute/service/AutoFixService.java`
3. `src/main/java/com/example/demo/dispute/service/CaseService.java`
4. `src/main/java/com/example/demo/dispute/web/WebCaseController.java`
5. `src/main/java/com/example/demo/dispute/service/SubmissionExportService.java`
6. `src/main/java/com/example/demo/dispute/web/SeoController.java` (optional phase)
7. 신규 패키지 후보:
   - `policy/*`
   - `readiness/*`

## 6) 테스트 전략 (회귀 우선)
1. stale validation blocks pay/export
2. manifest excludes caseToken
3. state transition guard
4. Shopify credit multi-file export correctness
5. checkout duplicate prevention
6. controller no longer computes full-enum missing evidence
7. supported auto-fix only stays FIXABLE
8. deterministic score for same inputs

## 7) 배포 Go/No-Go 체크리스트
1. create -> upload -> validate -> auto-fix -> pay -> export core flow 정상
2. paid gating truth와 UI 메시지 일치
3. stale 상태에서 ZIP 차단 검증 완료
4. 정책/입력 스냅샷으로 결과 재현 가능
5. 민감정보 유출 없는 export artifact 확인

## 8) 리스크 로그
1. 과설계 리스크: 한번에 다 구현 금지, Phase 단위 병합
2. 마이그레이션 리스크: optional schema는 backward compatible 우선
3. UX 기대 리스크: FIXABLE 의미 변경 시 UI 문구 동시 업데이트
4. 품질 리스크: payment-export E2E 테스트 누락 시 출시 금지

## 9) 진행 추적 (작업용)
- [ ] Phase 0 Audit Freeze
- [ ] Phase 1 Truth Hardening Foundations
- [ ] Phase 2 Policy Catalog
- [ ] Phase 3 Coverage / Readiness
- [ ] Phase 4 Export Pack v2.1
- [ ] Phase 5 Optional SEO Dataization

## 10) Codex 메모 앵커 (까먹지 않기)
1. 지금은 "똑똑해 보이는 확장"이 아니라 "거짓 없는 동작"이 목표다.
2. score와 권한을 섞는 순간 설계가 무너진다.
3. stale를 무시하면 readiness는 거짓말이 된다.
4. manifest 보안은 기능이 아니라 신뢰의 핵심이다.
5. core가 끝나기 전 SEO/신기능으로 새지 않는다.
