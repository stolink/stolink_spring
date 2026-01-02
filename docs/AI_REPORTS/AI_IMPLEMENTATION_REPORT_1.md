# Python AI Backend 구현 완료 보고서 - Spring 팀에게

> **작성일**: 2026-01-02  
> **작성자**: AI Backend 팀 (Python/FastAPI)  
> **목적**: 대용량 문서 분석 아키텍처 Python 측 구현 완료 보고

---

## 📋 구현 완료 항목

### ✅ 1. 메시지 스키마 확장 (`messages.py`)

Spring 팀이 정의한 DTO와 호환되는 Pydantic 스키마를 구현했습니다.

#### 신규 스키마

| 클래스 | 용도 | Spring DTO 대응 |
|--------|------|-----------------|
| `DocumentAnalysisMessage` | 분석 요청 수신 | `DocumentAnalysisMessage.java` |
| `GlobalMergeMessage` | 2차 Pass 요청 수신 | `GlobalMergeMessage.java` |
| `DocumentAnalysisCallback` | 분석 결과 전송 | `DocumentAnalysisCallbackDTO.java` |
| `GlobalMergeCallback` | 병합 결과 전송 | `GlobalMergeCallbackDTO.java` |
| `SectionOutput` | Section 데이터 | `SectionDTO.java` |
| `CharacterMergeResult` | 캐릭터 병합 결과 | `CharacterMergeDTO.java` |

#### DocumentAnalysisMessage 스키마

```python
class DocumentAnalysisMessage(BaseModel):
    message_type: str = "DOCUMENT_ANALYSIS"
    document_id: str          # Document(TEXT) UUID
    project_id: str
    parent_folder_id: Optional[str]
    chapter_title: Optional[str]
    document_order: Optional[int]
    total_documents_in_chapter: Optional[int]
    analysis_pass: int = 1
    callback_url: str
    context: Optional[AnalysisContext]
    trace_id: Optional[str]
```

#### DocumentAnalysisCallback 스키마

```python
class DocumentAnalysisCallback(BaseModel):
    message_type: str = "DOCUMENT_ANALYSIS_RESULT"
    document_id: str
    parent_folder_id: Optional[str]
    status: str  # "COMPLETED" or "FAILED"
    error: Optional[dict]
    sections: list[SectionOutput]
    characters: list[dict]
    events: list[dict]
    settings: list[dict]
    processing_time_ms: Optional[int]
    trace_id: Optional[str]
```

---

### ✅ 2. rapidfuzz 의존성 추가 (`pyproject.toml`)

```toml
dependencies = [
    # ... 기존 의존성 ...
    
    # Fuzzy Matching (Entity Resolution)
    "rapidfuzz>=3.9.0",
]
```

---

### ✅ 3. Entity Resolution 유틸리티 (`entity_resolution.py`)

캐릭터 이름 매칭을 위한 Fuzzy Matching 함수를 구현했습니다.

#### 주요 함수

```python
# 두 캐릭터가 동일인인지 판별
result = is_same_character(
    name1="이안",
    name2="Ian",
    aliases1=["이안이"],
    aliases2=["ian"]
)
# MatchResult(is_same=True, score=100.0, classification=AUTO_MERGE, match_reason="korean_english_mapping")
```

#### 3단계 분류 시스템

| 점수 | 분류 | 처리 |
|------|------|------|
| 95점 이상 | `AUTO_MERGE` | 자동 병합 |
| 80~94점 | `NEEDS_REVIEW` | 사용자 검증 필요 |
| 80점 미만 | `DIFFERENT` | 별개 캐릭터 |

#### 지원 기능

- ✅ 정규화된 이름 비교 (대소문자, 공백)
- ✅ 한글-영문 매핑 테이블 (`이안` ↔ `Ian`)
- ✅ 별칭(aliases) 교차 매칭
- ✅ rapidfuzz 가중 평균 점수 (ratio 50%, partial 30%, token_sort 20%)

---

### ✅ 4. DB 조회 서비스 확장 (`db_query_service.py`)

대용량 분석에 필요한 새 쿼리 메서드를 추가했습니다.

#### 신규 메서드

| 메서드 | 용도 |
|--------|------|
| `get_document_content(document_id)` | Claim Check Pattern - content 조회 |
| `get_document_with_parent_info(document_id)` | 상위 폴더 정보 포함 조회 |
| `get_all_project_characters_for_merge(project_id)` | 2차 Pass Entity Resolution용 |
| `get_project_analysis_status(project_id)` | 분석 진행 상황 조회 |

#### get_document_content 쿼리

```python
async def get_document_content(self, document_id: str):
    """Claim Check Pattern: document_id로 content 조회"""
    query = """
        SELECT d.id, d.title, d.content, d.type, d.order,
               d.parent_id, d.project_id, d.word_count
        FROM documents d
        WHERE d.id = $1 AND d.type = 'TEXT'
    """
```

---

### ✅ 5. 새 Consumer 골격 (`document_analysis_consumer.py`)

Spring 가이드에 맞춘 두 개의 Consumer를 구현했습니다.

#### DocumentAnalysisConsumer (1차 Pass)

```
1. document_analysis_queue 수신
2. PROCESSING 상태 API 호출
3. DB에서 content 조회 (Claim Check)
4. LangGraph 파이프라인 실행 (TODO)
5. Callback 전송 (sections, characters, events)
```

#### GlobalMergeConsumer (2차 Pass)

