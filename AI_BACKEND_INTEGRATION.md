# AI Backend 연동 가이드 (for Spring Boot)

> **Last Updated**: 2025-12-28

이 문서는 Spring Boot 서버에서 FastAPI AI Backend로 분석 요청을 전송하는 방법을 설명합니다.

---

## 아키텍처 개요

```
┌─────────────────┐     RabbitMQ      ┌─────────────────┐
│   Spring Boot   │ ───────────────▶  │   FastAPI AI    │
│    (Producer)   │                   │   (Consumer)    │
└─────────────────┘                   └─────────────────┘
         ▲                                    │
         │                                    │
         └──────── HTTP Callback ◀────────────┘
```

1. Spring Boot가 RabbitMQ에 분석 요청 메시지 발행
2. FastAPI가 메시지 수신 후 LangGraph 파이프라인 실행
3. FastAPI가 분석 완료 후 callback_url로 결과 전송

---

## 메시지 스키마

### Queue 정보

| 항목 | 값 |
|------|-----|
| **Queue Name** | `stolink.analysis.queue` |
| **Exchange** | (default) |
| **Durable** | `true` |

### 메시지 형식 (JSON)

```json
{
  "job_id": "job-uuid-generated",
  "project_id": "550e8400-e29b-41d4-a716-446655440000",
  "document_id": "doc-uuid-123",
  "content": "소설 텍스트 내용...",
  "callback_url": "http://localhost:8080/api/internal/ai/analysis/callback",
  "trace_id": "trace-20251228-abc123",
  "context": {
    "chapter_number": 3,
    "total_chapters": 10,
    "previous_chapters": [],
    "existing_characters": [
      {"id": "char-001", "name": "아린", "role": "protagonist"},
      {"id": "char-002", "name": "카엘", "role": "supporting"}
    ],
    "existing_events": [
      {"id": "event-001", "event_type": "action", "summary": "아린과 카엘의 첫 만남", "chapter": 1}
    ],
    "existing_relationships": [
      {"source_name": "아린", "target_name": "카엘", "relation_type": "ALLY", "strength": 7}
    ],
    "existing_settings": [
      {"id": "loc-001", "name": "어두운 숲", "location_type": "forest"}
    ],
    "world_rules_summary": "마법은 왕국에서 금지됨"
  }
}
```

---

## 필드 설명

### 필수 필드

| 필드 | 타입 | 설명 | 예시 |
|------|------|------|------|
| `job_id` | string | 고유 작업 ID (UUID 권장) | `"job-12345"` |
| `project_id` | string | 프로젝트 UUID | `"550e8400-e29b-..."` |
| `document_id` | string | 문서 UUID | `"doc-uuid-123"` |
| `content` | string | 분석할 소설 텍스트 | `"아린은 검을..."` |
| `callback_url` | string | 결과 수신 URL (전체 경로) | `"http://localhost:8080/api/..."` |

### 선택 필드

| 필드 | 타입 | 설명 | 기본값 |
|------|------|------|--------|
| `trace_id` | string | 분산 추적용 ID | 자동 생성 |
| `context` | object | 기존 데이터 참조 | `null` |

### Context 필드 (선택)

| 필드 | 타입 | 설명 |
|------|------|------|
| `chapter_number` | int | 현재 챕터 번호 |
| `total_chapters` | int | 전체 챕터 수 |
| `previous_chapters` | string[] | 이전 챕터 텍스트 (최근 2-3개) |
| `existing_characters` | object[] | 기존 캐릭터 참조 목록 |
| `existing_events` | object[] | 최근 이벤트 참조 목록 |
| `existing_relationships` | object[] | Neo4j 관계 참조 목록 |
| `existing_settings` | object[] | 장소/설정 참조 목록 |
| `world_rules_summary` | string | 세계관 규칙 요약 |

---

## Context 상세 스키마

### ExistingCharacterRef

```json
{
  "id": "char-001",        // PostgreSQL UUID
  "name": "아린",           // 캐릭터 이름 (필수)
  "role": "protagonist"    // protagonist, antagonist, supporting, etc.
}
```

