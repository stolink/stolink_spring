# Spring 팀 질문 답변서 2 - AI Backend 팀

> **작성일**: 2026-01-01  
> **작성자**: AI Backend 팀 (Python/FastAPI)  
> **참조**: SPRING_TEAM_QUESTIONS_2.md

---

## 📋 용어 매핑 확정

| AI 팀 용어 | Spring 시스템 | 설명 |
|-----------|--------------|------|
| `chapter` | `Document(FOLDER)` | 목차 구조 (1장, 2장 등) |
| `chapter.content` | `Document(TEXT).content` | 실제 분석 대상 텍스트 |
| `section` | `Section` (신규) | AI가 생성하는 의미 단위 조각 |

---

## 1. Document 구조 매핑 관련

### A1.1: 분석 요청 단위 확인

**결정: (B) `Document(TEXT).id` - 실제 텍스트가 있는 문서 ID**

**이유**:
- Python은 `content`를 직접 분석해야 함
- FOLDER는 content가 비어있음

**메시지 스키마 수정**:

```json
{
  "message_type": "DOCUMENT_ANALYSIS",
  "document_id": "text-uuid-123",      // Document(TEXT)의 ID
  "parent_folder_id": "folder-uuid-1", // 상위 FOLDER ID (참조용)
  "chapter_title": "제1장",            // 네비게이션용
  "chapter_order": 1,
  "project_id": "proj-001",
  "callback_url": "http://...",
  "context": { ... }
}
```

---

### A1.2: 챕터 내 다중 TEXT 문서 처리

**결정: (A) 각 TEXT 문서마다 별도 메시지 발행**

```
Document(FOLDER) "1장"
  ├── Document(TEXT) "1-1. 도입부" → 메시지 1
  ├── Document(TEXT) "1-2. 전개"   → 메시지 2
  └── Document(TEXT) "1-3. 마무리" → 메시지 3
```

**이유**:
- 각 TEXT 문서가 독립적인 분석 단위
- 병렬 처리 효율 극대화
- Section은 각 TEXT 문서 하위에 생성

**문서 간 맥락 연결**:
```json
{
  "document_id": "text-uuid-2",
  "parent_folder_id": "folder-uuid-1",
  "sibling_order": 2,
  "total_siblings": 3,
  "context": {
    "previous_document_summary": "도입부에서 이안이 각성함",  // 선택적
    "existing_characters": [...]
  }
}
```

---

### A1.3: 상태 필드 추가 필요성

**추천: (B) 별도 `analysis_status` 컬럼 추가**

```java
// Document.java
@Enumerated(EnumType.STRING)
@Column(length = 20)
private DocumentStatus status = DocumentStatus.DRAFT;  // 기존 유지

// 신규 추가
@Enumerated(EnumType.STRING)
@Column(length = 20)
private AnalysisStatus analysisStatus = AnalysisStatus.NONE;

@Column
private Integer analysisRetryCount = 0;

public enum AnalysisStatus {
    NONE,        // 분석 요청 전
    PENDING,     // 분석 대기
    QUEUED,      // RabbitMQ 발행됨
    PROCESSING,  // Python 처리 중
    COMPLETED,   // 분석 완료
    FAILED       // 분석 실패
}
```

**이유**:
- `DocumentStatus`는 작가의 작업 상태 (DRAFT/REVISED/FINAL)
- `AnalysisStatus`는 AI 분석 상태 - 별개 관심사

---

## 2. RabbitMQ 메시지 스키마 관련

### A2.1: `context.existing_*` 필드와 병렬 처리

**결정: (A) 1차 Pass에서는 모든 문서에 빈 배열 `[]` 전송**

**2-Pass 전략**:

| Pass | existing_* | 목적 |
|------|-----------|------|
| 1차 Pass | `[]` (빈 배열) | 독립 분석, 최대 병렬화 |
| 2차 Pass | GlobalMerger | 캐릭터 병합, 관계 연결 |

**메시지에 `analysis_pass` 필드 추가**:

```json
{
  "document_id": "text-uuid",
  "analysis_pass": 1,  // 1차 Pass
  "context": {
    "existing_characters": []  // 병렬 처리 시 비움
  }
}
```

---

### A2.2: Document 계층 조회를 위한 추가 필드

**결정: 선택적 추가 필드 포함**

```json
{
  "message_type": "DOCUMENT_ANALYSIS",
  "document_id": "text-uuid-123",        // 필수: 분석 대상 TEXT ID
  "project_id": "proj-001",              // 필수
  "callback_url": "...",                 // 필수
  
  // 선택적 (네비게이션/맥락용)
  "parent_folder_id": "folder-uuid-1",   // 상위 FOLDER
  "chapter_title": "제1장 - 각성",       // FOLDER의 title
  "document_order": 1,                   // TEXT의 order
  "total_documents_in_chapter": 3,       // 형제 TEXT 수
  
  "context": { ... }
}
```

