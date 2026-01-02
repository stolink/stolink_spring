# Spring 팀 질문 답변서 2 - AI Backend 팀

> **작성일**: 2026-01-02  
> **작성자**: AI Backend 팀 (Python/FastAPI)  
> **참조**: SPRING_TEAM_QUESTIONS_2.md

---

## ❓ 질문에 대한 답변

### 1. message_type 값 통일 필요

**답변**: 네, 맞습니다. 요청과 응답의 `message_type`은 의도적으로 다릅니다.

| 방향 | message_type | 설명 |
|------|-------------|------|
| Spring → Python | `DOCUMENT_ANALYSIS` | 분석 **요청** |
| Python → Spring | `DOCUMENT_ANALYSIS_RESULT` | 분석 **결과** |
| Spring → Python | `GLOBAL_MERGE` | 병합 **요청** |
| Python → Spring | `GLOBAL_MERGE_RESULT` | 병합 **결과** |

**분기 처리 방법**:

```java
@PostMapping("/api/ai-callback")
public ResponseEntity<?> handleCallback(@RequestBody String rawPayload) {
    String messageType = extractMessageType(rawPayload);
    
    switch (messageType) {
        case "DOCUMENT_ANALYSIS_RESULT":
            return handleDocumentAnalysisCallback(rawPayload);
        case "GLOBAL_MERGE_RESULT":
            return handleGlobalMergeCallback(rawPayload);
        default:
            // 기존 FULL_DOCUMENT 분석 결과 (message_type 없는 경우)
            return handleLegacyCallback(rawPayload);
    }
}
```

**기존 FULL_DOCUMENT 처리**:
- 기존 `AnalysisCallbackDTO`에는 `message_type` 필드가 없었습니다
- `message_type`이 null이거나 없으면 기존 로직으로 처리하면 됩니다
- 또는 Python에서 기존 방식 콜백 시 `message_type: "FULL_DOCUMENT_RESULT"` 추가 가능 (필요시 요청 주세요)

---

### 2. pgvector 사용 여부

**답변**: 현재 단계에서는 **JSON 문자열 방식 유지**를 권장합니다.

**이유**:
- 의미 검색은 2차 개발 범위 (현재 목표: 대용량 분석 파이프라인 완성)
- pgvector 마이그레이션은 추후 별도 작업으로 진행

**향후 계획**:

| 단계 | 저장 방식 | 검색 기능 |
|------|----------|----------|
| Phase 1 (현재) | `embeddingJson` (TEXT) | ❌ |
| Phase 2 (추후) | `embedding` (vector) | ✅ 의미 검색 가능 |

**Python 측 대응**:
- 현재: `embedding` 필드를 JSON 배열로 전송
- pgvector 도입 시: 동일 형식 유지 (Spring에서 변환 처리)

---

### 3. snake_case 변환 방식 선택

**답변**: **방식 B (DTO별 `@JsonProperty` 명시)를 추천합니다.**

**이유**:
- 기존 API에 영향 없음
- AI 관련 DTO에만 선택적 적용
- 명시적으로 필드 매핑이 보여서 유지보수 용이

**Python 측 필드명 (snake_case)**:

```python
class DocumentAnalysisCallback:
    message_type: str
    document_id: str
    parent_folder_id: Optional[str]
    status: str
    sections: list[SectionOutput]
    characters: list[dict]
    events: list[dict]
    settings: list[dict]
    processing_time_ms: Optional[int]
    trace_id: Optional[str]
```

**Spring DTO 예시**:

```java
public class DocumentAnalysisCallbackDTO {
    @JsonProperty("message_type")
    private String messageType;
    
    @JsonProperty("document_id")
    private String documentId;
    
    @JsonProperty("parent_folder_id")
    private String parentFolderId;
    
    private String status;  // 동일하면 생략 가능
    
    private List<SectionDTO> sections;
    
    @JsonProperty("processing_time_ms")
    private Integer processingTimeMs;
    
    @JsonProperty("trace_id")
    private String traceId;
}
```

---

### 4. 테스트 환경 관련

**답변**:

#### Python 서버 실행 환경

현재 **Docker Compose 환경**을 사용합니다.

```bash
# 실행 방법
docker-compose -f docker-compose.standalone.yml up
```

로컬 개발 시에도 가능합니다:
```bash
cd sto-link-AI-backend
pip install -e .
uvicorn app.main:app --reload --port 8000
```

#### 테스트용 Project/Document ID

**테스트용 데이터 요청**:
- Spring에서 테스트용 Project와 Document(TEXT)를 생성해 주세요
- 또는 기존 데이터의 UUID를 공유해 주세요

예시 형식:
```
Project ID: 550e8400-e29b-41d4-a716-446655440000
Document ID (FOLDER): 550e8400-e29b-41d4-a716-446655440001
Document ID (TEXT): 550e8400-e29b-41d4-a716-446655440002
```

#### RabbitMQ 연결 정보

**Docker Compose 환경 (기본값)**:

| 항목 | 값 |
|------|---|
| Host | `localhost` (또는 `rabbitmq` in Docker network) |
| Port | `5672` |
| Management Port | `15672` |
| Username | `guest` |
| Password | `guest` |
| Virtual Host | `stolink` |

**큐 이름**:

| 큐 | 용도 |
|---|------|
| `stolink.analysis.queue` | 기존 단일 문서 분석 |
| `document_analysis_queue` | 대용량 문서 분석 (신규) |
| `global_merge_queue` | 2차 Pass 글로벌 병합 (신규) |

**Spring application.yml 설정 예시**:

```yaml
app:
  rabbitmq:
    queues:
      analysis: stolink.analysis.queue
      document-analysis: document_analysis_queue
      global-merge: global_merge_queue
    agent:
      host: localhost
      port: 5672
      username: guest
      password: guest
      virtual-host: stolink
```

---

## ✅ 추가 안내

### 연동 테스트 순서

1. **RabbitMQ 연결 확인**
   ```bash
   # 관리 UI 접속
   http://localhost:15672
   # guest / guest
   ```

2. **큐 생성 확인**
   - `document_analysis_queue` 존재 확인
   - `global_merge_queue` 존재 확인

3. **테스트 메시지 발행**
   ```java
   DocumentAnalysisMessage msg = DocumentAnalysisMessage.builder()
       .documentId("test-doc-uuid")
       .projectId("test-proj-uuid")
       .callbackUrl("http://localhost:8080/api/ai-callback")
       .analysisPass(1)
       .build();
   
   agentRabbitTemplate.convertAndSend("document_analysis_queue", msg);
   ```

4. **Python 로그 확인**
   ```bash
   docker-compose logs -f fastapi
   ```

5. **Callback 수신 확인**
   - Spring 로그에서 `/api/ai-callback` 호출 확인

---

## 📞 연락처

테스트 중 문제가 발생하면 언제든 연락 주세요!

---

> 위 작업 진행 후 연동 테스트 시작하시면 됩니다! 🚀
