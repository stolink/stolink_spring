# Spring 팀 질문사항 - AI Backend 팀 요청서 검토

> **작성일**: 2026-01-01  
> **작성자**: Spring Backend 팀  
> **목적**: SPRING_TEAM_REQUEST.md 및 big_data_processing.md 검토 후 명확화가 필요한 사항

---

## 📋 현재 시스템 구조

우리 Spring 프로젝트는 기존 `Document` 엔티티를 활용한 계층 구조를 사용합니다:

```
Project
  └── Document (type=FOLDER, parent=null) ← Chapter 역할
        └── Document (type=TEXT, parent=Chapter) ← 실제 텍스트 콘텐츠
              └── Section (신규 테이블) ← 의미적 분할 단위
```

### Document 엔티티 핵심 필드

| 필드 | 타입 | 역할 |
|------|------|------|
| `id` | UUID | 문서 고유 ID |
| `project` | Project | 소속 프로젝트 |
| `parent` | Document | 부모 문서 (Chapter → TEXT 연결) |
| `type` | Enum | `FOLDER` (챕터) / `TEXT` (본문) |
| `content` | TEXT | 실제 텍스트 내용 |
| `order` | Integer | 정렬 순서 (chapter_number로 활용) |
| `status` | Enum | `DRAFT`, `REVISED`, `FINAL` |

> [!IMPORTANT]
> AI 팀 요청서의 `chapter` 테이블 = 우리 시스템의 `Document(type=FOLDER)`  
> AI 팀 요청서의 chapter `content` = 우리 시스템의 `Document(type=TEXT).content`

---

## 📋 목차

