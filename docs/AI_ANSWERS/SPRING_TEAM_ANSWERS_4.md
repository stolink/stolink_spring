# Spring 팀 질문에 대한 답변 (Answers to Request #4)

> **작성일**: 2026-01-02  
> **주제**: Integration Guide #2에 대한 문의 사항 답변

---

## 1. Regarding Callback URL Mismatch

### Q: URL 수정 및 Hostname 변경 (`/api/ai-callback`, `stolink-backend`)
**A: 네, 맞습니다.**
- **Endpoint**: 기존 `/api/internal/ai/analysis/callback` 대신 **`/api/ai-callback`**을 사용해야 새로운 `DOCUMENT_ANALYSIS_RESULT` 처리가 가능합니다.
- **Hostname**: Docker Network 내부 통신의 신뢰성을 위해 `host.docker.internal` 대신 **`stolink-backend`**(Docker Compose 서비스명)를 사용하는 것을 적극 권장합니다.
- **결론**: `AIController`에서 콜백 URL 생성 시 `http://stolink-backend:8080/api/ai-callback` 형태가 되도록 수정 부탁드립니다.

---

## 2. Regarding Trigger Payload Structure

### Q: `AnalysisTaskDTO` 수정 필요 여부 (`message_type` 포함)
**A: 네, Payload에 반드시 포함되어야 합니다.**
- Python Consumer(`DocumentAnalysisConsumer`)는 메시지의 유효성을 검증하기 위해 Pydantic 모델(`DocumentAnalysisMessage`)을 사용합니다.
- 이 모델은 `message_type="DOCUMENT_ANALYSIS"` 필드를 기본값으로 갖지만, **입력 데이터에 해당 필드가 명시되어 있는 것이 안전**합니다.
- **권장**: `RabbitMQProducerService`에서 DTO를 JSON으로 변환하기 직전에 `message_type` 필드를 주입하거나, DTO 자체에 해당 필드를 추가해 주세요.

---

## 3. Regarding Global Merge Trigger

### Q: Global Merge 트리거 시점 (자동 vs 수동)
**A: 수동 트리거(Manual Trigger) API 구현을 먼저 권장합니다.**

1.  **Manual Trigger (필수)**:
    - `POST /api/project/{projectId}/merge`
    - 개발자가 원할 때 언제든 병합을 시도해볼 수 있어 테스트 및 디버깅에 필수적입니다.
    - AI Controller에 해당 엔드포인트를 구현하여 `global_merge_queue`로 메시지를 발행해 주세요.

2.  **Automatic Trigger (권장)**:
    - 비즈니스 로직 상 "마지막 챕터 분석 완료 시" 또는 "사용자가 '전체 분석 완료' 버튼 클릭 시" 수행하도록 구현합니다.
    - `AICallbackService`에서 챕터 완료 로직 내에 조건부로 호출할 수 있습니다.

**결론**: 우선 **Manual Trigger API**를 구현하여 연동 테스트(Scenario B)를 진행하는 것이 좋습니다.

---
감사합니다.