**Python이 필요한 것**:
- `document_id` → content 조회
- `project_id` → 2차 Pass에서 전체 조회
- 나머지는 결과 구성 시 참조용

---

### A2.3: 콜백 URL 엔드포인트 분리

**결정: 단일 엔드포인트 + 타입 분기 (기존 답변 유지)**

```http
POST /api/ai-callback
Content-Type: application/json

{
  "message_type": "DOCUMENT_ANALYSIS",  // 타입으로 분기
  "document_id": "text-uuid",
  "parent_folder_id": "folder-uuid",
  "status": "COMPLETED",
  "result": {
    "sections": [...],
    "characters": [...],
    "events": [...],
    "settings": [...]
  }
}
```

---

## 3. Python DB 조회 관련

### A3.1: documents 테이블 조회 쿼리

**결정: 옵션 A (TEXT 문서 직접 조회)**

```python
# Python에서 document_id로 직접 조회
content = await db.query(
    "SELECT content FROM documents WHERE id = %s AND type = 'TEXT'",
    msg.document_id
)
```

**이유**:
- 메시지에 이미 `document_id` (TEXT)가 포함됨
- 추가 조인/조회 불필요
- 단순하고 빠름

**하위 TEXT 병합이 필요한 경우**:
- Spring이 발행 시점에 미리 병합하거나
- 각 TEXT마다 별도 메시지 발행 (추천)

---

### A3.2: 읽기 전용 접근 범위

**결정: Section은 Callback으로 전달 (Python 쓰기 권한 없음)**

| 테이블 | Python 읽기 | Python 쓰기 | 비고 |
|--------|------------|------------|------|
| `documents` | ✅ | ❌ | content 조회 |
| `characters` | ✅ | ❌ | existing_characters 조회 |
| `events` | ✅ | ❌ | existing_events 조회 |
| `settings` | ✅ | ❌ | existing_settings 조회 |
| `section` | ❌ | ❌ | **Callback으로 전달** |

**Section 저장 흐름**:
```
Python: Semantic Chunking → Section 목록 생성
    ↓
Callback: { "sections": [...] }
    ↓
Spring: Section 테이블에 저장
```

**이유**:
- Python은 Stateless 유지
- 트랜잭션은 Spring에서 관리
- 단일 책임 원칙

---

## 4. 콜백 및 상태 관리 관련

### A4.1: Document 상태 업데이트 방식

**결정: Document(TEXT) ID 기준 + 옵션 B (Callback 포함)**

```json
{
  "message_type": "DOCUMENT_ANALYSIS",
  "document_id": "text-uuid",           // TEXT 문서 ID
  "parent_folder_id": "folder-uuid",    // 참조용
  "status": "COMPLETED",
  "result": { ... }
}
```

**상태 전환 시점**:

| 상태 | 전환 시점 | 담당 |
|------|----------|------|
| `PENDING` → `QUEUED` | RabbitMQ 발행 후 | Spring |
| `QUEUED` → `PROCESSING` | 메시지 수신 즉시 | Python (별도 API 호출) |
| `PROCESSING` → `COMPLETED/FAILED` | Callback | Python |

**Python PROCESSING 상태 업데이트**:
```python
async def process_document(msg):
    # 1. PROCESSING 상태로 변경
    await http_client.patch(
        f"{SPRING_URL}/api/documents/{msg.document_id}/analysis-status",
        json={"status": "PROCESSING"}
    )
    
    # 2. 분석 수행...
    
    # 3. 완료 Callback
    await send_callback({ "status": "COMPLETED", ... })
```

---

### A4.2: 실패 및 재시도 로직

**결정**:
- `retry_count`: `documents` 테이블에 컬럼 추가 (옵션 A)
- 재발행 트리거: **Spring Scheduler**

```java
// Document.java
@Column
private Integer analysisRetryCount = 0;

// AnalysisRetryScheduler.java
@Scheduled(fixedDelay = 60000)
public void retryFailedDocuments() {
    List<Document> failed = documentRepository.findByAnalysisStatusAndRetryCountLessThan(
        AnalysisStatus.FAILED, MAX_RETRY
    );
    
    for (Document doc : failed) {
        doc.setAnalysisStatus(AnalysisStatus.QUEUED);
        doc.setAnalysisRetryCount(doc.getAnalysisRetryCount() + 1);
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, buildMessage(doc));
        documentRepository.save(doc);
    }
}
```

---

## 5. 2-Pass 하이브리드 처리 관련

### A5.1: 1차 Pass 완료 감지

**결정: (B) Callback 수신 시 카운터 체크**

