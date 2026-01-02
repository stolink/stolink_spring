# Spring 팀 → AI 팀 확인 요청

> **작성일**: 2026-01-02
> **참고**: `docs/AI_TO_SPRING_REQUESTS/SPRING_IMPLEMENTATION_CHECKLIST.md`

---

## 📋 미구현 항목 관련 확인 사항

### 1. 챕터 분할 로직 (Cascading Fallback)

> 체크리스트에서 **구현 여부 확인 필요**라고 표시되어 있습니다.

**질문**:
1.  이 로직은 **Spring Backend**에서 구현해야 하나요, 아니면 **AI Server**에서 처리하나요?
    *   현재 흐름상 Spring이 문서 **원본 텍스트**를 AI에게 통째로 넘기면, AI가 Semantic Chunking을 수행하는 것으로 이해하고 있습니다.
    *   "챕터 분할"은 문서를 업로드할 때 사용자가 직접 챕터를 나누는 것인지, 아니면 시스템이 자동으로 나누는 것인지 확인이 필요합니다.
2.  만약 Spring에서 구현해야 한다면, 분할된 챕터마다 각각 별도의 분석 요청(`document_analysis_queue`)을 보내야 하나요?

---

### 2. SSE 실시간 상태 알림

> `GET /api/project/{id}/status/stream` 엔드포인트가 필요하다고 명시되어 있습니다.

**질문**:
1.  AI 서버에서 **Progress Update**(예: `30% 완료`, `청킹 중`, `임베딩 생성 중`)를 실시간으로 보내주나요?
    *   만약 그렇다면, 어떤 방식으로 전달받나요? (RabbitMQ? HTTP Callback?)
    *   아니면 Spring이 단순히 DB의 `status` 컬럼을 폴링하는 방식인가요?
2.  현재 AI 서버의 응답에 Progress 정보가 포함되어 있나요?

---

### 3. Automatic Merge Trigger

> 현재는 Manual API만 구현되어 있습니다.

**질문**:
1.  자동 트리거를 위해 "모든 챕터 분석 완료"를 어떻게 판단해야 하나요?
    *   프로젝트에 속한 모든 `Document`의 `analysisStatus`가 `COMPLETED`일 때?
    *   아니면 AI 서버에서 "프로젝트 분석 완료" 신호를 별도로 보내주나요?
2.  사용자가 나중에 새 챕터를 추가하면 Global Merge를 다시 수행해야 하나요?

---

### 4. 에러 핸들링 (DLQ, Retry)

**질문**:
1.  AI 서버 콜백 실패 시 AI 측에서 재시도를 하나요, 아니면 Spring이 DLQ를 모니터링하고 재처리해야 하나요?
2.  현재 RabbitMQ에 DLQ가 설정되어 있나요? (예: `document_analysis_queue.dlq`)

---

## ✅ 테스트 전 최종 확인 사항 (Spring 팀 자체 점검 결과)

| 항목 | 확인 결과 |
|---|---|
| `callback_url`이 `http://stolink-backend:8080/api/ai-callback` 형식인가? | ✅ 확인 완료 (`docker-compose.spring.yml`) |
| `AnalysisTaskDTO`에 `message_type` 필드가 포함되어 있는가? | ✅ 확인 완료 (`DOCUMENT_ANALYSIS` default) |
| 테스트용 문서 데이터(긴 텍스트)가 DB에 준비되어 있는가? | ⚠️ 짧은 테스트 데이터만 있음 (실제 긴 텍스트 필요) |
| RabbitMQ vhost `stolink`에 권한이 있는가? | ✅ 확인 완료 (`stolink` user) |

---

위 내용에 대해 답변 부탁드립니다. 감사합니다!
