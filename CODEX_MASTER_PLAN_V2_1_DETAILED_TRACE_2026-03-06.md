# CODEX MASTER PLAN v2.1 - DETAILED TRACE (2026-03-06)

## 0) 왜 요약본이 짧았는가
- 기존 `[CODEX_MASTER_PLAN_V2_1_2026-03-06.md]`는 "실행 우선" 문서다.
- 구현자가 흔들리지 않게 핵심 규칙과 순서만 남긴 압축판이다.
- 이 문서는 압축 과정에서 빠진 문맥과 근거를 복원하는 상세판이다.

## 1) 입력 자료와 사용 목적
1. `chargeback_analysis_report_ko.pdf`
   - 목적: 시장성/포지셔닝/매출 가능성/운영 리스크/P0·P1 실무 이슈 근거 확보
2. `chargeback_repo_bible_v2_1_truth_hardening.md`
   - 목적: 아키텍처 규약/불변식/서비스 경계/단계별 구현 계약 정의

## 2) 결론 요약 (자료 전체 기준)
1. 제품 방향은 확장보다 정합성이 맞다.
2. "AI 승소율 엔진"이 아니라 "증빙 pack validator/exporter" 포지셔닝이 맞다.
3. 실제 매출 문제의 핵심은 신기능 부족보다 P0 결함과 신뢰 자산 부족이다.
4. 기술적으로는 전면 재작성보다 hardening patch가 ROI가 높다.

## 3) 원문 근거 -> 실행 계획 추적 매트릭스

| 원문 근거 | 핵심 주장 | 구현/운영 반영 | 대응 Phase | 테스트 기준 |
|---|---|---|---|---|
| PDF p2 Executive Summary | SaaS MVP이지만 export/payment 정합성 결함 존재 | 결제 메시지 truth 일치, 중복 checkout 방지 | Phase 1, 4 | paid/unlock 통합 테스트 |
| PDF p3~p4 | Shopify Credit export 누락 리스크 | productScope별 export 규칙 분기 | Phase 4 | multi-file export 회귀 테스트 |
| PDF p4 | FIXABLE 과장 위험 | AUTO_FIXABLE vs MANUAL 구분 | Phase 1, 3 | unsupported fix는 manual 유지 |
| PDF p4~p5 | policy 반영 정확도는 강점 | 정책 데이터화는 유지하되 truth hardening 우선 | Phase 2 | policy precedence 테스트 |
| PDF p5 | 보안 기능은 있으나 신뢰 자산 부족 | privacy/security/retention 공개 자산 보강 | 운영 트랙 (코드 외) | 랜딩/문서 점검 |
| PDF p6~p7 | 주제는 좋으나 경쟁 포지션 정밀화 필요 | 카피를 validator/exporter로 좁힘 | 운영 트랙 | 전환 실험 |
| PDF p7~p8 | 월 100만원은 가능하나 funnel 관리 필요 | 핵심 전환 지표 계측 우선 | 운영 트랙 | funnel 지표 수집 |
| PDF p8 | 30일 실행안은 P0부터 | 개발 순서 고정 (P0 -> P1 -> P2) | 전체 | 단계별 DoD |
| Bible §5 | 핵심은 contract 추가(stale/target/manifest/snapshot) | 엔티티/서비스 계약 도입 | Phase 1~4 | 계약별 단위 테스트 |
| Bible §6 Rule 1 | score는 advisory only | 권한 결정 로직에서 score 제거 | Phase 3~4 | score와 권한 분리 테스트 |
| Bible §6 Rule 6 | manifest 비밀정보 금지 | caseToken/path/secret scrub | Phase 4 | manifest 민감정보 검사 |
| Bible §10 | Validation은 file-rule 전담 | coverage 책임 분리 | Phase 3 | validation/coverage 분리 테스트 |
| Bible §10.2 | issue target 명시 필요 | IssueTargetScope/IssueTarget 도입 | Phase 1 | target 매핑 테스트 |
| Bible §10.4 | FIXABLE truth 강제 | 실제 구현 fix 전략만 FIXABLE | Phase 1 | FIXABLE 정합성 테스트 |
| Bible §11 | stale 판정은 필수 | inputFingerprint+context+policy version 비교 | Phase 1,3 | freshness 테스트 |
| Bible §12 | coverage는 policy-aware | required/recommended/optional 구분 | Phase 3 | coverage 상태 테스트 |
| Bible §13 | pay/export는 score와 분리 | state/payment/freshness 기반 gating | Phase 3~4 | payReady/exportReady 테스트 |
| Bible §15 | state transition legality 강제 | CaseService transition matrix | Phase 1 | illegal transition 테스트 |
| Bible §17~18 | export pack self-describing + secure | checklist.pdf + manifest.json + deterministic files | Phase 4 | export artifact 테스트 |
| Bible §19 | snapshot 재현성 | policy/validation/pack snapshot 연결 | Phase 2~4 | snapshot consistency 테스트 |
| Bible §25 | 회귀 테스트 doctrine | 핵심 회귀군 우선 작성 | 모든 phase | regression suite |

