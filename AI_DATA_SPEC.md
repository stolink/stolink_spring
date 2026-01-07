# AI Backend 데이터 저장 명세서 (AI Data Persistence Spec)

이 문서는 AI Backend가 분석 과정에서 **직접 데이터베이스(PostgreSQL, Neo4j)에 저장하는 데이터**의 상세 명세를 기술합니다.
Spring Backend 팀은 이 명세를 참고하여 **데이터 중복 저장을 방지**하고, **AI가 저장하지 않는 데이터(예: 관계 Edge)만 선별적으로 처리**해야 합니다.

---

## 1. 개요 (Data Flow)

1. **AI Backend 역할:**
   - 텍스트 분석 후 주요 엔티티(`Character`, `Event`, `Setting`)와 벡터 데이터(`Section`)를 **직접 DB에 저장**합니다.
   - 분석 결과 전체(관계 포함)를 JSON으로 Spring Backend에 Callback으로 전송합니다.

2. **Spring Backend 권장 역할:**
   - Callback으로 받은 데이터 중, **이미 AI가 저장한 엔티티(Character, Event, Setting)는 DB에 다시 INSERT 하지 않습니다.** (조회/매핑만 수행)
   - **AI가 저장하지 않는 데이터(Relationship Edge, Link 등)를 Neo4j/Postgres에 저장**합니다.

---

## 2. PostgreSQL 저장 데이터 (`stolink` DB)

AI Backend는 다음 테이블에 데이터를 직접 `INSERT` / `UPSERT` 합니다.

### 2.1. `characters` (캐릭터 정보)
AI는 캐릭터의 기본 정보를 저장합니다.
- **저장 시점:** 분석 중 수시로 (Streaming) + 완료 시
- **Key:** `id` (UUID)
- **저장 필드:**
  - `id`: UUID
  - `project_id`: Project UUID
  - `name`: 캐릭터 이름
  - `role`: 역할 (Protagonist, Antagonist, Supporting 등)
  - `description`: 상세 설명 (외모, 성격 등 포함)
  - `aliases_json`: 별칭 리스트 (JSONB)
  - `created_at`, `updated_at`

### 2.2. `events` (사건 정보)
AI는 주요 사건 정보를 저장합니다.
- **저장 시점:** 분석 중 수시로 + 완료 시
- **Key:** `id` (UUID)
- **저장 필드:**
  - `id`: UUID
  - `project_id`: Project UUID
  - `document_id`: Source Document UUID
  - `event_type`: 사건 유형 (Main, Sub 등 - 현재 대부분 'Unknown' 또는 값 없음)
  - `description`: 사건 요약 (Narrative Summary)
  - `chapter`: 챕터 번호 (Sequence)
  - `sequence_order`: 정렬 순서
  - `participants`: 참여자 이름 리스트 (JSONB - 참고용, 외래키 아님)
  - `location_ref`: 장소 이름 (참고용)
  - `created_at`, `updated_at`

### 2.3. `settings` (배경/장소)
AI는 배경 정보를 저장합니다.
- **저장 시점:** 분석 중 수시로 + 완료 시
- **Key:** `id` (UUID)
- **저장 필드:**
  - `id`: UUID
  - `project_id`: Project UUID
  - `name`: 장소 이름
  - `location_type`: 장소 유형 (Indoor, Outdoor, City 등)
  - `description`: 장소 설명
  - `created_at`, `updated_at`

### 2.4. `sections` (벡터 검색용 청크)
AI는 RAG(검색 증강 생성을 위한) 텍스트 청크와 임베딩 벡터를 저장합니다.
- **저장 시점:** 분석 시작 시 (Chunking 단계)
- **Key:** `id` (UUID)
- **저장 필드:**
  - `id`: UUID
  - `document_id`: Source Document UUID
  - `content`: 텍스트 내용 (Chunk)
  - `embedding`: `vector(768)` (Gemini Embedding)
  - `nav_title`: 섹션 제목 (네비게이션용)
  - `sequence_order`: 순서
  - `created_at`, `updated_at`

---

## 3. Neo4j 저장 데이터 (`Graph DB`)

AI Backend는 **노드(Node)만 생성/병합(MERGE)** 하며, **관계(Edge)는 저장하지 않을 가능성이 높습니다.** (코드 분석 결과)

### 3.1. `Character` (Node)
- **Label:** `:Character`
- **Key:** `id` (UUID)
- **Properties:**
  - `id`: UUID
  - `project_id`: Project UUID
  - `name`: 캐릭터 이름
  - `role`: 역할

### 3.2. `Event` (Node)
- **Label:** `:Event`
- **Key:** `id` (UUID)
- **Properties:**
  - `id`: UUID
  - `project_id`: Project UUID
  - `description`: 사건 요약
  - `chapter`: 챕터 번호

### 3.3. ⚠️ 저장되지 않는 데이터 (Spring 처리 필요)
AI 코드(`db_query_service.py`) 분석 결과, 다음 데이터는 **Neo4j에 저장되는 로직이 발견되지 않았습니다.** Spring Backend에서 Callback 결과를 받아 처리해야 합니다.

1.  **Relationships (Edge):**
    -   `(:Character)-[:RELATIONSHIP]->(:Character)`
    -   Type, Description, Strength 등의 속성 포함 Edge
2.  **Participation (Edge):**
    -   `(:Character)-[:PARTICIPATED_IN]->(:Event)`
3.  **Happen (Edge):**
    -   `(:Event)-[:HAPPENED_AT]->(:Setting)`
4.  **Setting (Node):**
    -   `settings` 테이블에는 저장되나, Neo4j 노드 생성 로직(`MERGE (:Setting)`)은 명시적으로 확인되지 않았습니다. (확인 필요)

---

## 4. 요약 및 Spring 팀 요청사항

1.  **엔티티 중복 저장 방지:** `Character`, `Event`, `Setting`은 AI가 이미 DB(Postgres)에 저장했습니다. Spring은 `id`를 기준으로 매핑만 하십시오.
2.  **그래프 관계 저장 필수:** AI는 `relationships` 데이터를 JSON 리스트로만 전달합니다. Spring은 이를 파싱하여 **Neo4j에 Edge를 생성**해야 합니다.
3.  **벡터 데이터 활용:** `sections` 테이블의 `embedding` 컬럼은 AI가 생성해 두었으므로, Spring에서 유사도 검색 (`<=>` 연산자) 등에 바로 활용할 수 있습니다.
