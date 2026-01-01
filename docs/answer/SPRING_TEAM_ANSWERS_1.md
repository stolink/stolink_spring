# Spring 팀 질문 답변서 - AI Backend 팀

> **작성일**: 2026-01-01  
> **작성자**: AI Backend 팀 (Python/FastAPI)  
> **참조**: SPRING_TEAM_QUESTIONS.md

---

## 📋 목차

1. [Chapter 테이블 및 DB 설계 관련](#1-chapter-테이블-및-db-설계-관련)
2. [RabbitMQ 메시지 스키마 관련](#2-rabbitmq-메시지-스키마-관련)
3. [Python DB 조회 관련](#3-python-db-조회-관련)
4. [콜백 및 상태 관리 관련](#4-콜백-및-상태-관리-관련)
5. [2-Pass 하이브리드 처리 관련](#5-2-pass-하이브리드-처리-관련)
6. [기존 시스템 호환성 관련](#6-기존-시스템-호환성-관련)

---

## 1. Document 테이블 및 DB 설계 관련

### A1.1: 현재 구조 유지 결정

**결정: 기존 Document 계층 구조 유지 (신규 Chapter 엔티티 불필요)**

**현재 구조**:

```
Project 1 : N Document (parent_id로 계층화)
                ↓
        Document(FOLDER) → Document(FOLDER) → Document(TEXT)
        (1부)              (1장)              (장면 - 분석 대상)
```

**Entity 관계 다이어그램**:

```
┌─────────────────────────────────────────────────────────────────┐
│                   현재 Document 계층 구조                         │
│                                                                 │
│   ┌─────────┐     ┌─────────────────────────────────────┐      │
│   │ Project │────▶│            Document                  │      │
│   │         │ 1:N │  (parent_id로 자기 참조 - 계층 구조)   │      │
│   └─────────┘     └─────────────────────────────────────┘      │
│                                                                 │
│   예시:                                                         │
│   Document(FOLDER, "제1부") ──▶ Document(FOLDER, "제1장")       │
│                                      ↓                          │
│                              Document(TEXT, "장면1")            │
│                              Document(TEXT, "장면2") ← 분석 대상 │
└─────────────────────────────────────────────────────────────────┘
```

**역할 정리**:

| DocumentType | 역할 | 분석 대상 |
|--------------|------|----------|
| `FOLDER` | 목차 구조 (1부, 1장 등) | ❌ |
| `TEXT` | 실제 콘텐츠 (장면) | ✅ |

**장점**:
- ✅ 기존 코드 수정 없음
- ✅ 마이그레이션 불필요
- ✅ 이미 검증된 계층 구조

**Python 메시지 스키마**:

```json
{
  "message_type": "DOCUMENT_ANALYSIS",
  "document_id": "doc-101",
  "project_id": "proj-001",
  "parent_path": ["제1부", "제1장"],
  "callback_url": "http://...",
  "context": {
    "existing_characters": [...],
    "existing_events": [...]
  }
}
```

**Spring 분석 대상 선정**:

```java
// TEXT 타입 Document만 분석 요청
List<Document> analysisTargets = documentRepository.findByProjectIdAndType(
    projectId, 
    DocumentType.TEXT
);

for (Document doc : analysisTargets) {
    publishAnalysisMessage(doc);
}
```

---

### A1.2: Section 테이블 생성 시점

**결정: Python이 생성 → Spring Callback으로 전달**

**흐름**:

```
1. Python이 Document(TEXT)의 content를 Semantic Chunking
2. Section 목록 생성 (nav_title, content, embedding 포함)
3. Callback으로 Spring에 전달
4. Spring이 Section 테이블에 저장
```

**Section의 소속**:

```
Project 1 : N Document(계층) 1 : N Section
                ↓
        Document(TEXT)만 Section 보유
```

**Callback Payload 예시**:

```json
{
  "document_id": "doc-101",
  "parent_path": ["제1부", "제1장"],  // 상위 FOLDER 경로
  "status": "COMPLETED",
  "sections": [
    {
      "sequence_order": 1,
      "nav_title": "이안이 폐허에서 깨어나다",
      "content": "눈을 떴을 때 가장 먼저...",
      "embedding": [0.123, -0.456, ...],
      "related_characters": ["이안", "나비"],
      "related_events": ["E001"]
    }
  ],
  "characters": [...],
  "events": [...]
}
```

> [!NOTE]
> Python은 Section 테이블에 **직접 쓰기 권한 없음**. Spring이 Callback 수신 후 저장.

---

## 2. RabbitMQ 메시지 스키마 관련

### A2.1: `context.existing_*` 필드 생성 책임

**답변: Spring이 발행 시점에 DB 조회하여 포함**

**시나리오별 처리**:

| 시나리오 | existing_characters |
|----------|---------------------|
| 첫 번째 챕터 | `[]` (빈 배열) |
| 순차 처리 | 이전 챕터 Callback 결과 저장 후 조회 |
| **병렬 처리** | 아래 참조 |

**병렬 처리 시 전략**:

```
[옵션 1] 2-Pass 전략 사용 (추천)
- 1차 Pass: existing_* = [] (모든 챕터 독립 분석)
- 2차 Pass: GlobalMergerWorker가 병합

[옵션 2] 순차 의존 분석
- 챕터 1~10 순차 분석 완료 후
- 챕터 11~20 발행 시 1~10 결과 포함
- 단점: 느림 (병렬 효과 감소)
```

**Python 팀 추천**: **옵션 1 (2-Pass)**

```json
// 1차 Pass - 모든 챕터에 빈 context
{
  "message_type": "CHAPTER_ANALYSIS",
  "chapter_id": "chap-101",
  "context": {
    "existing_characters": [],  // 병렬 처리 시 비워둠
    "analysis_pass": 1          // 1차 Pass 표시
  }
}
```

---

### A2.2: `callback_url` 엔드포인트 분리

**추천: 단일 엔드포인트 + 메시지 타입 분기**

```http
POST /api/ai-callback
Content-Type: application/json

{
  "message_type": "CHAPTER_ANALYSIS",  // 또는 "FULL_DOCUMENT"
  "chapter_id": "chap-101",            // CHAPTER_ANALYSIS 시에만
  "status": "COMPLETED",
  "result": { ... }
}
```

**Spring 처리 로직**:

```java
@PostMapping("/api/ai-callback")
public ResponseEntity<?> handleCallback(@RequestBody AnalysisCallbackDTO dto) {
    if ("CHAPTER_ANALYSIS".equals(dto.getMessageType())) {
        return handleChapterCallback(dto);
    } else {
        return handleFullDocumentCallback(dto);  // 기존 로직
    }
}
```

**스키마**:
- 공통 필드: `status`, `result`, `trace_id`
- CHAPTER_ANALYSIS 전용: `chapter_id`, `sections`, `analysis_pass`
- FULL_DOCUMENT 전용: 기존 그대로

---

## 3. Python DB 조회 관련

### A3.1: Python의 PostgreSQL 접근 권한

**현재 구성**:

```yaml
# Python 환경 변수
DATABASE_URL: postgresql://readonly_user:***@spring-db:5432/stolink

# 권한 범위
- SELECT: chapter(content), project(id, title)
- INSERT/UPDATE/DELETE: ❌ 불가
```

**연결 정보 공유**:
- `.env` 파일 또는 환경 변수로 주입
- 민감 정보는 Secret Manager (AWS Secrets Manager 등) 권장

**Read Replica 여부**:
- 초기 버전: 메인 DB 직접 연결 (읽기 전용 계정)
- 트래픽 증가 시: Read Replica 도입 가능

**접근 범위 명확화**:

| 테이블 | SELECT | INSERT | UPDATE | DELETE |
|--------|--------|--------|--------|--------|
| chapter | ✅ | ❌ | ❌ | ❌ |
| project | ✅ | ❌ | ❌ | ❌ |
| document | ✅ | ❌ | ❌ | ❌ |

---

### A3.2: Character/Event ID 조회

**2차 Pass에서의 ID 조회**:

```
방식: 1차 Pass 결과를 임시 저장 후 조회
```

**흐름**:

```
1. 1차 Pass: 각 챕터 분석 결과를 Python 내부 DB/파일에 임시 저장
   - 또는 Spring Callback 후 Spring DB에서 조회

2. 2차 Pass: GlobalMergerWorker가 모든 챕터 결과 수집
   - Spring DB에서 1차 Pass 결과 조회
   - 또는 Redis/임시 테이블에서 조회

3. 병합 후 최종 Callback
```

**추천 방식**: Spring DB에서 조회

```python
# GlobalMergerWorker
async def merge_project(self, project_id: str):
    # Spring DB에서 1차 Pass 결과 조회 (읽기 전용)
    chapter_results = await db.query("""
        SELECT c.id, c.analysis_result 
        FROM chapter c 
        WHERE c.document_id IN (
            SELECT d.id FROM document d WHERE d.project_id = %s
        )
        AND c.status = 'COMPLETED'
    """, project_id)
    
    # 병합 로직...
```

---

## 4. 콜백 및 상태 관리 관련

### A4.1: 챕터 상태 업데이트 방식 선택

**추천: 옵션 B (Callback에 포함)**

**이유**:
- API 호출 횟수 감소 (네트워크 비용 절감)
- 원자적 상태 전환 (결과와 상태가 함께 전달)

**단, `PROCESSING` 상태 전환**:
- Python Consumer가 메시지 수신 시점에 **별도 API 호출** 필요

```python
async def process_chapter_message(msg: ChapterAnalysisMessage):
    # 1. PROCESSING 상태 업데이트 (별도 API)
    await http_client.patch(
        f"{SPRING_URL}/api/chapters/{msg.chapter_id}/status",
        json={"status": "PROCESSING"}
    )
    
    # 2. 분석 수행...
    
    # 3. 완료/실패 Callback (상태 포함)
    await send_callback({
        "chapter_id": msg.chapter_id,
        "status": "COMPLETED",  # 또는 "FAILED"
        "result": {...}
    })
```

---

### A4.2: 실패 시 재시도 로직 구현 위치

**추천: 하이브리드**

| 재시도 유형 | 담당 | 설명 |
|------------|------|------|
| **즉시 재시도** (3회) | Python | LLM 일시 오류, 네트워크 재시도 |
| **지연 재시도** | Spring | 3회 실패 후 스케줄링 |

**Python 내부 재시도**:

```python
@retry(stop=stop_after_attempt(3), wait=wait_exponential(min=1, max=10))
async def analyze_chapter(content: str):
    return await run_analysis_pipeline(content)
```

**최종 실패 시 Callback**:

```json
{
  "chapter_id": "chap-101",
  "status": "FAILED",
  "error": {
    "code": "LLM_TIMEOUT",
    "message": "3회 재시도 후 실패",
    "retry_count": 3
  }
}
```

**`RETRY_PENDING` → `QUEUED` 트리거**: **Spring Scheduler**

```java
@Scheduled(fixedDelay = 60000)  // 1분마다
public void retryFailedChapters() {
    List<Chapter> failed = chapterRepository.findByStatusAndRetryCountLessThan(
        "FAILED", MAX_RETRY
    );
    
    for (Chapter ch : failed) {
        ch.setStatus("RETRY_PENDING");
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, buildMessage(ch));
        ch.setStatus("QUEUED");
        ch.setRetryCount(ch.getRetryCount() + 1);
    }
}
```

---

## 5. 2-Pass 하이브리드 처리 관련

### A5.1: 1차 Pass 완료 감지 및 2차 Pass 트리거

**추천: (A) Spring이 감지 → `global_merge_queue` 발행**

**구현 방식**:

```java
// 챕터 Callback 수신 시
@PostMapping("/api/ai-callback")
public ResponseEntity<?> handleCallback(@RequestBody AnalysisCallbackDTO dto) {
    // 1. 챕터 결과 저장
    saveChapterResult(dto);
    
    // 2. 모든 챕터 완료 체크
    Project project = getProjectByChapterId(dto.getChapterId());
    long completed = chapterRepository.countByProjectIdAndStatus(
        project.getId(), "COMPLETED"
    );
    
    if (completed == project.getTotalChapters()) {
        // 3. 2차 Pass 트리거
        rabbitTemplate.convertAndSend(
            "global_merge_queue",
            new GlobalMergeMessage(project.getId())
        );
        project.setStatus("MERGE_PENDING");
    }
}
```

**왜 Spring인가?**:
- Spring이 **SSOT (Single Source of Truth)** - 상태 관리의 주체
- Python은 상태를 모름 (Stateless 처리)

---

### A5.2: 2차 Pass 결과 Callback 스키마

**GlobalMergeResult Callback**:

```json
{
  "message_type": "GLOBAL_MERGE_RESULT",
  "project_id": "proj-001",
  "status": "COMPLETED",
  "result": {
    "merged_characters": [
      {
        "final_id": "char-이안-001",
        "merged_from": ["char-이안-001", "char-ian-002", "char-Ian-003"],
        "canonical_name": "이안",
        "aliases": ["Ian", "ian"],
        "data": { /* FullCharacter 스키마 */ }
      }
    ],
    "merged_events": [...],
    "global_relationships": [
      {
        "source": "char-이안-001",
        "target": "char-나비-001",
        "type": "ALLY",
        "first_appearance": "chap-001",
        "strength": 8
      }
    ],
    "consistency_report": {
      "conflicts_detected": 2,
      "conflicts_resolved": 2,
      "details": [...]
    }
  }
}
```

**Character ID 매핑 정보**:

```json
{
  "character_id_map": {
    "char-이안-001": {
      "merged_ids": ["char-ian-002", "char-Ian-003"],
      "merge_reason": "fuzzy_match",
      "confidence": 0.95
    }
  }
}
```

**Spring 처리**:

```java
// ID 매핑 적용
for (CharacterMerge merge : result.getCharacterIdMap().values()) {
    for (String oldId : merge.getMergedIds()) {
        // 기존 참조 업데이트
        eventRepository.updateCharacterRef(oldId, merge.getFinalId());
        relationshipRepository.updateCharacterRef(oldId, merge.getFinalId());
        // 중복 캐릭터 삭제
        characterRepository.deleteById(oldId);
    }
}
```

---

## 6. 기존 시스템 호환성 관련

### A6.1: FULL_DOCUMENT 메시지 유지 여부

**추천: 조건부 유지**

| 문서 크기 | 처리 방식 |
|----------|----------|
| **10,000자 미만** | `FULL_DOCUMENT` (기존) |
| **10,000자 이상** | `CHAPTER_ANALYSIS` (분할) |

**Spring 발행 로직**:

```java
public void requestAnalysis(Document document) {
    if (document.getContent().length() < 10000) {
        // 기존 방식
        publishFullDocumentMessage(document);
    } else {
        // 신규 방식
        List<Chapter> chapters = splitIntoChapters(document);
        chapterRepository.saveAll(chapters);
        publishChapterBatch(chapters);
    }
}
```

**전환 시점**:
- Phase 1: 두 방식 **공존** (안정화 기간)
- Phase 2: **CHAPTER_ANALYSIS**로 **통일** (선택)

---

### A6.2: 기존 AICallbackService 수정 범위

**추천: 메서드 분리 + 공통 로직 재사용**

```java
@Service
public class AICallbackService {
    
    // 기존 메서드 (유지)
    public void handleFullDocumentCallback(FullDocumentCallbackDTO dto) {
        saveCharacters(dto.getCharacters());
        saveEvents(dto.getEvents());
        saveSettings(dto.getSettings());
        // ...
    }
    
    // 신규 메서드 (추가)
    public void handleChapterCallback(ChapterCallbackDTO dto) {
        // 1. 챕터 상태 업데이트
        updateChapterStatus(dto.getChapterId(), dto.getStatus());
        
        // 2. 섹션 저장 (신규)
        saveSections(dto.getChapterId(), dto.getSections());
        
        // 3. 임시 캐릭터/이벤트 저장 (2차 Pass용)
        saveTempCharacters(dto.getChapterId(), dto.getCharacters());
        saveTempEvents(dto.getChapterId(), dto.getEvents());
        
        // 4. 모든 챕터 완료 체크 → 2차 Pass 트리거
        checkAndTriggerGlobalMerge(dto.getChapterId());
    }
    
    // 신규 메서드 (추가)
    public void handleGlobalMergeCallback(GlobalMergeCallbackDTO dto) {
        // 1. 최종 캐릭터 저장 (병합된)
        saveFinalCharacters(dto.getMergedCharacters());
        
        // 2. ID 매핑 적용
        applyCharacterIdMapping(dto.getCharacterIdMap());
        
        // 3. 관계 저장
        saveRelationships(dto.getGlobalRelationships());
        
        // 4. 프로젝트 완료 표시
        markProjectCompleted(dto.getProjectId());
    }
}
```

**신규 테이블/엔티티**:

| 엔티티 | 용도 |
|--------|------|
| `Document` | 기존 계층 구조 유지 (FOLDER/TEXT) |
| `Section` | 의미 단위 분할 (Document(TEXT) 하위) |
| `TempCharacter` | 1차 Pass 결과 임시 저장 |
| `CharacterMergeLog` | 병합 이력 (감사용) |

---

## 📎 결정 필요 사항 요약

| 항목 | 결정 | Spring 확인 필요 |
|------|------|-----------------|
| **Document 구조** | 기존 계층 구조 유지 (신규 엔티티 불필요) | ✅ |
| **분석 대상** | `DocumentType.TEXT`만 분석 | ✅ |
| Section 생성 | Python → Callback | ✅ |
| existing_* 채우기 | 1차 Pass는 빈 배열 | ✅ |
| Callback 엔드포인트 | 단일 + 타입 분기 | ✅ |
| 상태 업데이트 | Callback 포함 + PROCESSING만 별도 | ✅ |
| 재시도 | Python 즉시 → Spring 지연 | ✅ |
| 2차 Pass 트리거 | Spring이 감지하여 발행 | ✅ |
| FULL_DOCUMENT 유지 | 10,000자 미만은 유지 | ✅ |

---

> 추가 질문이나 논의가 필요하면 말씀해 주세요! 🙏