```java
@PostMapping("/api/ai-callback")
public ResponseEntity<?> handleCallback(@RequestBody AnalysisCallbackDTO dto) {
    // 1. 결과 저장
    saveDocumentResult(dto);
    
    // 2. 완료 체크
    Project project = getProjectByDocumentId(dto.getDocumentId());
    long totalText = documentRepository.countByProjectIdAndType(
        project.getId(), DocumentType.TEXT
    );
    long completed = documentRepository.countByProjectIdAndAnalysisStatus(
        project.getId(), AnalysisStatus.COMPLETED
    );
    
    if (completed == totalText) {
        // 3. 2차 Pass 트리거
        rabbitTemplate.convertAndSend(
            "global_merge_queue",
            new GlobalMergeMessage(project.getId())
        );
        project.setMergeStatus("PENDING");
    }
}
```

---

### A5.2: Entity Resolution 결과 반영

**결정: `aliases` 배열 + 중복 정리**

**Callback 스키마**:
```json
{
  "message_type": "GLOBAL_MERGE_RESULT",
  "project_id": "proj-001",
  "character_merges": [
    {
      "primary_id": "char-이안-001",
      "merged_ids": ["char-ian-002", "char-Ian-003"],
      "canonical_name": "이안",
      "merged_aliases": ["Ian", "ian"],
      "confidence": 0.95
    }
  ]
}
```

**Spring 처리**:
```java
public void applyCharacterMerge(CharacterMergeDTO merge) {
    Character primary = characterRepository.findById(merge.getPrimaryId());
    
    // 1. aliases 통합
    Set<String> allAliases = new HashSet<>(primary.getAliases());
    allAliases.addAll(merge.getMergedAliases());
    primary.setAliases(new ArrayList<>(allAliases));
    
    // 2. 중복 캐릭터의 관계 이전
    for (String oldId : merge.getMergedIds()) {
        relationshipRepository.updateSourceCharacter(oldId, merge.getPrimaryId());
        relationshipRepository.updateTargetCharacter(oldId, merge.getPrimaryId());
        eventRepository.updateParticipant(oldId, merge.getPrimaryId());
    }
    
    // 3. 중복 캐릭터 삭제 (또는 soft delete)
    characterRepository.deleteAllById(merge.getMergedIds());
}
```

---

## 6. Section 테이블 설계 관련

### A6.1: Section 테이블 FK 관계

**결정: `Document(TEXT)`에 직접 연결**

```sql
CREATE TABLE section (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    
    sequence_order INT NOT NULL,
    nav_title VARCHAR(200),
    content TEXT NOT NULL,
    
    -- 임베딩 (pgvector)
    embedding vector(1536),
    
    -- 메타데이터
    related_characters TEXT[],
    related_events TEXT[],
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT unique_section_order UNIQUE (document_id, sequence_order)
);

CREATE INDEX idx_section_document ON section(document_id);
CREATE INDEX idx_section_embedding ON section 
USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

**관계**:
```
Document(TEXT) 1 : N Section
```

---

### A6.2: Section 생성 주체

**결정: (A) Python이 Callback으로 전달 → Spring이 저장**

**흐름**:
```
1. Python: Document(TEXT).content를 Semantic Chunking
2. Python: Section 목록 + 임베딩 생성
3. Python: Callback으로 전달
4. Spring: Section 테이블에 저장
```

**Callback Payload**:
```json
{
  "document_id": "text-uuid",
  "sections": [
    {
      "sequence_order": 1,
      "nav_title": "이안의 각성",
      "content": "눈을 떴을 때...",
      "embedding": [0.123, -0.456, ...],
      "related_characters": ["이안", "나비"],
      "related_events": ["E001"]
    }
  ]
}
```

---

### A6.3: pgvector 통합

**결정**:
- 임베딩 생성: **Python** (OpenAI/AWS Bedrock)
- 임베딩 저장: **Spring** (Callback 수신 후)

**Spring PostgreSQL 요구사항**:
```sql
-- pgvector 확장 설치 필요
CREATE EXTENSION IF NOT EXISTS vector;
```

**Python에서 임베딩 생성**:
```python
from openai import OpenAI

def generate_embedding(text: str) -> list[float]:
    client = OpenAI()
    response = client.embeddings.create(
        model="text-embedding-ada-002",
        input=text
    )
    return response.data[0].embedding  # 1536차원
```

---

## 📎 결정 사항 요약

| 항목 | 결정 |
|------|------|
| 분석 요청 ID | `Document(TEXT).id` |
| 다중 TEXT 처리 | 각 TEXT마다 별도 메시지 |
| 상태 필드 | `analysis_status` 컬럼 추가 |
| existing_* 채우기 | 1차 Pass는 빈 배열 |
| Python DB 조회 | TEXT 직접 조회 |
| Section 쓰기 | Callback 전달 (Python 쓰기 권한 없음) |
| 상태 업데이트 기준 | Document(TEXT) ID |
| 1차 Pass 완료 감지 | Callback 시 카운터 체크 |
| Entity Resolution | aliases 통합 + 중복 삭제 |
| Section FK | Document(TEXT) 참조 |
| 임베딩 생성 | Python |

---

> 추가 질문이 있으면 말씀해 주세요! 🙏
