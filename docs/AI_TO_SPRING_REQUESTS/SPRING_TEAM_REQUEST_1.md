# Spring 팀 요청서 - 대용량 데이터 처리 아키텍처

> **작성일**: 2026-01-01  
> **작성자**: AI Backend 팀 (Python/FastAPI)  
> **목적**: 대용량 소설(365개 챕터) 처리를 위한 인터페이스 합의 요청

---

## 📋 목차

1. [배경 및 목표](#1-배경-및-목표)
2. [RabbitMQ 메시지 스키마 확정](#2-rabbitmq-메시지-스키마-확정)
3. [Chapter 테이블 설계](#3-chapter-테이블-설계)
4. [RabbitMQ Batch 발행 테스트](#4-rabbitmq-batch-발행-테스트)
5. [구현 체크리스트](#5-구현-체크리스트)
6. [일정 제안](#6-일정-제안)

---

## 1. 배경 및 목표

### 문제점
현재 시스템은 **전체 텍스트를 한 번에 분석**하는 구조로, 대용량 소설(100만자+) 처리에 한계가 있습니다.

| 현재 | 목표 |
|------|------|
| 전체 문서 단위 분석 | 챕터별 분할 분석 |
| 6000자당 1분 20~40초 | 365챕터 약 25분 (10배 병렬화) |
| 업로드 후 긴 대기 | **0.5초 이내** 즉시 응답 |

### 아키텍처 변경 요약

```
[현재]
Client → Spring → RabbitMQ(전체 텍스트) → Python → Callback

[변경 후]
Client → Spring → S3(원본) + DB(챕터 분할) + RabbitMQ(ID만) → Python → Callback
```

---

## 2. RabbitMQ 메시지 스키마 확정

### 2.1 현재 메시지 스키마 (유지)

```json
{
  "message_type": "FULL_DOCUMENT",
  "job_id": "story-001",
  "project_id": "proj-001",
  "document_id": "doc-001",
  "callback_url": "http://spring-backend:8080/api/ai-callback",
  "content": "전체 스토리 텍스트...",
  "context": {
    "existing_characters": [...],
    "existing_events": [...],
    "existing_relationships": [...],
    "existing_settings": [...]
  },
  "trace_id": "req-20260101-abc123"
}
```

### 2.2 신규 메시지 스키마 (요청)

**챕터별 분석 메시지** (`CHAPTER_ANALYSIS`)

```json
{
  "message_type": "CHAPTER_ANALYSIS",
  "job_id": "story-001-chap-001",
  "project_id": "proj-001",
  "chapter_id": "chap-101",
  "chapter_number": 1,
  "total_chapters": 365,
  "callback_url": "http://spring-backend:8080/api/ai-callback/chapter",
  "context": {
    "existing_characters": [
      {
        "id": "char-이안-001",
        "name": "이안",
        "role": "protagonist",
        "aliases": ["Ian"]
      }
    ],
    "existing_events": [
      {
        "id": "evt-001",
        "event_type": "ENCOUNTER",
        "summary": "이안이 폐허에서 나비를 만남"
      }
    ],
    "existing_relationships": [],
    "existing_settings": []
  },
  "trace_id": "req-20260101-abc123"
}
```

### 2.3 필드 상세 설명

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `message_type` | string | ✅ | `"CHAPTER_ANALYSIS"` 또는 `"FULL_DOCUMENT"` |
| `job_id` | string | ✅ | 전체 작업 ID (예: `story-001-chap-001`) |
| `project_id` | string | ✅ | 프로젝트 UUID |
| `chapter_id` | string | ✅ | **신규** - 챕터 UUID (DB 조회용) |
| `chapter_number` | int | ✅ | **신규** - 챕터 순번 (1부터 시작) |
| `total_chapters` | int | ✅ | **신규** - 전체 챕터 수 |
| `callback_url` | string | ✅ | 결과 전송 URL |
| `context` | object | ✅ | 기존 데이터 참조 |
| `trace_id` | string | ❌ | 분산 추적 ID |

### 2.4 핵심 변경: `content` 필드 제거

> **Claim Check Pattern 적용**

```
[변경 전] 메시지에 content 포함 (수십 KB)
[변경 후] chapter_id만 전송, Python이 DB에서 조회

이유:
1. RabbitMQ 메시지 크기 최소화 (OOM 방지)
2. 재시도 시 네트워크 비용 감소
3. 데이터 일관성 보장 (항상 최신 DB 값 참조)
```

### 2.5 Python 측 처리 로직

```python
# Python Consumer가 하는 일
async def process_chapter_message(msg: ChapterAnalysisMessage):
    # 1. DB에서 content 조회
    content = await db.query(
        "SELECT content FROM chapter WHERE id = %s", 
        msg.chapter_id
    )
    
    # 2. 기존 파이프라인 실행
    result = await run_analysis_pipeline(
        content=content,
        existing_characters=msg.context.existing_characters,
        ...
    )
    
    # 3. Callback 전송
    await send_callback(msg.callback_url, result)
```

---

## 3. Chapter 테이블 설계

### 3.1 테이블 스키마 (PostgreSQL)

```sql
CREATE TABLE chapter (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    
    -- 챕터 정보
    chapter_number INT NOT NULL,
    chapter_title VARCHAR(200),
    content TEXT NOT NULL,
    
    -- 메타데이터
    word_count INT,
    char_count INT,
    start_offset INT,          -- 원본 텍스트 내 시작 위치
    end_offset INT,            -- 원본 텍스트 내 끝 위치
    
    -- 상태 관리
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    retry_count INT DEFAULT 0,
    
    -- 타임스탬프
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    
    -- 제약 조건
    CONSTRAINT unique_chapter_per_project UNIQUE (project_id, chapter_number)
);

-- 인덱스
CREATE INDEX idx_chapter_project ON chapter(project_id);
CREATE INDEX idx_chapter_status ON chapter(status);
CREATE INDEX idx_chapter_project_status ON chapter(project_id, status);
```

### 3.2 상태 필드 정의

| 상태 | 설명 | 전이 조건 |
|------|------|----------|
| `PENDING` | 생성됨, RabbitMQ 발행 대기 | 초기 상태 |
| `QUEUED` | RabbitMQ 발행 완료 | 메시지 발행 후 |
| `PROCESSING` | Python 워커가 처리 중 | Consumer가 메시지 수신 시 |
| `COMPLETED` | 분석 완료 | Callback 수신 시 |
| `FAILED` | 분석 실패 | Callback 실패 수신 시 |
| `RETRY_PENDING` | 재시도 대기 | 실패 후 재시도 스케줄링 |

### 3.3 상태 전이 다이어그램

```
                    ┌─────────┐
                    │ PENDING │
                    └────┬────┘
                         │ RabbitMQ 발행
                         ▼
                    ┌─────────┐
                    │ QUEUED  │
                    └────┬────┘
                         │ Consumer 수신
                         ▼
                   ┌──────────────┐
                   │  PROCESSING  │
                   └──────┬───────┘
                          │
              ┌───────────┴───────────┐
              ▼                       ▼
        ┌───────────┐           ┌─────────┐
        │ COMPLETED │           │ FAILED  │
        └───────────┘           └────┬────┘
                                     │ 재시도 로직
                                     ▼
                              ┌──────────────┐
                              │RETRY_PENDING │
                              └──────┬───────┘
                                     │ 재발행
                                     ▼
                                ┌─────────┐
                                │ QUEUED  │
                                └─────────┘
```

### 3.4 상태 업데이트 API (Python → Spring)

Python에서 상태 업데이트가 필요할 때:

```http
PATCH /api/chapters/{chapterId}/status
Content-Type: application/json

{
  "status": "PROCESSING",
  "trace_id": "req-20260101-abc123"
}
```

**또는** Callback에 상태 포함:

```http
POST /api/ai-callback/chapter
Content-Type: application/json

{
  "chapter_id": "chap-101",
  "status": "COMPLETED",
  "result": { ... }
}
```

---

## 4. RabbitMQ Batch 발행 테스트

### 4.1 목표

> **365개 챕터 메시지를 0.5초 이내에 발행**

### 4.2 구현 요구사항

#### A. TransactionalEventListener 패턴 (필수)

```java
// ❌ 위험: DB 트랜잭션 내에서 RabbitMQ 대기
@Transactional
public void processUpload(MultipartFile file) {
    projectRepository.save(project);
    rabbitTemplate.invoke(...).waitForConfirms(3000);  // DB Connection 점유!
}

// ✅ 안전: 트랜잭션 커밋 후 발행
@Transactional
public void processUpload(MultipartFile file) {
    Project project = projectRepository.save(project);
    List<Chapter> chapters = splitAndSaveChapters(file);
    
    // 메모리 이벤트 발행 (트랜잭션 내)
    applicationEventPublisher.publishEvent(
        new ProjectCreatedEvent(project.getId(), chapters.size())
    );
}

// 트랜잭션 완료 후 실행
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onProjectCreated(ProjectCreatedEvent event) {
    List<Chapter> chapters = chapterRepository.findByProjectId(event.getProjectId());
    
    try {
        rabbitTemplate.invoke(operations -> {
            for (Chapter ch : chapters) {
                ChapterMessage msg = buildMessage(ch);
                operations.convertAndSend(EXCHANGE, ROUTING_KEY, msg);
            }
            operations.waitForConfirms(5000);  // 5초 타임아웃
            return null;
        });
        
        // 상태 업데이트: PENDING → QUEUED
        chapterRepository.updateStatusByProjectId(event.getProjectId(), "QUEUED");
        
    } catch (Exception e) {
        log.error("RabbitMQ 발행 실패", e);
        // 보상 트랜잭션
        projectService.markAsFailed(event.getProjectId());
    }
}
```

#### B. Batch 발행 설정

```yaml
# application.yml
spring:
  rabbitmq:
    publisher-confirm-type: correlated  # Publisher Confirms 활성화
    publisher-returns: true
    template:
      default-receive-queue: chapter_analysis_queue
```

#### C. 메시지 크기 최적화

```java
// 메시지 예시 (약 500 bytes)
{
  "message_type": "CHAPTER_ANALYSIS",
  "job_id": "story-001-chap-001",
  "project_id": "proj-001",
  "chapter_id": "chap-101",
  "chapter_number": 1,
  "total_chapters": 365,
  "callback_url": "http://...",
  "context": {
    "existing_characters": []  // 첫 챕터는 비어있음
  },
  "trace_id": "req-..."
}

// 365개 × 500 bytes = 약 180 KB (RabbitMQ 적정 범위)
```

### 4.3 성능 테스트 시나리오

```java
@Test
void batchPublishPerformanceTest() {
    // Given: 365개 챕터 준비
    List<Chapter> chapters = createTestChapters(365);
    
    // When: Batch 발행
    long startTime = System.currentTimeMillis();
    
    rabbitTemplate.invoke(operations -> {
        for (Chapter ch : chapters) {
            operations.convertAndSend(EXCHANGE, ROUTING_KEY, buildMessage(ch));
        }
        operations.waitForConfirms(5000);
        return null;
    });
    
    long duration = System.currentTimeMillis() - startTime;
    
    // Then: 500ms 이내 완료
    assertThat(duration).isLessThan(500);
    
    log.info("365개 메시지 발행: {}ms", duration);
}
```

### 4.4 예상 결과

| 항목 | 예상값 | 비고 |
|------|--------|------|
| 메시지 크기 | ~500 bytes | context 비어있을 때 |
| 발행 시간 | ~50ms | Batch 모드 |
| 네트워크 왕복 | 1회 | waitForConfirms |
| DB 커넥션 점유 | 0ms | AFTER_COMMIT 패턴 |

---

## 5. 구현 체크리스트

### Spring 팀 작업

| 우선순위 | 작업 | 예상 시간 | 상태 |
|----------|------|----------|------|
| **P0** | Chapter 테이블 생성 | 1h | 🔲 |
| **P0** | 챕터 분할 정규식 구현 | 4h | 🔲 |
| **P0** | Cascading Fallback 로직 | 2h | 🔲 |
| **P0** | RabbitMQ Batch 발행 | 2h | 🔲 |
| **P0** | TransactionalEventListener 적용 | 1h | 🔲 |
| **P1** | 상태 업데이트 API | 1h | 🔲 |
| **P1** | Callback 엔드포인트 확장 | 2h | 🔲 |
| **P2** | 성능 테스트 | 2h | 🔲 |

### Python 팀 작업 (병렬 진행)

| 우선순위 | 작업 | 상태 |
|----------|------|------|
| **P0** | 메시지 스키마 확장 | 🔲 |
| **P0** | DB 조회 서비스 추가 | 🔲 |
| **P1** | 챕터 Consumer 구현 | 🔲 |
| **P2** | Fuzzy Matching 추가 | 🔲 |

---

## 6. 일정 제안

```
Week 1: Foundation
├── Day 1-2: 스키마 합의 + 테이블 생성
├── Day 3-4: 챕터 분할 + Batch 발행
└── Day 5: 통합 테스트

Week 2: Integration
├── Day 1-3: Python Consumer + Spring Callback 연동
├── Day 4-5: E2E 테스트

Week 3: 2차 Pass
├── GlobalMergerWorker 구현
└── Entity Resolution 구현
```

## 📎 참고 문서

- [big_data_processing.md](./big_data_processing.md) - 전체 아키텍처 설계
- [TROUBLESHOOTING.md](./TROUBLESHOOTING.md) - 기존 이슈 및 해결책
- [AI_BACKEND_INTEGRATION.md](./AI_BACKEND_INTEGRATION.md) - 기존 통합 가이드

---