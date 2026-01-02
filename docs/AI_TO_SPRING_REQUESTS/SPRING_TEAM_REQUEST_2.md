# Spring 팀 요청사항 - Python 구현 완료 후 연동 작업

> **작성일**: 2026-01-02  
> **작성자**: AI Backend 팀 (Python/FastAPI)  
> **목적**: Python 측 구현 완료에 따른 Spring 팀 확인/구현 요청

---

## 📋 요약

Python 측에서 대용량 문서 분석을 위한 Consumer와 스키마 구현을 완료했습니다.  
연동 테스트를 위해 Spring 팀에서 아래 항목들을 확인/구현해 주세요.

---

## ✅ 확인 요청 사항

### 1. Callback 엔드포인트 `message_type` 분기

Python에서 두 가지 타입의 Callback을 전송합니다:

| message_type | 시점 | 처리 메서드 |
|--------------|------|------------|
| `DOCUMENT_ANALYSIS_RESULT` | 1차 Pass 문서 분석 완료 | `handleDocumentAnalysisCallback()` |
| `GLOBAL_MERGE_RESULT` | 2차 Pass 캐릭터 병합 완료 | `handleGlobalMergeCallback()` |

**확인 필요**: `/api/ai-callback` 엔드포인트에서 `message_type` 분기가 완료되었나요?

```java
@PostMapping("/api/ai-callback")
public ResponseEntity<?> handleCallback(@RequestBody Map<String, Object> payload) {
    String messageType = (String) payload.get("message_type");
    
    if ("DOCUMENT_ANALYSIS_RESULT".equals(messageType)) {
        // DocumentAnalysisCallbackDTO로 변환 후 처리
        return handleDocumentAnalysisCallback(payload);
    } else if ("GLOBAL_MERGE_RESULT".equals(messageType)) {
        // GlobalMergeCallbackDTO로 변환 후 처리
        return handleGlobalMergeCallback(payload);
    } else {
        // 기존 FULL_DOCUMENT 처리
        return handleFullDocumentCallback(payload);
    }
}
```

---

### 2. Section 저장 로직

Python이 전송하는 Section 데이터:

```json
{
  "sections": [
    {
      "sequence_order": 1,
      "nav_title": "이안의 각성",
      "content": "눈을 떴을 때...",
      "embedding": [0.123, -0.456, ... (1536개)],
      "related_characters": ["이안", "나비"],
      "related_events": ["E001"]
    }
  ]
}
```

**확인 필요**:
- `embedding` 필드: JSON 문자열로 저장? 또는 pgvector 타입으로 저장?
- 현재 `Section.embeddingJson`이 `String`으로 정의되어 있는데, pgvector 사용 시 마이그레이션 필요

---

### 3. 상태 업데이트 API 응답 형식

Python이 호출하는 API:

```http
PATCH /api/documents/{documentId}/analysis-status
Content-Type: application/json

{
  "status": "PROCESSING",
  "traceId": "trace-123"
}
```

**확인 필요**: 현재 `AnalysisStatusUpdateDTO`의 필드명이 Spring 코드와 일치하나요?

```java
// Spring DTO
public class AnalysisStatusUpdateDTO {
    private AnalysisStatus status;  // enum
    private String traceId;
}
```

Python에서는 `status`를 문자열("PROCESSING")로 전송합니다. Enum 변환이 필요합니다.

---

## 🔧 구현 요청 사항

### 1. [필수] AICallbackController 분기 처리

```java
@RestController
@RequestMapping("/api/ai-callback")
public class AICallbackController {
    
    @PostMapping
    public ResponseEntity<?> handleCallback(@RequestBody String rawPayload) {
        // 1. message_type 추출
        JsonNode root = objectMapper.readTree(rawPayload);
        String messageType = root.path("message_type").asText();
        
        // 2. 타입별 분기 처리
        switch (messageType) {
            case "DOCUMENT_ANALYSIS_RESULT":
                DocumentAnalysisCallbackDTO docCallback = 
                    objectMapper.readValue(rawPayload, DocumentAnalysisCallbackDTO.class);
                return aiCallbackService.handleDocumentAnalysisCallback(docCallback);
                
            case "GLOBAL_MERGE_RESULT":
                GlobalMergeCallbackDTO mergeCallback = 
                    objectMapper.readValue(rawPayload, GlobalMergeCallbackDTO.class);
                return aiCallbackService.handleGlobalMergeCallback(mergeCallback);
                
            default:
                // 기존 AnalysisCallbackDTO 처리
                AnalysisCallbackDTO callback = 
                    objectMapper.readValue(rawPayload, AnalysisCallbackDTO.class);
                return aiCallbackService.handleAnalysisCallback(callback);
        }
    }
}
```