```
1. global_merge_queue 수신
2. 프로젝트의 모든 캐릭터 조회
3. Entity Resolution (is_same_character)
4. 병합 결과 Callback 전송
```

#### 흐름도

```
Spring                          Python
  │                               │
  ├── DOCUMENT_ANALYSIS ──────────▶ DocumentAnalysisConsumer
  │                               │   ├─ PATCH /api/documents/{id}/analysis-status
  │                               │   ├─ SELECT content FROM documents
  │                               │   ├─ LangGraph Pipeline
  │   ◀── Callback ───────────────┤   └─ POST /api/ai-callback
  │                               │
  │ (모든 문서 완료 시)            │
  ├── GLOBAL_MERGE ───────────────▶ GlobalMergeConsumer
  │                               │   ├─ SELECT * FROM characters
  │                               │   ├─ Entity Resolution
  │   ◀── Callback ───────────────┤   └─ POST /api/ai-callback
```

---

## 📊 설정 추가 (`config.py`)

```python
# Document Analysis Architecture (대용량 분석)
document_analysis_queue: str = "document_analysis_queue"
global_merge_queue: str = "global_merge_queue"
consumer_prefetch_count: int = 10

# Spring Backend
spring_backend_url: str = "http://localhost:8080"
```

환경 변수로 재정의 가능:
```bash
DOCUMENT_ANALYSIS_QUEUE=document_analysis_queue
GLOBAL_MERGE_QUEUE=global_merge_queue
CONSUMER_PREFETCH_COUNT=10
SPRING_BACKEND_URL=http://spring-backend:8080
```

---

## 🔄 Spring 팀 연동 확인 사항

### 1. 상태 업데이트 API

Python Consumer가 분석 시작 시 호출:

```http
PATCH /api/documents/{document_id}/analysis-status
Content-Type: application/json

{
  "status": "PROCESSING",
  "traceId": "trace-123"
}
```

**Spring 응답 기대**:
```json
{
  "documentId": "uuid",
  "previousStatus": "QUEUED",
  "currentStatus": "PROCESSING",
  "message": "분석 상태가 업데이트되었습니다."
}
```

### 2. 분석 결과 Callback

Python이 분석 완료 시 전송:

```http
POST /api/ai-callback
Content-Type: application/json

{
  "message_type": "DOCUMENT_ANALYSIS_RESULT",
  "document_id": "uuid",
  "parent_folder_id": "folder-uuid",
  "status": "COMPLETED",
  "sections": [
    {
      "sequence_order": 1,
      "nav_title": "이안의 각성",
      "content": "눈을 떴을 때...",
      "embedding": [0.123, -0.456, ...],
      "related_characters": ["이안", "나비"],
      "related_events": ["E001"]
    }
  ],
  "characters": [...],
  "events": [...],
  "settings": [...],
  "processing_time_ms": 5000,
  "trace_id": "trace-123"
}
```

### 3. 글로벌 병합 결과 Callback

```http
POST /api/ai-callback
Content-Type: application/json

{
  "message_type": "GLOBAL_MERGE_RESULT",
  "project_id": "project-uuid",
  "status": "COMPLETED",
  "character_merges": [
    {
      "primary_id": "char-이안-001",
      "merged_ids": ["char-ian-002"],
      "canonical_name": "이안",
      "merged_aliases": ["Ian", "ian"],
      "confidence": 0.95
    }
  ],
  "consistency_report": {
    "total_characters": 50,
    "merge_count": 5,
    "auto_merged": 3,
    "needs_review": 2
  },
  "processing_time_ms": 10000,
  "trace_id": "trace-123"
}
```

---

## 📁 생성/수정된 파일 목록

### 신규 생성 (2개 파일)

| 파일 | 설명 |
|------|------|
| `app/utils/entity_resolution.py` | Fuzzy Matching 유틸리티 |
| `app/services/document_analysis_consumer.py` | 대용량 분석 Consumer |

### 수정됨 (3개 파일)

| 파일 | 변경 내용 |
|------|----------|
| `app/schemas/messages.py` | 6개 신규 스키마 추가 |
| `app/services/db_query_service.py` | 4개 신규 쿼리 메서드 |
| `app/config.py` | 대용량 분석 설정 추가 |
| `pyproject.toml` | rapidfuzz 의존성 추가 |

---

## ⏳ 미완료 항목 (다음 단계)

| 항목 | 상태 | 비고 |
|------|------|------|
| LangGraph 파이프라인 연동 | ⏳ | `_run_analysis()` 메서드에 TODO |
| Semantic Chunking 구현 | ⏳ | 현재 단락 기준 임시 분할 |
| 임베딩 생성 | ⏳ | AWS Bedrock Titan 연동 필요 |
| 통합 테스트 | ⏳ | Docker 환경에서 End-to-End 테스트 |

---

## 📞 확인 요청 사항

1. **Callback URL 분기**: `message_type`으로 분기 처리가 완료되었나요?
   - `DOCUMENT_ANALYSIS_RESULT` → `handleDocumentAnalysisCallback()`
   - `GLOBAL_MERGE_RESULT` → `handleGlobalMergeCallback()`

2. **embedding 필드**: Section의 `embedding` 배열을 JSON으로 저장하시나요, 아니면 pgvector로 저장하시나요?

3. **트랜잭션**: 여러 Section 저장 시 하나의 트랜잭션으로 처리하시나요?

---

> 추가 질문이나 수정 요청이 있으면 말씀해 주세요! 🚀
