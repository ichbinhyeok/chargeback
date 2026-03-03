# Chargeback Builder - Handoff (2026-03-03)

작성일: 2026-03-03 (Asia/Seoul)
브랜치: main
원격: https://github.com/ichbinhyeok/chargeback
기준 커밋: a8e7e7d

## 1) 오늘 무엇을 했는가

### 1.1 백엔드 핵심 기능 구현
- Case 생성/업로드/분류/검수/리포트/삭제 API 구현 및 연결
- 검수 이력 저장(ValidationRun + ValidationIssue) 구현
- 감사 로그(AuditLog) 저장 구현
- 보관기간 만료 자동삭제 스케줄러(RetentionCleanupService) 구현
- Auto-fix Job 파이프라인 구현
  - `POST /api/cases/{caseId}/fix`
  - `GET /api/cases/{caseId}/fix/{jobId}`
  - 상태: `QUEUED -> RUNNING -> SUCCEEDED/FAILED`
  - 현재 자동수정 지원: "같은 evidence type에 여러 파일" 중복 제거(최신 1개 유지)
  - 수정 후 자동 재검수 + validation 이력 저장(`AUTO_FIX`) + 케이스 상태 반영

### 1.2 DB/마이그레이션
- Flyway 마이그레이션 적용
  - `V1__create_cases.sql`
  - `V2__create_evidence_files.sql`
  - `V3__create_validation_tables.sql`
  - `V4__create_audit_logs.sql`
  - `V5__create_fix_jobs.sql`

### 1.3 테스트 확장
- 검수 규칙 테스트 대폭 확대 (`ValidationServiceTest`)
- API 통합 테스트 확대 (`CaseControllerIntegrationTest`)
- 전체 테스트 수: 27
- 검증 명령:
  - `./gradlew.bat clean test` (Windows)
  - 성공 확인 완료

## 2) 현재 동작 범위

### 2.1 구현 완료 API
- `POST /api/cases`
- `POST /api/cases/{caseId}/files`
- `GET /api/cases/{caseId}/files`
- `PATCH /api/cases/{caseId}/files/{fileId}/classification`
- `POST /api/cases/{caseId}/validate`
- `POST /api/cases/{caseId}/validate-stored`
- `GET /api/cases/{caseId}/report`
- `POST /api/cases/{caseId}/fix`
- `GET /api/cases/{caseId}/fix/{jobId}`
- `DELETE /api/cases/{caseId}`

### 2.2 플랫폼 룰(현재 코드 기준)
- Stripe
  - PDF/JPEG/PNG
  - 총 4.5MB
  - 총 50p 미만
  - Mastercard 19p 제한
  - 타입별 1파일
  - 외부 링크 금지
- Shopify
  - PDF/JPEG/PNG
  - 파일당 2MB
  - 합산 4MB(Shopify Payments)
  - 합산 4.5MB(Shopify Credit)
  - PDF 50p 미만
  - PDF Portfolio 금지
  - 타입별 1파일
  - 외부 링크 금지
  - Early submit 경고
  - **주의: 현재 코드는 PDF/A 준수를 요구하도록 구현됨(`ERR_SHPFY_PDF_NOT_PDFA`)**

## 3) 반드시 확인할 정책 이슈 (중요)

### 3.1 Shopify PDF/A 방향 재확정 필요
- 문서 버전 간 충돌 이력이 있었음
  - 버전 A: PDF/A 금지
  - 버전 B: PDF/A 필수
- 현재 코드: **PDF/A 필수**로 동작
- 해야 할 일:
  1. Shopify 공식 문서 최신 정책 재확인
  2. 결과에 따라 룰/에러코드/Auto-fix 문구/문서 전체 일괄 정합성 수정

## 4) 다음에 해야 할 일 (프론트 이전 백엔드 우선)

### P0 (바로 진행)
1. 산출물 생성 파이프라인 구현
- `Evidence Upload Kit ZIP`
- `Master Binder PDF`
- `Checklist PDF`

2. 결제 직전까지 필요한 백엔드 상태머신 정리
- `READY -> PAYWALL_PENDING -> PAID -> DOWNLOAD_READY` 등
- 다운로드 토큰/만료 처리

3. Auto-fix 확장
- 단순 dedupe 외에 용량/페이지 관련 fix 가능한 항목 단계적 추가
- 실패 시 단일 원인(return one reason) 정책 강화

### P1 (곧바로)
1. 백엔드 운영 안정화
- idempotency 강화(webhook/events 대비)
- 장애/실패 재시도 정책
- structured logging + trace id

2. 보안/개인정보
- 삭제 정책 검증(E2E)
- 민감정보 로그 마스킹

## 5) 다른 로컬(새 PC)에서 바로 이어받기

### 5.1 클론 및 브랜치
```powershell
git clone https://github.com/ichbinhyeok/chargeback.git
cd chargeback
git checkout main
```

### 5.2 JDK 설정(예시)
```powershell
$env:JAVA_HOME=".\.jdk\jdk-21.0.10+7"
.\gradlew.bat clean test
```

- 로컬에 `.jdk` 폴더가 없으면 시스템 JDK 21 설치 후 `JAVA_HOME`을 해당 경로로 지정

### 5.3 앱 실행
```powershell
$env:JAVA_HOME="C:\Path\To\JDK21"
.\gradlew.bat bootRun
```

- 기본 DB: H2 in-memory
- 마이그레이션: Flyway 자동 실행
- 설정 파일: `src/main/resources/application.properties`

## 6) 참고 파일 맵
- 핵심 API: `src/main/java/com/example/demo/dispute/api/CaseController.java`
- 검수 엔진: `src/main/java/com/example/demo/dispute/service/ValidationService.java`
- 자동수정: `src/main/java/com/example/demo/dispute/service/AutoFixService.java`
- 케이스/삭제: `src/main/java/com/example/demo/dispute/service/CaseService.java`
- 리포트: `src/main/java/com/example/demo/dispute/service/CaseReportService.java`
- 파일처리: `src/main/java/com/example/demo/dispute/service/EvidenceFileService.java`
- PDF 메타 추출: `src/main/java/com/example/demo/dispute/service/pdf/PdfMetadataExtractor.java`
- 테스트(룰): `src/test/java/com/example/demo/dispute/ValidationServiceTest.java`
- 테스트(API): `src/test/java/com/example/demo/dispute/CaseControllerIntegrationTest.java`

## 7) 오늘 기준 결론
- 백엔드 코어(케이스/검수/이력/자동수정/삭제/테스트)는 작동 가능한 베이스 완료
- 결제/산출물 생성/다운로드 제어는 아직 미구현
- Shopify PDF/A 정책은 반드시 확정 후 룰을 단일 진실원으로 고정해야 함
