# Spring Boot ↔ FastAPI AI Backend 통합 가이드

> **Last Updated**: 2026-01-01  
> **대상**: Spring Boot Backend Team  
> **목적**: FastAPI AI 서버와의 RabbitMQ 기반 통합 가이드

---

## 📋 목차

1. [시스템 아키텍처](#1-시스템-아키텍처)
2. [메시지 발행 (Spring → AI)](#2-메시지-발행-spring--ai)
3. [Job 상태 업데이트 API](#3-job-상태-업데이트-api)
4. [콜백 수신 (AI → Spring)](#4-콜백-수신-ai--spring)
5. [응답 스키마 상세](#5-응답-스키마-상세)
6. [Neo4j 저장 가이드](#6-neo4j-저장-가이드)
7. [Spring Boot 구현 체크리스트](#7-spring-boot-구현-체크리스트)
8. [트러블슈팅](#8-트러블슈팅)

---

## 1. 시스템 아키텍처

### 1.1 전체 플로우

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SPRING BOOT                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│  1. 사용자가 소설 텍스트 입력                                                 │
│  2. AnalysisTaskMessage 생성 (job_id, project_id, content, context)         │
│  3. RabbitMQ에 메시지 발행 (stolink.analysis.queue)                          │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       FASTAPI AI BACKEND                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│  [EXTRACTION PHASE - 병렬 처리]                                              │
│  ├─ Character Agent → 캐릭터 추출 (profile, appearance, personality, etc.)  │
│  ├─ Setting Agent → 배경/장소 추출 (visual_background, atmosphere)          │
│  └─ Event Agent → 이벤트 추출 (participants, location_ref)                  │
│                                                                             │
│  [ANALYSIS PHASE - 순차 처리]                                                │
│  ├─ Relationship Agent → 캐릭터 관계 분석                                    │
│  ├─ Consistency Agent → 일관성 검사 (conflicts, warnings)                   │
│  ├─ Plot Agent → 줄거리 요약 (narrative, central_conflict)                  │
│  └─ Validator Agent → 최종 검증 (quality_score, action)                     │
│                                                                             │
│  [CALLBACK] HTTP POST → callback_url                                        │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       SPRING BOOT (Callback 수신)                           │
├─────────────────────────────────────────────────────────────────────────────┤
│  4. Callback 수신 → 결과 파싱                                               │
│  5. PostgreSQL에 캐릭터/이벤트/세팅 저장                                     │
│  6. Neo4j에 관계 그래프 저장                                                 │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 RabbitMQ 설정

| 항목 | 값 |
|------|-----|
| **Exchange** | `stolink.exchange` (direct) |
| **Queue** | `stolink.analysis.queue` |
| **Routing Key** | `analysis` |
| **VHost** | `stolink` |

---

## 2. 메시지 발행 (Spring → AI)

### 2.1 메시지 스키마

```json
{
  "job_id": "string (required)",
  "project_id": "string (required)", 
  "document_id": "string (required)",
  "content": "string (required)",
  "context": {
    "chapter_number": "integer (optional)",
    "total_chapters": "integer (optional)",
    "previous_chapters": ["string (optional)"],
    "existing_characters": [
      {
        "id": "string",
        "name": "string",
        "role": "string"
      }
    ],
    "existing_events": [
      {
        "id": "string",
        "event_type": "string",
        "summary": "string",
        "chapter": "integer"
      }
    ],
    "existing_relationships": [
      {
        "source_name": "string",
        "target_name": "string",
        "relation_type": "string",
        "strength": "integer (1-10)"
      }
    ],
    "existing_settings": [
      {
        "id": "string",
        "name": "string",
        "location_type": "string"
      }
    ],
    "world_rules_summary": "string (optional)"
  },
  "callback_url": "string (required)",
  "trace_id": "string (optional)"
}
```

### 2.2 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `job_id` | string | ✅ | 고유 작업 식별자 (UUID 권장) |
| `project_id` | string | ✅ | 프로젝트 UUID |
| `document_id` | string | ✅ | 분석할 문서 UUID |
| `content` | string | ✅ | **분석할 스토리 텍스트** |
| `context` | object | ❌ | 기존 데이터 컨텍스트 |
| `context.chapter_number` | integer | ❌ | 현재 챕터 번호 |
| `context.previous_chapters` | string[] | ❌ | 이전 챕터 텍스트 (연속성 분석용) |
| `context.existing_characters` | array | ❌ | 기존 캐릭터 참조 목록 |
| `context.existing_events` | array | ❌ | 기존 이벤트 참조 |
| `context.existing_relationships` | array | ❌ | 기존 관계 참조 |
| `context.existing_settings` | array | ❌ | 기존 배경 참조 |
| `context.world_rules_summary` | string | ❌ | 세계관 규칙 요약 |
| `callback_url` | string | ✅ | 결과 수신 URL |
| `trace_id` | string | ❌ | 분산 추적용 ID (없으면 자동 생성) |

### 2.3 예시 입력

```json
{
  "job_id": "analysis-20260101-001",
  "project_id": "550e8400-e29b-41d4-a716-446655440000",
  "document_id": "chapter-03",
  "content": "아린은 검을 받아들었다. 카엘이 그녀를 바라보며 말했다...",
  "context": {
    "chapter_number": 3,
    "total_chapters": 10,
    "existing_characters": [
      {"id": "char-001", "name": "아린", "role": "protagonist"},
      {"id": "char-002", "name": "카엘", "role": "supporting"}
    ],
    "existing_relationships": [
      {"source_name": "아린", "target_name": "카엘", "relation_type": "Friendly", "strength": 7}
    ],
    "world_rules_summary": "마법은 왕국에서 금지됨"
  },
  "callback_url": "http://spring-server:8080/api/internal/ai/analysis/callback",
  "trace_id": "trace-20260101-001"
}
```

### 2.4 Spring Boot 메시지 발행 코드

```java
@Service
public class AIAnalysisService {
    
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    
    public void requestAnalysis(String projectId, String documentId, String content) {
        Map<String, Object> message = new HashMap<>();
        message.put("job_id", UUID.randomUUID().toString());
        message.put("project_id", projectId);
        message.put("document_id", documentId);
        message.put("content", content);
        message.put("context", buildContext(projectId));
        message.put("callback_url", "https://your-server.com/api/internal/ai/analysis/callback");
        message.put("trace_id", "trace-" + System.currentTimeMillis());
        
        rabbitTemplate.convertAndSend(
            "stolink.exchange", 
            "analysis", 
            message
        );
    }
    
    private Map<String, Object> buildContext(String projectId) {
        Map<String, Object> context = new HashMap<>();
        context.put("chapter_number", getCurrentChapter(projectId));
        context.put("existing_characters", getExistingCharacters(projectId));
        context.put("existing_relationships", getExistingRelationships(projectId));
        return context;
    }
}
```

---

## 3. Job 상태 업데이트 API

FastAPI AI Backend는 분석 진행 중 실시간 상태를 Spring Boot에 전송합니다.  
**Spring Boot에서 이 API를 구현해야 합니다.**

### 3.1 Job 상태 흐름

```
PENDING → PROCESSING → ANALYZING → VALIDATING → COMPLETED
                                              ↘ FAILED
```

| 상태 | 설명 | 설정 주체 |
|------|------|------------|
| `PENDING` | 작업 대기 중 | Spring Boot (Job 생성 시) |
| `PROCESSING` | RabbitMQ로 전송됨 | Spring Boot (메시지 전송 후) |
| `ANALYZING` | AI 분석 중 | FastAPI (Agent 실행 시작) |
| `VALIDATING` | 검증 중 | FastAPI (Validator Agent 시작) |
| `COMPLETED` | 완료 | FastAPI (Callback 전송 시) |
| `FAILED` | 실패 | FastAPI (에러 발생 시) |

### 3.2 API 스펙

#### Endpoint

```
POST /api/internal/ai/jobs/{jobId}/status
```

#### Request Body

```json
{
  "status": "ANALYZING",
  "message": "Character Agent 실행 중"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `status` | string | ✅ | 새 상태 (ANALYZING, VALIDATING, FAILED 등) |
| `message` | string | ❌ | 상태 메시지 (FAILED 시 에러 메시지) |

#### Response (Success)

```json
{
  "status": "OK",
  "message": "OK",
  "code": 200
}
```

#### Response (Invalid Status)

```json
{
  "status": "BAD_REQUEST",
  "message": "Invalid status: INVALID_STATUS",
  "code": 400
}
```

### 3.3 Spring Boot 구현 코드

```java
@RestController
@RequestMapping("/api/internal/ai")
public class AIJobStatusController {
    
    private final JobService jobService;
    
    @PostMapping("/jobs/{jobId}/status")
    public ResponseEntity<ApiResponse> updateJobStatus(
            @PathVariable String jobId,
            @RequestBody JobStatusUpdateRequest request) {
        
        // 유효한 상태 값인지 확인
        List<String> validStatuses = List.of(
            "PENDING", "PROCESSING", "ANALYZING", 
            "VALIDATING", "COMPLETED", "FAILED"
        );
        
        if (!validStatuses.contains(request.getStatus())) {
            return ResponseEntity.badRequest().body(
                new ApiResponse("BAD_REQUEST", 
                    "Invalid status: " + request.getStatus(), 400)
            );
        }
        
        // Job 상태 업데이트
        jobService.updateStatus(jobId, request.getStatus(), request.getMessage());
        
        // WebSocket으로 프론트엔드에 알림 (선택사항)
        // webSocketService.notifyStatusChange(jobId, request.getStatus());
        
        return ResponseEntity.ok(new ApiResponse("OK", "OK", 200));
    }
}

// DTO
@Data
public class JobStatusUpdateRequest {
    private String status;
    private String message;
}
```

### 3.4 유효한 상태 값

```java
public enum JobStatus {
    PENDING,
    PROCESSING,
    ANALYZING,
    VALIDATING,
    COMPLETED,
    FAILED
}
```

---

## 4. 콜백 수신 (AI → Spring)

### 4.1 콜백 Payload 구조

AI Backend는 분석 완료 후 `callback_url`로 HTTP POST 요청을 보냅니다.

> [!IMPORTANT]  
> 필드명은 **snake_case**를 사용하며, `results`는 복수형입니다.

```json
{
  "job_id": "test-story-cyberpunk-01",
  "status": "completed",
  "results": {
    "characters": [...],
    "events": [...],
    "settings": [...],
    "relationships": [...],
    "plot": {...},
    "consistency_report": {...},
    "validation": {...},
    "metadata": {...}
  },
  "error": null
}
```

### 4.2 Status 값

| Status | 설명 |
|--------|------|
| `completed` | 분석 성공, 모든 데이터 정상 |
| `warning` | 분석 완료, 일부 경고 존재 (results는 유효) |
| `failed` | 분석 실패, error 필드에 오류 메시지 |

> [!NOTE]
> status 값은 **소문자**입니다.

### 4.3 Spring Boot 콜백 수신 코드

```java
@RestController
@RequestMapping("/api/internal/ai")
public class AICallbackController {
    
    private final CharacterService characterService;
    private final EventService eventService;
    private final RelationshipService relationshipService;
    
    @PostMapping("/analysis/callback")
    public ResponseEntity<String> handleAICallback(@RequestBody AICallbackPayload payload) {
        log.info("Received AI callback for job: {}, status: {}", 
                 payload.getJobId(), payload.getStatus());
        
        // status는 소문자로 전달됨
        if ("failed".equals(payload.getStatus())) {
            log.error("AI analysis failed: {}", payload.getError());
            return ResponseEntity.ok("Error acknowledged");
        }
        
        // results 복수형 사용
        AIResults results = payload.getResults();
        
        // 1. 캐릭터 저장 (_id 필드 포함, embedding 제외 권장)
        if (results.getCharacters() != null) {
            for (Map<String, Object> character : results.getCharacters()) {
                characterService.saveOrUpdate(character);
            }
        }
        
        // 2. 이벤트 저장 (embedding 제외 권장)
        if (results.getEvents() != null) {
            for (Map<String, Object> event : results.getEvents()) {
                eventService.save(event);
            }
        }
        
        // 3. 배경 저장 (embedding 제외 권장)
        if (results.getSettings() != null) {
            for (Map<String, Object> setting : results.getSettings()) {
                settingService.save(setting);
            }
        }
        
        // 4. 관계 저장 (Neo4j) - characters.relations.graph에서 추출
        if (results.getRelationships() != null) {
            for (Map<String, Object> relationship : results.getRelationships()) {
                relationshipService.saveToNeo4j(relationship);
            }
        }
        
        return ResponseEntity.ok("Processed");
    }
}

// DTO 클래스
@Data
public class AICallbackPayload {
    @JsonProperty("job_id")
    private String jobId;
    
    private String status;
    private AIResults results;  // 복수형
    private String error;
}
```

---

## 5. 응답 스키마 상세

### 5.1 Characters (FullCharacter 구조)

> [!NOTE]
> `embedding` 필드는 1024차원의 float 배열입니다. 저장 시 용량이 클 수 있으므로 별도 테이블/컬렉션 저장을 권장합니다.

```json
{
  "_id": "char-세라-001",
  "role": "protagonist",
  
  "profile": {
    "character_id": "char-세라-001",
    "name": "세라",
    "age": 24,
    "gender": "female",
    "race": "android",
    "mbti": null,
    "personality": ["자아 발견 의지", "취약성", "감수성", "용기"],
    "backstory": "원래 한그룹 회장의 딸 한채린이었으나...",
    "faction": {
      "name": "없음",
      "social": {
        "rank": "COMMON",
        "influence": 0,
        "faction_reputation": {}
      }
    }
  },
  
  "aliases": ["한채린"],
  "status": "alive",
  
  "appearance": {
    "physique": "완벽한 인체 모델",
    "skin_tone": "너무 완벽한 피부",
    "eyes": "unspecified",
    "nose": "unspecified",
    "mouth": "unspecified",
    "hair_style": "unspecified",
    "hair_color": "unspecified",
    "attire": ["안드로이드 기본 의상"],
    "expression": "두려움과 혼란",
    "scars_tattoos": [],
    "style_context": {
      "art_style": "cyberpunk noir"
    }
  },
  
  "personality": {
    "core_traits": ["자아 발견 의지", "취약성", "감수성", "용기"],
    "flaws": ["정체성 혼란", "취약한 감정", "불완전한 기억"],
    "values": ["진실", "자아 정체성", "존엄성"]
  },
  
  "relations": {
    "graph": [
      {
        "target": "진하",
        "type": "ALLY",
        "strength": 7,
        "description": "자신의 과거를 찾아주는 탐정과의 동맹",
        "public_stance": "ALLY",
        "private_feeling": "TRUST"
      },
      {
        "target": "유민재",
        "type": "ENEMY",
        "strength": 9,
        "description": "자신을 추적하고 포획하려는 위험한 적",
        "public_stance": "ENEMY",
        "private_feeling": "FEAR"
      }
    ],
    "event_refs": [],
    "location_context": "Unknown"
  },
  
  "current_mood": {
    "emotion": "공포",
    "intensity": 9,
    "trigger": "유민재의 등장과 자신의 진정한 정체 발각"
  },
  
  "inventory": {
    "equipped_items": [
      {
        "item_id": null,
        "name": "메모리 칩",
        "quantity": 1,
        "rarity": "RARE",
        "estimated_value": 50,
        "equipped": false,
        "slot": "QUICK_SLOT",
        "description": "암호화된 개인 메모리 칩"
      }
    ],
    "bag_items": [],
    "quest_items": ["메모리 칩"]
  },
  
  "meta": {
    "created_at": null,
    "updated_at": null,
    "data_version": "2.0.0",
    "lock_version": 0
  },
  
  "embedding": [0.27734375, -0.38671875, ...]  // 1024차원 float 배열
}
```

### 5.2 Events

```json
{
  "event_id": "E001",
  "event_type": "action",
  "narrative_summary": "진하, 산성비 내리는 하층 거주구에 도착",
  "description": "진하가 낡은 트렌치코트 깃을 세우고 뒷골목 입구에 서 있다...",
  "participants": ["진하", "ARIA"],
  "location_ref": "하층 거주구",
  "prev_event_id": null,
  "timestamp": {
    "relative": null,
    "absolute": "2087년",
    "chapter": null,
    "sequence_order": 1
  },
  "importance": 7,
  "changes_made": null,
  "embedding": [-0.1103515625, -0.3984375, ...]  // 1024차원 float 배열
}
```

### 5.3 Settings

```json
{
  "setting_id": "loc_dark_forest_01",
  "name": "어둠의 숲",
  "location_name": "어둠의 숲",
  "location_type": "forest",
  "parent_location": null,
  "visual_background": "Dense ancient forest with tall twisted trees, thick fog covering the ground, moonlight filtering through leaves",
  "atmosphere": "ominous, tense",
  "time_of_day": "night",
  "lighting": "dim moonlight",
  "weather": "foggy",
  "art_style": "Dark Fantasy, Realistic, Cinematic Lighting",
  "description": "마법이 금지된 왕국의 경계에 위치한 고대 숲",
  "notable_features": ["고대 폐허", "마법 봉인석"],
  "significance": "마족과의 첫 교전지",
  "is_primary": true
}
```

### 5.4 Relationships

> [!NOTE]
> 관계 정보는 `results.characters[].relations.graph` 배열에 포함됩니다.  
> 별도의 `relationships` 필드가 아닌 각 캐릭터 내 `relations.graph`에서 추출하세요.

```json
{
  "target": "진하",
  "type": "Friendly",
  "strength": 7,
  "description": "자신의 과거를 찾아주는 탐정과의 동맹",
  "public_stance": "ALLY",
  "private_feeling": "TRUST"
}
```

**relation_type 값 (4가지 Enum)**:

| 값 | 설명 |
|---|------|
| `Romance` | 연인/사랑 관계 |
| `Friendly` | 우호적/동맹 관계 |
| `Hostile` | 적대적 관계 |
| `Normal` | 일반적/중립 관계 |

> [!IMPORTANT]
> `type` 필드는 반드시 위 4가지 값 중 하나여야 합니다.

**private_feeling 값**:
- `TRUST`, `FEAR`, `HATE`, `ANGER`, `GUILT`, `CURIOSITY`, `DISTRUST` 등

### 5.5 Plot

```json
{
  "summary": {
    "narrative": "아린과 카엘이 마족의 침입을 막기 위해 첫 전투를 치른다...",
    "central_conflict": "왕국과 마족 간의 전쟁"
  }
}
```

### 5.6 Consistency Report

```json
{
  "overall_score": 95,
  "requires_reextraction": false,
  "conflicts": [
    {
      "type": "PERSONALITY_CONFLICT",
      "severity": "LOW",
      "source": "extracted",
      "existing": "차분함",
      "new": "충동적",
      "character": "아린",
      "description": "이전 챕터와 성격 묘사 불일치",
      "suggested_action": "FLAG_FOR_HUMAN"
    }
  ],
  "warnings": ["캐릭터 '카엘'의 나이 정보 누락"],
  "resolution_summary": {
    "auto_fixable": 0,
    "ready_for_update": 1,
    "needs_human_review": 1,
    "total_conflicts": 1
  },
  "neo4j_validation": {
    "is_valid": true,
    "conflict_count": 0,
    "high_severity_count": 0
  }
}
```

**ConflictType 값**:
- `PERSONALITY_CONFLICT` - 성격 불일치
- `RELATIONSHIP_CONFLICT` - 관계 불일치
- `TIMELINE_CONFLICT` - 시간선 불일치
- `STATUS_CONFLICT` - 상태 불일치 (alive/deceased)
- `PHYSICAL_CONFLICT` - 외형 불일치
- `SETTING_CONFLICT` - 배경 설정 불일치
- `CHARACTER_TRAIT_CONFLICT` - 캐릭터 특성 불일치
- `INVENTORY_CONFLICT` - 인벤토리 불일치
- `STATS_CONFLICT` - 스탯 불일치
- `CROSS_CHAPTER_CONFLICT` - 챕터간 불일치

**Severity 값**: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`

**SuggestedAction 값**: `AUTO_FIX`, `FLAG_FOR_HUMAN`, `IGNORE`, `REEXTRACT`

### 5.7 Validation

```json
{
  "is_valid": true,
  "quality_score": 92,
  "completeness_score": 88,
  "action": "approve",
  "action_description": "Ready for callback to Spring Boot",
  "issues": []
}
```

### 5.8 Metadata

```json
{
  "processing_time_ms": 45000,
  "tokens_used": 12500,
  "trace_id": "trace-20260101-001",
  "agents_executed": [
    "character", "event", "setting", 
    "relationship", "consistency", "plot", "validator"
  ]
}
```

---

## 6. Neo4j 저장 가이드

### 6.1 Character 노드 저장

```java
public void saveCharacterToNeo4j(Map<String, Object> character) {
    String cypher = """
        MERGE (c:Character {character_id: $id, project_id: $projectId})
        SET c.name = $name,
            c.role = $role,
            c.status = $status,
            c.aliases = $aliases
    """;
    
    Map<String, Object> profile = (Map<String, Object>) character.get("profile");
    
    neo4jTemplate.query(cypher, Map.of(
        "id", profile.get("character_id"),
        "projectId", projectId,
        "name", profile.get("name"),
        "role", character.get("role"),
        "status", character.get("status"),
        "aliases", character.getOrDefault("aliases", List.of())
    ));
}
```

### 6.2 Relationship 엣지 저장

```java
public void saveRelationshipToNeo4j(Map<String, Object> relationship) {
    String cypher = """
        MATCH (a:Character {name: $source, project_id: $projectId})
        MATCH (b:Character {name: $target, project_id: $projectId})
        MERGE (a)-[r:RELATED_TO]->(b)
        SET r.relation_type = $relationType,
            r.strength = $strength,
            r.description = $description,
            r.bidirectional = $bidirectional
    """;
    
    neo4jTemplate.query(cypher, Map.of(
        "projectId", projectId,
        "source", relationship.get("source"),
        "target", relationship.get("target"),
        "relationType", relationship.get("relation_type"),
        "strength", relationship.getOrDefault("strength", 5),
        "description", relationship.getOrDefault("description", ""),
        "bidirectional", relationship.getOrDefault("bidirectional", true)
    ));
}
```

### 6.3 Event 노드 및 연결

```java
public void saveEventToNeo4j(Map<String, Object> event) {
    // 1. Event 노드 생성
    String createEvent = """
        MERGE (e:Event {event_id: $eventId, project_id: $projectId})
        SET e.narrative_summary = $summary,
            e.event_type = $eventType,
            e.importance = $importance
    """;
    
    neo4jTemplate.query(createEvent, Map.of(
        "eventId", event.get("event_id"),
        "projectId", projectId,
        "summary", event.get("narrative_summary"),
        "eventType", event.get("event_type"),
        "importance", event.getOrDefault("importance", 5)
    ));
    
    // 2. 참여 캐릭터 연결
    List<String> participants = (List<String>) event.getOrDefault("participants", List.of());
    for (String participant : participants) {
        String linkCharacter = """
            MATCH (c:Character {name: $name, project_id: $projectId})
            MATCH (e:Event {event_id: $eventId, project_id: $projectId})
            MERGE (c)-[:PARTICIPATES_IN]->(e)
        """;
        neo4jTemplate.query(linkCharacter, Map.of(
            "name", participant,
            "eventId", event.get("event_id"),
            "projectId", projectId
        ));
    }
    
    // 3. 이전 이벤트 연결 (타임라인)
    String prevEventId = (String) event.get("prev_event_id");
    if (prevEventId != null) {
        String linkPrevEvent = """
            MATCH (prev:Event {event_id: $prevId, project_id: $projectId})
            MATCH (curr:Event {event_id: $currId, project_id: $projectId})
            MERGE (prev)-[:NEXT]->(curr)
        """;
        neo4jTemplate.query(linkPrevEvent, Map.of(
            "prevId", prevEventId,
            "currId", event.get("event_id"),
            "projectId", projectId
        ));
    }
}
```

---

## 7. Spring Boot 구현 체크리스트

### 필수 구현

- [ ] **RabbitMQ 메시지 발행**
  - [ ] `stolink.exchange`에 `analysis` 라우팅 키로 발행
  - [ ] 메시지 스키마 준수 (job_id, project_id, document_id, content, callback_url)

- [ ] **Job 상태 업데이트 API**
  - [ ] `POST /api/internal/ai/jobs/{jobId}/status` 구현
  - [ ] 유효한 상태 값 검증 (ANALYZING, VALIDATING, FAILED 등)
  - [ ] Job 상태 DB 업데이트 로직

- [ ] **Callback 수신 API**
  - [ ] `POST /api/internal/ai/analysis/callback` 구현
  - [ ] status: COMPLETED/WARNING/FAILED 처리
  - [ ] result 파싱 및 저장 로직

- [ ] **데이터 저장**
  - [ ] PostgreSQL: characters, events, settings 저장
  - [ ] Neo4j: relationships, character nodes, event nodes 저장

### 선택 구현

- [ ] **WebSocket 알림** (프론트엔드에 분석 상태/완료 알림)
- [ ] **재시도 로직** (콜백 수신 실패 시)

---

## 8. 트러블슈팅

### 8.1 Callback URL 연결 실패

```
❌ 오류: Connection refused to http://localhost:8080/...
```

**원인**: Docker 컨테이너에서 `localhost`는 컨테이너 자체를 가리킴

**해결**: 
```json
// Docker Compose 사용 시
"callback_url": "http://host.docker.internal:8080/api/internal/ai/analysis/callback"

// 서비스명 사용 시
"callback_url": "http://spring-backend:8080/api/internal/ai/analysis/callback"
```

### 8.2 RabbitMQ 연결 실패

```
❌ 오류: Cannot connect to RabbitMQ
```

**확인사항**:
1. RabbitMQ 실행 중인지 확인
2. VHost `stolink` 존재 확인
3. 사용자 권한 확인

```bash
# RabbitMQ 관리 콘솔 접속
http://localhost:15672 (guest/guest)

# VHost 확인
rabbitmqctl list_vhosts

# 권한 확인
rabbitmqctl list_permissions -p stolink
```

### 8.3 분석 시간이 너무 오래 걸림

**예상 처리 시간**:
- 1,000자 이하: 20~30초
- 3,000자: 40~60초
- 5,000자: 60~90초

**권장사항**:
- 5,000자 이상은 챕터 단위로 분할
- context에 기존 데이터 제공하여 중복 분석 방지

### 8.4 캐릭터 ID가 기존과 다르게 생성됨

**원인**: `context.existing_characters`에 기존 캐릭터 정보 미제공

**해결**: 메시지 발행 시 기존 캐릭터 정보 포함
```json
"context": {
  "existing_characters": [
    {"id": "char-001", "name": "아린", "role": "protagonist"}
  ]
}
```

### 8.5 관계 타입이 예상과 다름

**RelationType 매핑**:
| AI 응답 값 | 설명 |
|-----------|------|
| `Romance` | 연인/사랑 |
| `Friendly` | 우호적 |
| `Hostile` | 적대적 |
| `Normal` | 중립적 |
| `Unknown` | 불명확 |

---

## 📞 연락처

문의사항이 있으시면 AI Backend 담당자에게 연락해주세요.

---

## 📎 관련 문서

- [RABBITMQ_MESSAGE_GUIDE.md](./RABBITMQ_MESSAGE_GUIDE.md) - RabbitMQ 메시지 상세
- [RABBITMQ_WEB_TESTING.md](./RABBITMQ_WEB_TESTING.md) - Web UI 테스트 가이드
- [JOB_STATUS_UPDATE_API.md](./JOB_STATUS_UPDATE_API.md) - 상태 업데이트 API