1. [Document 구조 매핑 관련](#1-document-구조-매핑-관련)
2. [RabbitMQ 메시지 스키마 관련](#2-rabbitmq-메시지-스키마-관련)
3. [Python DB 조회 관련](#3-python-db-조회-관련)
4. [콜백 및 상태 관리 관련](#4-콜백-및-상태-관리-관련)
5. [2-Pass 하이브리드 처리 관련](#5-2-pass-하이브리드-처리-관련)
6. [Section 테이블 설계 관련](#6-section-테이블-설계-관련)

---

## 1. Document 구조 매핑 관련

### Q1.1: 분석 요청 단위 확인

요청서에서 `chapter_id`로 분석을 요청하는데, 우리 시스템에서는:

```
Document(FOLDER) "1장" (id: chapter-uuid)
  └── Document(TEXT) "1장 본문" (id: text-uuid, content: "실제 텍스트...")
```

**질문**:
- RabbitMQ 메시지의 `chapter_id`에 어떤 UUID를 넣어야 하나요?
  - (A) `Document(FOLDER).id` - 챕터 폴더 ID
  - (B) `Document(TEXT).id` - 실제 텍스트가 있는 문서 ID

```json
{
  "message_type": "CHAPTER_ANALYSIS",
  "chapter_id": "어떤 UUID?",  // FOLDER의 ID vs TEXT의 ID
  ...
}
```

### Q1.2: 챕터 내 다중 TEXT 문서 처리

한 챕터 폴더 안에 여러 TEXT 문서가 있을 수 있습니다:

```
Document(FOLDER) "1장"
  ├── Document(TEXT) "1-1. 도입부"
  ├── Document(TEXT) "1-2. 전개"
  └── Document(TEXT) "1-3. 마무리"
```

**질문**:
- 이 경우 분석 요청을 어떻게 하나요?
  - (A) 각 TEXT 문서마다 별도 메시지 발행 (3개 메시지)
  - (B) FOLDER ID로 1개 메시지 발행, Python이 하위 TEXT들을 조회해서 병합
  - (C) Spring이 하위 TEXT들을 미리 병합해서 1개로 전송

### Q1.3: 상태 필드 추가 필요성

요청서에 챕터 상태 필드가 정의되어 있습니다:
`PENDING` → `QUEUED` → `PROCESSING` → `COMPLETED` / `FAILED`

현재 `DocumentStatus`는 `DRAFT`, `REVISED`, `FINAL`만 있습니다.

**질문**:
- AI 분석 상태를 어디에 저장하나요?
  - (A) `DocumentStatus`에 `ANALYZING`, `ANALYZED`, `ANALYSIS_FAILED` 추가
  - (B) 별도 `analysis_status` 컬럼을 `documents` 테이블에 추가
  - (C) 새로운 `document_analysis_job` 테이블 생성 (문서-분석 매핑)

---

## 2. RabbitMQ 메시지 스키마 관련

### Q2.1: `context.existing_*` 필드와 병렬 처리

요청서의 병렬 처리 시나리오:
- 1차 Pass: 10 워커가 365개 챕터 병렬 분석
- 챕터 10번 분석 시점에 챕터 1~9의 결과가 없을 수 있음

**질문**:
- 병렬 처리 시 `existing_characters`는 어떻게 채워지나요?
  - (A) 1차 Pass에서는 모든 챕터에 빈 배열 `[]` 전송
  - (B) 이전에 분석 완료된 챕터의 결과만 포함 (비결정적)
  - (C) 순차 처리로 변경하여 항상 이전 챕터 결과 포함

### Q2.2: Document 계층 조회를 위한 추가 필드

Python이 `documents` 테이블을 조회할 때, 계층 구조를 이해하려면 추가 정보가 필요합니다.

**질문**:
- 메시지에 다음 필드들이 필요한가요?

```json
{
  "chapter_id": "folder-uuid",
  "text_document_ids": ["text-uuid-1", "text-uuid-2"],  // 추가 필요?
  "chapter_order": 1,  // Document.order 값
  ...
}
```

### Q2.3: 콜백 URL 엔드포인트 분리

**질문**:
- `/api/ai-callback/chapter` 신규 엔드포인트의 응답 스키마가 기존과 다른가요?
- 챕터 단위 결과에는 어떤 필드가 포함되나요?

---

## 3. Python DB 조회 관련

### Q3.1: documents 테이블 조회 쿼리

Python이 `chapter_id`로 콘텐츠를 조회할 때:

```python
# 요청서 예시 (chapter 테이블 가정)
content = await db.query("SELECT content FROM chapter WHERE id = %s", msg.chapter_id)
```

**우리 시스템에서는**:

```python
# 옵션 A: TEXT 문서 직접 조회
content = await db.query(
    "SELECT content FROM documents WHERE id = %s AND type = 'TEXT'", 
    msg.text_document_id
)

# 옵션 B: FOLDER ID로 하위 TEXT들 조회
contents = await db.query(
    "SELECT content FROM documents WHERE parent_id = %s AND type = 'TEXT' ORDER BY \"order\"",
    msg.chapter_folder_id
)
merged_content = '\n'.join(contents)
```

**질문**:
- 어떤 방식을 지원해야 하나요?
- 하위 TEXT 문서들의 `order`에 따른 정렬이 필요한가요?

### Q3.2: 읽기 전용 접근 범위

Python이 조회할 수 있는 테이블 목록 확인:

| 테이블 | 읽기 | 쓰기 | 용도 |
|--------|------|------|------|
| `documents` | ✅? | ❌ | 콘텐츠 조회 |
| `characters` | ✅? | ❌ | existing_characters 조회 |
| `events` | ✅? | ❌ | existing_events 조회 |
| `settings` | ✅? | ❌ | existing_settings 조회 |
| `section` (신규) | ✅? | ✅? | Section 저장 |

**질문**:
- `section` 테이블 쓰기 권한이 Python에 필요한가요?
- 아니면 Section도 Callback으로 Spring에 전달하나요?

---

## 4. 콜백 및 상태 관리 관련

### Q4.1: Document 상태 업데이트 방식

**옵션 A: 별도 API**
```http
PATCH /api/documents/{documentId}/analysis-status
{
  "status": "PROCESSING"
}
```

**옵션 B: Callback에 포함**
```json
{
  "document_id": "text-uuid",
  "chapter_folder_id": "folder-uuid",  // 부모 챕터
  "status": "COMPLETED",
  "result": { ... }
}
```

**질문**:
- Document ID(TEXT) 기준으로 상태 관리하나요, FOLDER ID(Chapter) 기준인가요?
- `PROCESSING` 상태 전환 시점은 언제인가요? (메시지 수신 즉시? 분석 시작 시?)

### Q4.2: 실패 및 재시도 로직

**질문**:
- 재시도 횟수(`retry_count`)는 어디에 저장하나요?
  - (A) `documents` 테이블에 컬럼 추가
  - (B) 별도 `analysis_job` 테이블
  - (C) Python 메모리 / Redis
- `RETRY_PENDING` → `QUEUED` 재발행 트리거는 누가 담당하나요?

---

## 5. 2-Pass 하이브리드 처리 관련

### Q5.1: 1차 Pass 완료 감지

365개 Document(TEXT) 분석 완료 후 GlobalMergerWorker 트리거:

**질문**:
- 프로젝트 내 모든 TEXT 문서 분석 완료 판단 기준은?
  - (A) Spring이 `documents` 테이블 상태 폴링
  - (B) Callback 수신 시 카운터 체크 → 마지막 완료 시 `global_merge_queue` 발행
  - (C) Python에서 `total_chapters` 도달 시 자동 트리거

### Q5.2: Entity Resolution 결과 반영

2차 Pass에서 캐릭터 병합 결과 (`"이안" == "Ian"` 확인됨):

**질문**:
- 병합 결과를 어떻게 DB에 반영하나요?
  - 중복 캐릭터 삭제?
  - `merged_into` 참조 필드 추가?
  - `aliases` 배열에 추가?

---

## 6. Section 테이블 설계 관련

### Q6.1: Section 테이블 FK 관계

요청서의 Section 스키마:
```sql
CREATE TABLE section (
    chapter_id BIGINT NOT NULL,  -- 어떤 테이블 참조?
    ...
);
```

**우리 시스템에서는**:
```sql
CREATE TABLE section (
    document_id UUID NOT NULL REFERENCES documents(id),  -- TEXT 문서
    -- 또는
    parent_folder_id UUID REFERENCES documents(id),  -- FOLDER 챕터
    ...
);
```

**질문**:
- Section은 `Document(TEXT)`에 직접 연결되나요?
- 아니면 `Document(FOLDER)` 챕터에 연결되나요?

### Q6.2: Section 생성 주체

**질문**:
- Section은 누가 생성하나요?
  - (A) Python이 Semantic Chunking 후 Callback으로 전달 → Spring이 저장
  - (B) Python이 직접 `section` 테이블에 INSERT
  - (C) Spring이 TEXT 콘텐츠를 분할하여 저장

### Q6.3: pgvector 통합

Section 임베딩 저장:

```sql
embedding vector(1536)  -- pgvector 확장 필요
```

**질문**:
- 임베딩 생성은 Python 측에서 하고 값만 전달하나요?
- Spring PostgreSQL에 pgvector 확장이 이미 설치되어 있어야 하나요?

---

## 📎 다음 단계 제안

1. **용어 매핑 확정**
   - AI 팀 `chapter` = Spring `Document(FOLDER)`
   - AI 팀 `chapter.content` = Spring `Document(TEXT).content`

2. **인터페이스 명세서 작성**
   - 메시지 스키마에 `document_type`, `parent_id` 필드 추가 여부
   - Callback DTO에 Document 계층 정보 포함 여부

3. **DB 스키마 변경**
   - `documents` 테이블에 `analysis_status`, `retry_count` 컬럼 추가
   - `section` 테이블 생성 및 FK 정의

---

## 📚 참고

- [SPRING_TEAM_REQUEST.md](./SPRING_TEAM_REQUEST.md) - AI Backend 팀 요청서
- [big_data_processing.md](./big_data_processing.md) - 전체 아키텍처 설계
- 현재 Spring 코드베이스:
  - `Document.java` - 문서 엔티티 (계층 구조)
  - `Project.java` - 프로젝트 엔티티
  - `AICallbackService.java` - 기존 콜백 처리

---