## 4) 압축본에 의도적으로 줄인 내용
1. 시장/경쟁 상세 분석 텍스트
2. GTM 채널별 실행 디테일
3. pricing 시나리오(USD 19/29/49/79)
4. SEO 가이드 확장안 상세
5. 레퍼런스 목록(외부 링크형 참고문헌)

이 항목들은 "코드 구현 지시 문서"에선 노이즈가 될 수 있어 압축했다.

## 5) 구현 우선순위 재확인 (자료 전체 반영본)

### 5.1 반드시 먼저
1. Shopify Credit export 누락 방지
2. 결제 성공 UI와 실제 unlock truth 정렬
3. 중복 checkout session 방지
4. stale validation 차단
5. manifest 민감정보 제거

### 5.2 그 다음
1. issue target contract
2. fixability contract
3. state transition legality
4. readiness service화
5. checklist 중심 UX/출력

### 5.3 마지막
1. policy catalog 고도화
2. snapshot 재현성 완성
3. optional SEO dataization

## 6) 운영/GTM 병렬 트랙 (코드와 별도)
1. 포지셔닝 문구를 `AI 승소`에서 `증빙 패키지 실수 방지`로 변경
2. privacy/security/retention 페이지 공개
3. redacted demo pack 공개
4. 초기 유입을 SEO 단독이 아닌 직접 채널(대행사/컨설턴트/셀러 커뮤니티)로 확보

## 7) 우리가 까먹기 쉬운 함정
1. score를 편의상 권한에 연결하는 유혹
2. "곧 지원 예정" 기능을 FIXABLE로 표시하는 과장
3. stale를 UI 배지 정도로만 처리하고 권한 차단을 누락하는 실수
4. export manifest에 디버깅 편의 정보(경로/토큰)를 남기는 실수
5. SEO/신기능으로 코어 품질 이슈보다 앞서 나가는 실수

## 8) 이 문서의 사용 규칙
1. 구현 시작 전: §3 매트릭스에서 해당 작업의 근거 행 확인
2. PR 작성 시: "어떤 행을 해결했는지" 명시
3. 릴리스 직전: §5, §7 체크 후 Go/No-Go 결정

## 9) 상태
- 작성일: 2026-03-06
- 상태: Active
- 상위 실행문서: `CODEX_MASTER_PLAN_V2_1_2026-03-06.md`

## 10) 플랜 카운트 (원문 재분석 반영)
아래 숫자는 PDF + Bible을 기준으로 "실행 가능한 작업 단위"로 재분해한 결과다.

### 10.1 전략/원칙 카운트
1. 제품 정의 고정 규칙: 1개
2. 하드 룰(불변 규칙): 10개
3. 핵심 계약(contract): 6개
4. 스냅샷 재현성 계층: 3개
5. SEO 분리 원칙: 1개
6. 합계: 21개

### 10.2 구현 작업 카운트 (엔지니어링)
Phase별 구현 작업 단위를 다음처럼 세었다.

1. Phase 0 Audit Freeze: 8개
   - issue matrix 작성
   - fixability matrix 작성
   - current rule inventory
   - state transition inventory
   - export asset inventory
   - P0/P1/P2 태깅
   - 파일별 영향도 매핑
   - risk register 초안

2. Phase 1 Truth Hardening Foundations: 12개
   - IssueTargetScope 정의
   - IssueTarget 모델 정의
   - ValidationIssue target 확장
   - FixStrategy enum 도입
   - fixability map 구축
   - InputFingerprintService 도입
   - ValidationFreshnessService 도입
   - stale 판정 API 설계
   - CaseService transition matrix 구현
   - illegal transition 예외화
   - state audit 로그 보강
   - 기본 회귀 테스트 추가

