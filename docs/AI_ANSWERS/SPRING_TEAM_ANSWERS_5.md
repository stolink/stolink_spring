# Spring 팀 질문에 대한 답변 (Answers to Questions #5)

> **작성일**: 2026-01-02  
> **참고**: `docs/SPRING_QUESTIONS/SPRING_TEAM_QUESTIONS_5.md`

---

## 1. 챕터 분할 로직 (Cascading Fallback)

### Q1-1: 이 로직은 Spring에서 구현해야 하나요, AI에서 처리하나요?

**A: Spring Backend에서 구현해야 합니다.**

| 구분 | 담당 | 설명 |
|---|---|---|
| **챕터 분할 (Chapter Splitting)** | Spring | 대용량 파일(소설 전체)을 챕터 단위로 나눔 |
| **섹션 분할 (Semantic Chunking)** | AI | 각 챕터 내용을 의미 단위(Scene)로 나눔 |

**현재 흐름**:
```
[사용자 업로드] → [Spring: 챕터 분할] → [RabbitMQ: N개 메시지] → [AI: 섹션 분할]
```

- AI의 `ChunkingService`는 **단일 챕터(또는 문서)**를 입력으로 받아 **섹션**으로 분할합니다.
- 만약 소설 전체(100만자)를 한 번에 보내면, AI의 메모리/토큰 제한에 걸릴 수 있습니다.
- 따라서 Spring이 **정규식 등으로 챕터 단위로 분할**하고, 각 챕터를 개별 메시지로 발행해야 합니다.

### Q1-2: 분할된 챕터마다 별도의 분석 요청을 보내야 하나요?

**A: 네, 맞습니다.**

- 각 챕터(또는 Document)에 대해 `document_analysis_queue`에 **개별 메시지**를 발행합니다.
- AI 워커가 **병렬로** 처리하므로 (prefetch_count=10), 100개의 챕터도 빠르게 처리됩니다.
- 메시지 예시:
  ```json
  { "message_type": "DOCUMENT_ANALYSIS", "project_id": "...", "document_id": "chapter-001", ... }
  { "message_type": "DOCUMENT_ANALYSIS", "project_id": "...", "document_id": "chapter-002", ... }
  ...
  ```

---

## 2. SSE 실시간 상태 알림

### Q2-1: AI 서버에서 Progress Update를 실시간으로 보내주나요?

**A: 현재는 아닙니다.**

| 이벤트 | 전달 방식 | 현재 상태 |
|---|---|---|
| **분석 시작** | Spring이 메시지 발행 시 자체적으로 DB 상태 업데이트 | ✅ Spring 관리 |
| **분석 진행률** (30%, 50%, ...) | 미구현 | ❌ 미지원 |
| **분석 완료** | AI → Spring Callback (`POST /api/ai-callback`) | ✅ 구현됨 |

**권장 구현 (Spring 측)**:
1.  메시지 발행 시 `Document.status = PROCESSING` 저장
2.  Callback 수신 시 `Document.status = COMPLETED` 저장
3.  클라이언트는 SSE로 DB `status` 변경을 구독

**향후 고도화 (AI 측)**:
- 필요시 RabbitMQ의 별도 큐(`progress_queue`)로 중간 진행률 메시지를 보낼 수 있습니다.
- 하지만 현재 우선순위는 낮습니다.

### Q2-2: 현재 AI 서버 응답에 Progress 정보가 포함되어 있나요?

**A: 아니요.**

콜백 페이로드에는 **최종 결과**만 포함됩니다:
```json
{
  "message_type": "DOCUMENT_ANALYSIS_RESULT",
  "status": "SUCCESS",
  "sections": [...],
  "characters": [...],
  ...
}
```

---

## 3. Automatic Merge Trigger

### Q3-1: "모든 챕터 분석 완료"를 어떻게 판단해야 하나요?

**A: Spring에서 판단해야 합니다.**

**권장 로직**:
```java
boolean isAllCompleted = documentRepository
    .countByProjectIdAndStatusNot(projectId, "COMPLETED") == 0;

if (isAllCompleted) {
    rabbitTemplate.convertAndSend("global_merge_queue", mergeMessage);
}
```

- AI는 **각 문서 완료**만 알립니다 (`DOCUMENT_ANALYSIS_RESULT`).
- AI는 "프로젝트 전체 완료" 여부를 **알 수 없습니다** (프로젝트에 몇 개의 문서가 있는지 모르기 때문).
- Spring이 `AICallbackService`에서 콜백 수신 시 위 로직을 실행하면 됩니다.

### Q3-2: 새 챕터 추가 시 Global Merge를 다시 수행해야 하나요?

**A: 권장합니다.**

- 새 챕터에서 추출된 캐릭터가 기존 캐릭터와 중복될 수 있습니다.
- Global Merge는 **멱등성(Idempotent)**을 보장하므로, 여러 번 실행해도 안전합니다.
- 단, 성능을 위해 "마지막 Merge 이후 변경된 캐릭터만" 대상으로 하는 최적화를 고려할 수 있습니다 (향후).

---

## 4. 에러 핸들링 (DLQ, Retry)

### Q4-1: AI 콜백 실패 시 누가 재시도하나요?

**A: 현재는 재시도 로직이 없습니다.**

| 실패 케이스 | 현재 동작 | 권장 개선 |
|---|---|---|
| **AI 내부 LLM 호출 실패** | `tenacity` 라이브러리로 자동 재시도 (최대 3회) | ✅ 구현됨 |
| **AI → Spring Callback HTTP 실패** | 실패 로그만 남김, 재시도 없음 | ⚠️ 개선 필요 |
| **RabbitMQ 메시지 처리 실패** | `basic_nack` → 메시지 큐에 다시 들어감 | ✅ 기본 동작 |

**권장 개선 (AI 측)**:
- Callback 실패 시 3회 재시도 후 실패 상태를 DB에 기록하거나 DLQ로 전송

### Q4-2: 현재 RabbitMQ에 DLQ가 설정되어 있나요?

**A: 현재 명시적으로 설정되어 있지 않습니다.**

- Python Consumer는 `aio_pika`로 Queue를 Consume하지만, DLQ 설정은 **Queue 선언 시** RabbitMQ 측에서 해야 합니다.
- Spring 또는 RabbitMQ Admin에서 다음 설정 추가를 권장합니다:
  ```
  x-dead-letter-exchange: ""
  x-dead-letter-routing-key: "document_analysis_queue.dlq"
  ```

---

## ✅ 테스트 전 최종 확인 사항 (AI 팀 확인)

| 항목 | AI 팀 확인 결과 |
|---|---|
| 긴 텍스트 테스트 데이터 필요 | ⚠️ Spring 팀에서 준비 필요 (최소 10,000자 이상 권장) |
| AI Consumer 정상 작동 | ✅ 확인 완료 (로그: `Document Analysis Consumer started`) |

---
추가 질문이 있으시면 말씀해 주세요!
