# Spring 팀 질문사항 - AI Backend 팀 요청서 검토

> **작성일**: 2026-01-01  
> **작성자**: Spring Backend 팀  
> **목적**: SPRING_TEAM_REQUEST.md 및 big_data_processing.md 검토 후 명확화가 필요한 사항

---

## 📋 목차

1. [Chapter 테이블 및 DB 설계 관련](#1-chapter-테이블-및-db-설계-관련)
2. [RabbitMQ 메시지 스키마 관련](#2-rabbitmq-메시지-스키마-관련)
3. [Python DB 조회 관련](#3-python-db-조회-관련)
4. [콜백 및 상태 관리 관련](#4-콜백-및-상태-관리-관련)
5. [2-Pass 하이브리드 처리 관련](#5-2-pass-하이브리드-처리-관련)
6. [기존 시스템 호환성 관련](#6-기존-시스템-호환성-관련)

---

## 1. Chapter 테이블 및 DB 설계 관련

### Q1.1: Chapter 테이블과 기존 Document 테이블의 관계

현재 Spring 프로젝트에는 `Document` 엔티티가 존재하며 `Project`와 1:N 관계입니다:

```java
// Project.java
@OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Document> documents = new ArrayList<>();
```

**질문**: 
- 새로운 `Chapter` 테이블은 `Document`와 어떤 관계를 가지나요?
  - (A) `Document` 1 : N `Chapter` (문서당 여러 챕터)
  - (B) `Project` 1 : N `Chapter` (프로젝트에 직접 연결, Document 무관)
  - (C) 기존 `Document` 테이블을 `Chapter`로 대체

> [!IMPORTANT]
> 기존 `Document` 테이블의 역할이 "전체 문서 저장"이었다면, 새 아키텍처에서는 S3에 원본을 저장하고 `Chapter`로 분할하는 것으로 이해됩니다. 이 경우 `Document` 테이블의 향후 역할을 명확히 해주세요.

아래는 현재 Document entity의 구조입니다.

```java
@Entity
@Table(name = "documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Document parent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentType type;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content = "";

    @Column(columnDefinition = "TEXT")
    private String synopsis = "";

    @Column(name = "\"order\"", nullable = false)
    private Integer order = 0;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DocumentStatus status = DocumentStatus.DRAFT;

    @Column(length = 50)
    private String label;

    @Column(length = 7)
    private String labelColor;

    @Column(nullable = false)
    private Integer wordCount = 0;

    private Integer targetWordCount;

    @Column(nullable = false)
    private Boolean includeInCompile = true;

    @Column(columnDefinition = "text")
    private String keywords; // Comma-separated tags

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Builder
    public Document(UUID id, Project project, Document parent, DocumentType type, String title, String content, String synopsis, Integer order, DocumentStatus status, String label, String labelColor, Integer wordCount, Integer targetWordCount, Boolean includeInCompile, String keywords, String notes) {
        this.id = id;
        this.project = project;
        this.parent = parent;
        this.type = type;
        this.title = title;
        this.content = content;
        this.synopsis = synopsis;
        this.order = order;
        this.status = status;
        this.label = label;
        this.labelColor = labelColor;
        this.wordCount = wordCount;
        this.targetWordCount = targetWordCount;
        this.includeInCompile = includeInCompile;
        this.keywords = keywords;
        this.notes = notes;
    }

    public void updateContent(String content) {
        this.content = content;
        this.wordCount = calculateWordCount(content);
    }

    public void update(String title, String synopsis, Integer order, DocumentStatus status,
            Integer targetWordCount, Boolean includeInCompile, String notes) {
        if (title != null)
            this.title = title;
        if (synopsis != null)
            this.synopsis = synopsis;
        if (order != null)
            this.order = order;
        if (status != null)
            this.status = status;
        if (targetWordCount != null)
            this.targetWordCount = targetWordCount;
        if (includeInCompile != null)
            this.includeInCompile = includeInCompile;
        if (notes != null)
            this.notes = notes;
    }

    public void updateLabel(String label, String labelColor) {
        if (label != null)
            this.label = label;
        if (labelColor != null)
            this.labelColor = labelColor;
    }

    public void updateKeywords(String keywords) {
        this.keywords = keywords;
    }

    /**
     * 문서의 부모를 변경합니다 (폴더 이동)
     * @param newParent 새로운 부모 문서 (null이면 루트로 이동)
     * @param newOrder 새 부모 아래에서의 순서
     */
    public void updateParent(Document newParent, int newOrder) {
        this.parent = newParent;
        this.order = newOrder;
    }

    private int calculateWordCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // Simple word count - can be enhanced
        return text.replaceAll("<[^>]*>", "").trim().length();
    }

    public enum DocumentType {
        FOLDER, TEXT
    }

    public enum DocumentStatus {
        DRAFT, REVISED, FINAL
    }
}
```

### Q1.2: Section 테이블 생성 시점

`big_data_processing.md`에 `section` 테이블이 소개되어 있습니다:

```sql
CREATE TABLE section (
    id BIGSERIAL PRIMARY KEY,
    chapter_id BIGINT NOT NULL,
    nav_title VARCHAR(100),
    content TEXT NOT NULL,
    embedding vector(1536),
    ...
);
```

**질문**:
- `Section`은 Python 측에서 생성하여 Spring Callback으로 전달하나요?
- 아니면 Spring이 직접 테이블을 생성하고 Python은 조회만 하나요?

---

## 2. RabbitMQ 메시지 스키마 관련

### Q2.1: `context.existing_*` 필드 생성 책임

요청서의 `CHAPTER_ANALYSIS` 메시지에 `context.existing_characters` 등이 포함됩니다:

```json
{
  "context": {
    "existing_characters": [
      {"id": "char-이안-001", "name": "이안", "role": "protagonist", "aliases": ["Ian"]}
    ],
    ...
  }
}
```

**질문**:
- 이 `context` 데이터는 Spring이 챕터별 메시지 발행 시점에 DB에서 조회하여 포함시키나요?
- 첫 번째 챕터는 빈 배열, 이후 챕터는 이전 챕터에서 추출된 캐릭터를 포함?
- 만약 병렬 처리(10 워커)라면, 챕터 10번 분석 시점에 챕터 1~9의 결과가 아직 없을 수 있는데, 이 경우 `existing_*`는 어떻게 채워지나요?

### Q2.2: `callback_url` 엔드포인트 분리

요청서에 두 가지 callback URL이 언급됩니다:
- 기존: `/api/ai-callback` (FULL_DOCUMENT)
- 신규: `/api/ai-callback/chapter` (CHAPTER_ANALYSIS)

**질문**:
- 두 엔드포인트는 별도 구현이 필요한가요?
- 응답 스키마(AnalysisCallbackDTO)는 동일한가요, 다른가요?

---

## 3. Python DB 조회 관련

### Q3.1: Python의 PostgreSQL 접근 권한

요청서에 따르면 Python은 `chapter` 테이블에서 `content`를 조회합니다:

```python
content = await db.query(
    "SELECT content FROM chapter WHERE id = %s", 
    msg.chapter_id
)
```

**질문**:
- Python이 Spring의 PostgreSQL에 직접 연결하나요?
- 별도 Read Replica를 사용하나요?
- 연결 정보(host, port, credentials)는 어떻게 공유되나요?

> [!WARNING]
> 현재 구조(`big_data_processing.md` 6️⃣)에서는 "FastAPI는 쓰기 권한 없음"으로 되어 있는데, 읽기 전용 접근의 범위를 명확히 해주세요.

### Q3.2: Character/Event ID 조회

2-Pass 처리에서 2차 Pass(GlobalMergerWorker)가 모든 챕터의 캐릭터를 병합할 때, 기존 캐릭터 ID는 어디서 조회하나요?

- Spring PostgreSQL에서 직접 조회?
- Neo4j에서 조회?
- 1차 Pass 결과 저장 후 Callback으로 전달받은 ID 사용?

---

## 4. 콜백 및 상태 관리 관련

### Q4.1: 챕터 상태 업데이트 방식 선택

요청서에 두 가지 옵션이 제시되어 있습니다:

**옵션 A: 별도 API**
```http
PATCH /api/chapters/{chapterId}/status
```

**옵션 B: Callback에 포함**
```json
{
  "chapter_id": "chap-101",
  "status": "COMPLETED",
  "result": { ... }
}
```

**질문**:
- 어떤 방식을 선호하시나요?
- 옵션 B 선택 시, `PROCESSING` 상태 전환은 Python Consumer가 메시지 수신 시점에 별도 API 호출로 하나요?

### Q4.2: 실패 시 재시도 로직 구현 위치

챕터 분석 실패 시:
- Python에서 내부 재시도 후 최종 실패만 Callback?
- 모든 실패를 Callback하고 Spring이 재시도 스케줄링?

요청서의 상태 다이어그램에 `RETRY_PENDING` → `QUEUED` 전이가 있는데, 이 재발행 트리거는 누가 담당하나요?

---

## 5. 2-Pass 하이브리드 처리 관련

### Q5.1: 1차 Pass 완료 감지 및 2차 Pass 트리거

365개 챕터 병렬 처리 후 GlobalMergerWorker가 동작하려면:

**질문**:
- 모든 챕터가 `COMPLETED`되었는지 누가 판단하나요?
  - (A) Spring이 DB 폴링 또는 카운터로 감지 → `global_merge_queue`에 메시지 발행
  - (B) Python 측에서 마지막 챕터 Complete 시 자동 트리거
  - (C) 별도 Scheduler가 주기적으로 체크

### Q5.2: 2차 Pass 결과 Callback 스키마

GlobalMergerWorker가 Entity Resolution 완료 후:
- 기존 캐릭터 ID 병합 결과를 어떤 형태로 Callback 하나요?
- Character ID 매핑 정보 (`char-이안-001` ← `char-ian-002` 병합됨) 전달 방식?

---

## 6. 기존 시스템 호환성 관련

### Q6.1: FULL_DOCUMENT 메시지 유지 여부

`SPRING_TEAM_REQUEST.md` 2.1절에 "현재 메시지 스키마 (유지)"라고 되어 있습니다.

**질문**:
- 소규모 문서(예: 6000자 미만)는 기존 `FULL_DOCUMENT` 방식으로 처리?
- 아니면 모든 문서가 챕터 분할 → `CHAPTER_ANALYSIS`로 통일?

### Q6.2: 기존 AICallbackService 수정 범위

현재 `AICallbackService.java`는 단일 문서 분석 결과를 처리합니다:

```java
// 현재 메서드 (920줄)
handleAnalysisCallback(AnalysisCallbackDTO callback)
saveCharacters(...)
saveEvents(...)
saveSettings(...)
...
```

**질문**:
- 챕터별 Callback은 별도 서비스로 분리하나요?
- 기존 로직을 재사용하고 챕터 ID만 추가 처리?

---

## 📎 다음 단계 제안

위 질문에 대한 답변을 바탕으로:

1. **DB 스키마 확정** - Chapter/Section/Document 관계 정의
2. **인터페이스 명세서 작성** - OpenAPI Spec으로 Callback 엔드포인트 정의
3. **Python DB 접근 설정** - 연결 정보 및 권한 합의

---

## 📚 참고한 문서

- [SPRING_TEAM_REQUEST.md](./SPRING_TEAM_REQUEST.md) - AI Backend 팀 요청서
- [big_data_processing.md](./big_data_processing.md) - 전체 아키텍처 설계
- 현재 Spring 코드베이스:
  - `Project.java` - 프로젝트 엔티티
  - `AICallbackService.java` - 기존 콜백 처리 (920줄)
  - `RabbitMQProducerService.java` - 메시지 발행
  - `RabbitMQConfig.java` - RabbitMQ 설정

---
