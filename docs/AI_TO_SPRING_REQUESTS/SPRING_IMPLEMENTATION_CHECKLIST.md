# Spring 팀 구현 체크리스트 (Implementation Checklist)

> **작성일**: 2026-01-02  
> **목적**: `big_data_processing.md` 아키텍처 기반 Spring 팀 구현 사항 정리  
> **참고**: `docs/SPRING_IMPLEMENTS/SPRING_AI_INTEGRATION_FULL.md`

---

## ✅ 구현 완료된 항목

### 1. 인프라 (Infrastructure)
- [x] **pgvector 도입**: `pgvector/pgvector:pg16` 이미지 사용
- [x] **hibernate-vector**: JPA에서 `vector` 타입 지원
- [x] **스키마 마이그레이션**: `sections.embedding` → `vector(1024)`
- [x] **IVFFlat 인덱스**: 벡터 검색용 인덱스 생성

### 2. RabbitMQ (Producer)
- [x] **Queue 분리**: `document_analysis_queue`, `global_merge_queue`
- [x] **DTO 업데이트**: `message_type` 필드 추가 (`DOCUMENT_ANALYSIS`, `GLOBAL_MERGE`)
- [x] **VHost 설정**: `stolink` vhost 사용

### 3. Callback API (Consumer)
- [x] **통합 엔드포인트**: `POST /api/ai-callback`
- [x] **분기 처리**: `message_type` 기준으로 로직 분리
  - `DOCUMENT_ANALYSIS_RESULT` → 섹션 저장
  - `GLOBAL_MERGE_RESULT` → 노드 병합

### 4. Global Merge
- [x] **Hard Merge 전략**: APOC `mergeNodes` 사용
- [x] **Manual Trigger API**: `POST /api/project/{projectId}/merge`

---

## ⏳ 미구현 / 추가 권장 항목

### 1. 챕터 분할 로직 (Cascading Fallback)
> `big_data_processing.md` Line 554-574 참조

```java
public List<Chapter> splitChapters(String rawText) {
    // 1차: 명시적 마커 (제1장, Chapter 1)
    // 2차: 빈 줄 + 제목 패턴
    // 3차: 빈 줄 기반
    // 4차: 고정 글자 수 (10,000자)
}
```
- **상태**: 구현 여부 확인 필요
- **중요도**: ⭐⭐⭐ (대용량 처리의 핵심)

### 2. SSE 실시간 상태 알림
> `big_data_processing.md` Line 550-552 참조

- **설명**: 분석 진행 상황을 클라이언트에게 실시간 푸시
- **엔드포인트**: `GET /api/project/{id}/status/stream`
- **상태**: 미확인
- **중요도**: ⭐⭐ (UX 향상)

### 3. Automatic Merge Trigger
> 현재는 Manual API만 존재

- **설명**: 프로젝트의 모든 챕터 분석 완료 시 자동으로 Global Merge 실행
- **구현 위치**: `AICallbackService`에서 완료 조건 체크
- **상태**: 미구현 (옵션)
- **중요도**: ⭐ (편의 기능)

### 4. 에러 핸들링 강화
- **DLQ (Dead Letter Queue)**: 실패한 메시지 재처리 로직
- **Retry 정책**: 콜백 실패 시 재시도
- **상태**: 미확인
- **중요도**: ⭐⭐ (운영 안정성)

---

## 📋 테스트 전 최종 확인 사항

| 항목 | 확인 |
|---|---|
| `callback_url`이 `http://stolink-backend:8080/api/ai-callback` 형식인가? | ☐ |
| `AnalysisTaskDTO`에 `message_type` 필드가 포함되어 있는가? | ☐ |
| 테스트용 문서 데이터(긴 텍스트)가 DB에 준비되어 있는가? | ☐ |
| RabbitMQ vhost `stolink`에 권한이 있는가? | ☐ |

---
위 내용을 검토하시고, 미구현 항목 중 필요한 것이 있으면 구현해 주세요.