---

### 2. [필수] snake_case ↔ camelCase 변환

Python은 `snake_case`를 사용합니다. Jackson 설정 확인:

```java
@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return mapper;
    }
}
```

또는 DTO에 `@JsonProperty` 명시:

```java
public class DocumentAnalysisCallbackDTO {
    @JsonProperty("message_type")
    private String messageType;
    
    @JsonProperty("document_id")
    private String documentId;
    
    @JsonProperty("processing_time_ms")
    private Integer processingTimeMs;
    // ...
}
```

---

### 3. [선택] pgvector 확장 설치 (의미 검색 사용 시)

```sql
-- PostgreSQL에 pgvector 확장 설치
CREATE EXTENSION IF NOT EXISTS vector;

-- Section 테이블 embedding 컬럼 타입 변경
ALTER TABLE sections 
    ALTER COLUMN embedding TYPE vector(1536) 
    USING embedding::vector;

-- 벡터 인덱스 생성
CREATE INDEX idx_sections_embedding 
    ON sections USING ivfflat (embedding vector_cosine_ops) 
    WITH (lists = 100);
```

---

## 📋 연동 테스트 체크리스트

테스트 전 확인사항:

- [ ] RabbitMQ 큐 생성 확인
  - `document_analysis_queue`
  - `global_merge_queue`
- [ ] Spring → Python 메시지 발행 테스트
- [ ] Python → Spring 상태 업데이트 API 테스트
- [ ] Python → Spring Callback 테스트
- [ ] End-to-End 문서 분석 테스트

---

## 🧪 테스트용 메시지 예시

### Spring → Python (DOCUMENT_ANALYSIS)

```json
{
  "message_type": "DOCUMENT_ANALYSIS",
  "document_id": "550e8400-e29b-41d4-a716-446655440001",
  "project_id": "550e8400-e29b-41d4-a716-446655440000",
  "parent_folder_id": "550e8400-e29b-41d4-a716-446655440002",
  "chapter_title": "제1장",
  "document_order": 1,
  "total_documents_in_chapter": 3,
  "analysis_pass": 1,
  "callback_url": "http://localhost:8080/api/ai-callback",
  "context": {
    "existing_characters": [],
    "existing_events": []
  },
  "trace_id": "test-trace-001"
}
```

### Python → Spring (DOCUMENT_ANALYSIS_RESULT)

```json
{
  "message_type": "DOCUMENT_ANALYSIS_RESULT",
  "document_id": "550e8400-e29b-41d4-a716-446655440001",
  "parent_folder_id": "550e8400-e29b-41d4-a716-446655440002",
  "status": "COMPLETED",
  "sections": [
    {
      "sequence_order": 1,
      "nav_title": "폐허에서의 각성",
      "content": "눈을 떴을 때 가장 먼저 느낀 건...",
      "embedding": null,
      "related_characters": ["이안"],
      "related_events": []
    }
  ],
  "characters": [],
  "events": [],
  "settings": [],
  "processing_time_ms": 2500,
  "trace_id": "test-trace-001"
}
```

---

## 📞 다음 단계 제안

1. **Phase 1**: 위 확인/구현 사항 완료
2. **Phase 2**: 로컬 환경에서 연동 테스트
3. **Phase 3**: Docker Compose 통합 테스트
4. **Phase 4**: 성능 테스트 (365개 문서 Batch 발행)

---

> 질문이 있거나 추가 정보가 필요하면 말씀해 주세요! 🙏