### ExistingEventRef

```json
{
  "id": "event-001",       // PostgreSQL UUID
  "event_type": "action",  // action, dialogue, revelation, etc.
  "summary": "이벤트 요약",
  "chapter": 1
}
```

### ExistingRelationshipRef

```json
{
  "source_name": "아린",      // 관계 시작 캐릭터
  "target_name": "카엘",      // 관계 대상 캐릭터
  "relation_type": "ALLY",   // ALLY, ENEMY, FAMILY, ROMANTIC, MENTOR, RIVAL
  "strength": 7              // 1-10 (관계 강도)
}
```

### ExistingSettingRef

```json
{
  "id": "loc-001",           // PostgreSQL UUID
  "name": "어두운 숲",        // 장소 이름
  "location_type": "forest"  // indoor, outdoor, castle, forest, etc.
}
```

---

## 최소 요청 예시

context 없이 기본 분석만 요청:

```json
{
  "job_id": "job-simple-001",
  "project_id": "550e8400-e29b-41d4-a716-446655440000",
  "document_id": "doc-uuid-123",
  "content": "아린은 어두운 숲에서 검을 꺼내 들었다.",
  "callback_url": "http://localhost:8080/api/internal/ai/analysis/callback"
}
```

---

## Callback 응답 스키마

FastAPI가 callback_url로 POST 요청 (결과):

```json
{
  "jobId": "job-12345",
  "status": "COMPLETED",
  "result": {
    "characters": [...],
    "events": [...],
    "relationships": [...],
    "settings": {...},
    "dialogues": {...},
    "emotions": {...},
    "consistency_report": {...},
    "plot_integration": {...},
    "validation": {...},
    "metadata": {
      "processing_time_ms": 15234,
      "tokens_used": 12500,
      "trace_id": "trace-20251228-abc123",
      "agents_executed": ["character", "event", "setting", ...]
    }
  },
  "error": null
}
```

### Status 값

| Status | 설명 |
|--------|------|
| `COMPLETED` | 분석 성공 |
| `WARNING` | 분석 완료 (일부 경고) |
| `FAILED` | 분석 실패 |

---

## Spring Boot 구현 예시

