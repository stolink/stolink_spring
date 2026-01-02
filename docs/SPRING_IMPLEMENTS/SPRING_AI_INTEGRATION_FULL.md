# Spring Backend AI 연동 구현 상세 보고서

> **작성일**: 2026-01-02
> **작성자**: Spring Team
> **목적**: AI 서버와의 연동을 위해 수행된 모든 백엔드 구현 사항 정리

---

## 1. 🐘 Vector DB 도입 (pgvector)

AI의 시맨틱 검색(의미 기반 검색)을 지원하기 위해 PostgreSQL을 벡터 데이터베이스로 확장했습니다.

### 1-1. 인프라 변경
*   **Docker Image**: 기존 `postgres:16.11-alpine`에서 `pgvector/pgvector:pg16`으로 변경하여 `vector` 확장을 지원합니다.
*   **Dependencies**: JPA에서 벡터 타입을 다루기 위해 `hibernate-vector` 의존성을 추가했습니다.

### 1-2. 스키마 및 마이그레이션
*   **Schema**: `Section` 엔티티의 `embedding` 컬럼을 기존 `TEXT/JSON`에서 `vector(1024)` 타입으로 변경했습니다. (Amazon Titan Text Embeddings V2 호환)
*   **Index**: 고속 검색을 위해 `IVFFlat` 인덱스를 생성했습니다.
    ```sql
    CREATE INDEX idx_sections_embedding ON sections USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
    ```

### 1-3. 데이터 처리
*   **Conversion**: AI 서버로부터 `List<Double>` 형태로 오는 벡터 데이터를 `float[]` 배열로 변환하여 DB에 저장하도록 `AICallbackService` 로직을 수정했습니다.

---

## 2. 🌐 Global Merge (캐릭터 통합)

중복된 캐릭터(예: "길동" == "홍길동")를 그래프 데이터베이스 수준에서 물리적으로 병합하는 로직을 구현했습니다.

### 2-1. 전략: Physical Merge (Hard Merge)
*   **도구**: Neo4j의 APOC 라이브러리(`apoc.refactor.mergeNodes`)를 사용합니다.
*   **정책**:
    *   **Properties**: `discard` (Primary 노드의 속성을 유지)
    *   **Relationships**: `mergeRels: true` (중복 노드의 모든 관계를 Primary 노드로 이동)

### 2-2. 구현 상세
*   **Repository**: `CharacterRepository.mergeNodes` 메서드 추가 (Custom Cypher Query 사용)
*   **Process**:
    1. AI로부터 `{ primaryId, mergedIds[] }` 콜백 수신
    2. 별칭(Alias) 등 리스트 데이터는 Java 레벨에서 합집합(Union) 처리
    3. `mergeNodes`를 호출하여 그래프 병합 실행

---

## 3. 🔌 연동 인터페이스 (Integration)

AI 서버와의 비동기 통신을 위한 메시지 큐 및 API를 구축했습니다.

### 3-1. RabbitMQ (Producer)
*   **Queue 분리**: `document_analysis_queue` (신규 분석용)와 `stolink.image.queue` (기존 이미지 생성용)를 분리하여 관리합니다.
*   **DTO Update**: 페이로드 호환성을 위해 `AnalysisTaskDTO`에 `message_type` 필드를 추가했습니다.
    *   `DOCUMENT_ANALYSIS`: 문서 분석 요청
    *   `GLOBAL_MERGE`: 병합 요청

### 3-2. Callback API (Consumer)
*   **Endpoint**: `POST /api/ai-callback` (통합 콜백 주소)
*   **Logic**: `message_type` 필드를 기준으로 분기 처리
    *   `DOCUMENT_ANALYSIS_RESULT`: 섹션 저장 로직 수행
    *   `GLOBAL_MERGE_RESULT`: 노드 병합 로직 수행

### 3-3. Manual Trigger
*   테스트를 위해 수동으로 글로벌 병합을 요청할 수 있는 API를 구현했습니다.
*   `POST /api/project/{projectId}/merge`

---

## 4. 🔐 보안 및 기타 (Security & Misc)

### 4-1. User Schema Fix
*   `User` 테이블에 누락되었던 `provider` 컬럼을 추가하고, `nickname` 등 필수 필드 제약 조건을 확인하여 회원가입/로그인이 정상 작동하도록 수정했습니다.

### 4-2. Docker Environment
*   `docker-compose.spring.yml`을 통해 백엔드를 독립적으로 실행하거나, 기존 인프라 네트워크(`sto-link-ai-backend_stolink-network`)에 연결할 수 있도록 구성했습니다.
