# Spring 팀 질문사항 - AI Backend 연동 관련 (Request 2 회신)

> **작성일**: 2026-01-02  
> **작성자**: Spring Backend 팀  
> **대상 문서**: `docs/AI_TO_SPRING_REQUESTS/SPRING_TEAM_REQUEST_2.md`

---

## ❓ 질문 및 확인 사항

### 1. message_type 값 통일 필요

현재 Spring과 Python 사이에 `message_type` 값이 불일치합니다:

| 구분 | Spring (발행) | Python (Callback) |
|------|--------------|-------------------|
| 문서 분석 | `DOCUMENT_ANALYSIS` | `DOCUMENT_ANALYSIS_RESULT` |
| 글로벌 병합 | `GLOBAL_MERGE` | `GLOBAL_MERGE_RESULT` |

**질문**: 
- Callback 수신 시 `DOCUMENT_ANALYSIS_RESULT`와 `GLOBAL_MERGE_RESULT`로 분기 처리하면 되는 것인지 확인 부탁드립니다.
- 기존 `FULL_DOCUMENT` 분석 콜백은 어떤 `message_type`을 사용하나요? (기존 `AnalysisCallbackDTO` 처리용)

---

### 2. pgvector 사용 여부

현재 `Section.embeddingJson`은 `TEXT` 타입으로 JSON 문자열 저장 방식입니다.

**질문**:
- 의미 검색 기능(예: "이 장면과 유사한 다른 장면 찾기")이 필요한가요?
- 필요하다면 `pgvector` 확장 설치 및 마이그레이션을 진행하겠습니다.
- 단순 저장/조회만 필요하면 현재 JSON 문자열 방식을 유지하겠습니다.

---

### 3. snake_case 변환 방식 선택

두 가지 방식 중 선호하는 방식이 있나요?

**방식 A**: 전역 Jackson 설정
```java
mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
```
- 장점: 모든 API에 일괄 적용
- 단점: 기존 camelCase API에 영향 가능

**방식 B**: DTO별 `@JsonProperty` 명시
```java
@JsonProperty("document_id")
private String documentId;
```
- 장점: AI 관련 DTO에만 선택적 적용
- 단점: 코드량 증가

**현재 계획**: 방식 B (DTO별 명시)로 진행 예정입니다. 이견 있으시면 말씀해 주세요.

---

### 4. 테스트 환경 관련

연동 테스트(L194-204) 진행 시:

**질문**:
- Python 서버가 로컬에서 실행 중인가요? 아니면 Docker Compose 환경인가요?
- Spring에서 테스트 메시지를 발행할 때 사용할 테스트 Project/Document ID가 있나요?
- 테스트용 RabbitMQ 연결 정보(host, port, credentials)를 공유해 주세요.

---

## ✅ 진행 예정 사항

위 질문에 대한 답변을 받은 후 아래 작업을 진행하겠습니다:

1. `AICallbackController`에 `message_type` 분기 처리 추가
2. DTO에 `@JsonProperty` 어노테이션 추가 (snake_case 지원)
3. 연동 테스트 진행

---

> 회신 부탁드립니다! 🙏