### 1. DTO 클래스

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisTaskMessage {
    
    @JsonProperty("job_id")
    private String jobId;
    
    @JsonProperty("project_id")
    private String projectId;
    
    @JsonProperty("document_id")
    private String documentId;
    
    private String content;
    
    @JsonProperty("callback_url")
    private String callbackUrl;
    
    @JsonProperty("trace_id")
    private String traceId;
    
    private AnalysisContext context;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisContext {
    
    @JsonProperty("chapter_number")
    private Integer chapterNumber;
    
    @JsonProperty("total_chapters")
    private Integer totalChapters;
    
    @JsonProperty("previous_chapters")
    private List<String> previousChapters;
    
    @JsonProperty("existing_characters")
    private List<ExistingCharacterRef> existingCharacters;
    
    @JsonProperty("existing_events")
    private List<ExistingEventRef> existingEvents;
    
    @JsonProperty("existing_relationships")
    private List<ExistingRelationshipRef> existingRelationships;
    
    @JsonProperty("existing_settings")
    private List<ExistingSettingRef> existingSettings;
    
    @JsonProperty("world_rules_summary")
    private String worldRulesSummary;
}

@Data
@Builder
public class ExistingCharacterRef {
    private String id;
    private String name;
    private String role;
}

@Data
@Builder
public class ExistingRelationshipRef {
    @JsonProperty("source_name")
    private String sourceName;
    
    @JsonProperty("target_name")
    private String targetName;
    
    @JsonProperty("relation_type")
    private String relationType;
    
    private Integer strength;
}
```

### 2. Publisher 서비스

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AIAnalysisPublisher {
    
    private final RabbitTemplate rabbitTemplate;
    private final CharacterRepository characterRepository;
    private final RelationshipService relationshipService;
    
    private static final String QUEUE_NAME = "stolink.analysis.queue";
    
    public String requestAnalysis(Document document, Project project) {
        String jobId = UUID.randomUUID().toString();
        String traceId = generateTraceId();
        
        AnalysisTaskMessage message = AnalysisTaskMessage.builder()
            .jobId(jobId)
            .projectId(project.getId().toString())
            .documentId(document.getId().toString())
            .content(document.getContent())
            .callbackUrl(buildCallbackUrl())
            .traceId(traceId)
            .context(buildContext(project, document))
            .build();
        
        rabbitTemplate.convertAndSend(QUEUE_NAME, message);
        
        log.info("Analysis request sent: jobId={}, traceId={}", jobId, traceId);
        
        return jobId;
    }
    
    private AnalysisContext buildContext(Project project, Document document) {
        return AnalysisContext.builder()
            .chapterNumber(document.getChapterNumber())
            .totalChapters(project.getTotalChapters())
            .existingCharacters(getExistingCharacterRefs(project.getId()))
            .existingRelationships(getExistingRelationshipRefs(project.getId()))
            .build();
    }
    
    private List<ExistingCharacterRef> getExistingCharacterRefs(UUID projectId) {
        return characterRepository.findByProjectId(projectId).stream()
            .map(c -> ExistingCharacterRef.builder()
                .id(c.getId().toString())
                .name(c.getName())
                .role(c.getRole())
                .build())
            .toList();
    }
    
    private String generateTraceId() {
        return String.format("trace-%s-%s", 
            LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
            UUID.randomUUID().toString().substring(0, 8));
    }
    
    private String buildCallbackUrl() {
        return "http://localhost:8080/api/internal/ai/analysis/callback";
    }
}
```

### 3. Callback 수신 Controller

```java
@RestController
@RequestMapping("/api/internal/ai/analysis")
@RequiredArgsConstructor
@Slf4j
public class AICallbackController {
    
    private final AnalysisResultService analysisResultService;
    
    @PostMapping("/callback")
    public ResponseEntity<Void> handleAnalysisCallback(
            @RequestBody AnalysisCallbackPayload payload) {
        
        log.info("Received analysis callback: jobId={}, status={}", 
            payload.getJobId(), payload.getStatus());
        
        if ("COMPLETED".equals(payload.getStatus())) {
            analysisResultService.processResult(payload);
        } else if ("FAILED".equals(payload.getStatus())) {
            analysisResultService.handleFailure(payload);
        }
        
        return ResponseEntity.ok().build();
    }
}

@Data
public class AnalysisCallbackPayload {
    private String jobId;
    private String status;
    private Map<String, Object> result;
    private String error;
}
```

---

## RabbitMQ 설정 (Spring Boot)

### application.yml

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

### RabbitMQ Config

```java
@Configuration
public class RabbitMQConfig {
    
    public static final String ANALYSIS_QUEUE = "stolink.analysis.queue";
    
    @Bean
    public Queue analysisQueue() {
        return QueueBuilder.durable(ANALYSIS_QUEUE).build();
    }
    
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
```

---

## 테스트 방법

### 1. RabbitMQ WebUI에서 직접 테스트

1. http://localhost:15672 접속 (guest/guest)
2. Queues → `stolink.analysis.queue` → Publish message
3. JSON 메시지 입력 후 Publish

### 2. Webhook.site로 결과 확인

1. https://webhook.site 접속
2. 고유 URL 복사
3. `callback_url`을 webhook.site URL로 설정
4. 결과 수신 확인

---

## 주의사항

1. **callback_url은 전체 URL**
   - ✅ `http://localhost:8080/api/internal/ai/analysis/callback`
   - ❌ `/api/internal/ai/analysis/callback`

2. **필드명은 snake_case**
   - Jackson에서 `@JsonProperty` 또는 `SNAKE_CASE` 설정 필요

3. **content는 충분한 텍스트**
   - 최소 100자 이상 권장
   - 짧으면 분석 결과가 빈약할 수 있음

4. **context는 선택사항**
   - 없어도 기본 분석 가능
   - 제공하면 일관성 검사 및 관계 분석 품질 향상