3. Phase 2 Policy Catalog: 10개
   - policy schema 정의
   - metadata 필드 반영
   - YAML loader
   - resolver
   - precedence 구현
   - wildcard fallback
   - context key normalize
   - policy versioning 처리
   - 초기 catalog 파일 분리
   - policy 테스트 추가

4. Phase 3 Coverage/Readiness: 12개
   - EvidenceCoverageService
   - Group coverage 처리(one-of)
   - requirement level 분리(required/recommended/optional)
   - coverage state 집계
   - ReadinessScoringService
   - stale score cap 반영
   - payReady/exportReady truth 분리
   - ReadinessChecklistService
   - ReadinessFacade
   - controller readiness 제거
   - explainability reasons 추가
   - 통합 렌더링 테스트 보강

5. Phase 4 Export Pack v2.1: 12개
   - checklist.pdf 생성
   - summary.pdf에 freshness/snapshot 반영
   - manifest.json 구조화
   - caseToken 금지 검증
   - publicCaseRef 도입
   - pack snapshot 필드 반영
   - deterministic 파일 정렬
   - stale시 pack build 차단
   - paid gating 정합화
   - Shopify Credit export 정책 분기
   - export README(optional) 추가
   - export 회귀 테스트 추가

6. Phase 5 Optional SEO Dataization: 6개
   - guides data externalization
   - policy metadata 재사용 연결
   - route surface 유지
   - noindex/no-store 검증 유지
   - SEO 변경과 core 분리 검증
   - optional release gate

7. 엔지니어링 구현 합계: 60개

### 10.3 테스트 카운트
Bible §25 기준 테스트 항목을 그대로 집계:

1. highest priority regression: 7
2. policy resolution: 5
3. freshness: 5
4. issue target: 4
5. coverage: 7
6. score: 7
7. export: 7
8. controller integration: 4
9. 합계: 46

### 10.4 운영/GTM 카운트 (코드 외)
PDF 인사이트 기반 운영 과제:

1. 포지셔닝 카피 정렬(validator/exporter): 2
2. 신뢰 자산(privacy/security/retention): 3
3. proof asset(redacted demo, FAQ): 2
4. 채널 실험(대행사/컨설턴트/커뮤니티): 3
5. 가격 실험(case-based vs subscription): 2
6. 합계: 12

### 10.5 총합
1. 전략/원칙: 21
2. 구현 작업: 60
3. 테스트: 46
4. 운영/GTM: 12
5. 전체 실행 단위 총합: 139

## 11) 30일 실행 재구성 (원문 정합 버전)
PDF의 30일 제안을 Bible 구현 순서와 정합되게 다시 엮은 버전:

### Week 1
1. Phase 0 완료
2. Phase 1의 P0 항목 완료
3. P0 회귀 테스트(결제/내보내기/manifest) 선반영

### Week 2
1. Phase 2 완료(최소 catalog)
2. Phase 3 착수(coverage/readiness core)
3. controller readiness 제거 시작

### Week 3
1. Phase 3 완료
2. Phase 4 착수(checklist/manifest/pack snapshot)
3. paid/unlock UI 정합 완성

### Week 4
1. Phase 4 완료
2. regression suite 안정화
3. 운영 트랙(카피/신뢰 자산/초기 채널) 병행
4. Phase 5는 선택적으로만 착수

## 12) Go/No-Go 정량 게이트
출시 판단을 감으로 하지 않기 위한 수치형 게이트:

1. 필수 P0 버그 오픈 이슈: 0
2. manifest 민감정보 검출: 0
3. stale 상태 ZIP 허용 케이스: 0
4. 결제 성공 메시지 오표시 재현: 0
5. 회귀 테스트 통과율: 100%
6. pay 클릭 -> paid -> zip 다운로드 성공률(스테이징): 95%+

## 13) 사용자 인사이트 반영 여부 체크
"포지셔닝까지 포함됐냐"에 대한 명시 체크:

1. 포함됨: 제품 정의 고정(validator/exporter)
2. 포함됨: 확장 금지(OAuth/subscription/AI rebuttal 등)
3. 포함됨: P0 우선(결제/내보내기/신뢰)
4. 포함됨: GTM 병렬 트랙(카피/채널/신뢰 자산)
5. 포함됨: 월 100만원 목표를 위한 funnel 관점

즉, 기술 플랜 + 포지셔닝/GTM 인사이트가 분리되지 않고 같이 묶여 있다.
