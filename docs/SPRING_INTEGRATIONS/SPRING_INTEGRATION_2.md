# AI Backend 연동 테스트 가이드 (For Spring Team)

> **작성일**: 2026-01-02  
> **목적**: Spring Backend에서 AI 기능을 테스트하기 위한 단계별 가이드  

---

## 1. 사전 준비 (Prerequisites)

### 1-1. 인프라 확인
- **RabbitMQ**: VHost `stolink` 생성 여부 및 `stolink.analysis.queue` 등 큐 존재 확인
- **Postgres**: `vector` 확장이 활성화되었는지 확인 (`CREATE EXTENSION IF NOT EXISTS vector;`)
- **Python Backend**: 컨테이너(`ai-backend`) 정상 실행 중 확인 (로그: `Document Analysis Consumer started`)

### 1-2. 테스트 데이터 (Test Data)
AI Backend는 `db_query_service`를 통해 DB를 직접 조회하므로, **유효한 데이터가 DB에 먼저 저장**되어 있어야 합니다.

```sql
-- 예시 데이터 (Spring에서 Insert 필요)
INSERT INTO projects (id, title, status, ...) VALUES ('proj-123', '테스트 프로젝트', 'WRITING', ...);
INSERT INTO documents (id, project_id, type, title, content, ...) 
VALUES ('doc-abc', 'proj-123', 'TEXT', '챕터 1', '...긴 본문 텍스트...', ...);
```

---

## 2. 테스트 시나리오 (Test Scenarios)

### Scenario A: 단일 문서 분석 (Document Analysis)
1. **Trigger**: Spring에서 RabbitMQ로 메시지 발행
   - **Exchange**: `amq.default` (또는 설정된 Exchange)
   - **Routing Key**: `document_analysis_queue`
   - **Payload**:
     ```json
     {
       "message_type": "DOCUMENT_ANALYSIS",
       "project_id": "proj-123",
       "document_id": "doc-abc",
       "status": "PENDING",
       "callback_url": "http://host.docker.internal:8080/api/ai-callback"
     }
     ```
2. **AI Action**:
   - `ChunkingService`가 텍스트를 Semantic Section으로 분할
   - 각 Section 임베딩 생성 (1024 dim)
   - 캐릭터/이벤트 추출
3. **Observation**:
   - **Log**: Python 컨테이너 로그에서 `Created X semantic sections` 확인
   - **Callback**: Spring API로 `DOCUMENT_ANALYSIS_RESULT` 수신 확인
   - **DB**: `sections` 테이블에 데이터 생성 여부 확인

### Scenario B: 글로벌 병합 (Global Merge)
1. **Trigger**: Spring에서 RabbitMQ로 메시지 발행
   - **Routing Key**: `global_merge_queue`
   - **Payload**:
     ```json
     {
       "message_type": "GLOBAL_MERGE",
       "project_id": "proj-123",
       "callback_url": "..."
     }
     ```
2. **AI Action**:
   - 해당 프로젝트의 모든 캐릭터 로드
   - `EntityResolution` 로직으로 유사 캐릭터 그룹화
3. **Observation**:
   - **Callback**: `GLOBAL_MERGE_RESULT` 수신 (병합된 ID 리스트 포함)
   - **Neo4j**: (Spring 측 구현 필요) 그래프 병합 수행 확인

---

## 3. 트러블슈팅 (Troubleshooting)

| 증상 | 확인 사항 |
|---|---|
| **Callback 수신 안짐** | `callback_url`이 Docker 내부 네트워크에서 접근 가능한지 확인 (`host.docker.internal` 등) |
| **분석 실패 (500 Error)** | 문서 내용이 너무 짧거나 비어있는지 확인 (최소 100자 권장) |
| **Embedding 에러** | AWS 자격 증명(Env) 확인 및 Titan V2 접근 권한 확인 |
| **DB 조회 실패** | 메시지에 담긴 `document_id`가 DB에 실제로 존재하는지 확인 (UUID 포맷) |

---
위 가이드를 참고하여 연동 테스트를 진행해 주시기 바랍니다.
