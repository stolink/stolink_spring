# 종단간(End-to-End) 연동 플로우: 문서 분석 및 글로벌 병합

이 문서는 **Spring Backend**와 **AI Server** 간의 데이터 처리 전체 수명 주기를 설명합니다.

## 🔄 시퀀스 다이어그램 (Sequence Diagram)

```mermaid
sequenceDiagram
    autonumber
    actor User as 사용자
    participant Spring as Spring Backend
    participant QueueA as RabbitMQ (Analysis)
    participant QueueM as RabbitMQ (Merge)
    participant AI as AI Server
    participant DB as Postgres/Neo4j

    %% Scenario A: 문서 분석
    rect rgb(230, 240, 255)
    note right of User: Scenario A: 문서 분석 (Document Analysis)
    User->>Spring: 1. 문서 업로드 및 분석 요청 (POST /api/ai/analyze)
    Spring->>QueueA: 2. 분석 작업 발행 (DOCUMENT_ANALYSIS)
    QueueA->>AI: 3. 작업 수신 (Consume)
    activate AI
    AI->>AI: 4. 청킹(Chunking) 및 임베딩 생성 (Titan V2)
    AI->>AI: 5. 엔티티 추출 (NER)
    AI->>Spring: 6. 콜백 전송 (POST /api/ai-callback)
    deactivate AI
    activate Spring
    Spring->>DB: 7. 섹션(Vector) 및 엔티티(Node) 저장
    Spring-->>User: 8. 알림 (분석 완료)
    deactivate Spring
    end

    %% Scenario B: 글로벌 병합
    rect rgb(255, 240, 230)
    note right of User: Scenario B: 글로벌 병합 (Global Merge)
    User->>Spring: 9. 글로벌 병합 트리거 (POST /api/project/{id}/merge)
    Spring->>QueueM: 10. 병합 요청 발행 (GLOBAL_MERGE)
    QueueM->>AI: 11. 요청 수신 (Consume)
    activate AI
    AI->>DB: 12. 모든 캐릭터 노드 조회
    AI->>AI: 13. 중복 식별 (Clustering)
    AI->>Spring: 14. 콜백 전송 (POST /api/ai-callback)
    deactivate AI
    activate Spring
    Spring->>DB: 15. 노드 병합 실행 (세부 내용 하단 참조)
    note right of DB: - 메인 속성 유지<br/>- 모든 관계(Relationship) 이동<br/>- 중복 노드 삭제
    Spring-->>User: 16. 알림 (병합 완료)
    deactivate Spring
    end
```

---

## 📝 상세 단계 (Detailed Steps)

### Phase 1: 문서 분석 (Scenario A)
1.  **요청 (Request)**: 사용자가 특정 문서(챕터)에 대한 분석을 요청합니다.
2.  **큐잉 (Queuing)**: Backend가 `document_analysis_queue`에 분석 작업을 보냅니다.
3.  **AI 처리 (AI Processing)**:
    *   **청킹 (Chunking)**: 텍스트를 의미 단위로 쪼갭니다 (Semantic Chunking).
    *   **임베딩 (Embedding)**: 텍스트 조각을 1024차원 벡터로 변환합니다.
    *   **추출 (Extraction)**: 등장인물, 장소, 사건 등을 찾아냅니다.
4.  **콜백 (Callback)**: AI가 분석 결과를 Backend로 다시 보냅니다.
5.  **저장 (Persistence)**:
    *   **PostgreSQL**: `Section` 테이블에 벡터 데이터와 함께 저장합니다.
    *   **Neo4j**: 추출된 모든 엔티티에 대해 새로운 노드를 생성합니다 (이 단계에서는 중복이 존재할 수 있음).

### Phase 2: 글로벌 병합 (Scenario B)
1.  **트리거 (Trigger)**: 사용자(또는 배치 작업)가 프로젝트 전체에 대한 병합을 요청합니다.
2.  **AI 분석 (AI Analysis)**:
    *   AI가 Phase 1에서 생성된 모든 캐릭터 노드를 읽어옵니다.
    *   이름 유사도와 문맥을 분석하여 중복 그룹을 식별합니다 (예: "해리", "해리 포터", "포터 군" → 동일 인물).
    *   **대표(Primary) ID**를 선정합니다.
3.  **병합 실행 (Merge Execution - Spring)**:
    *   AI로부터 `{ keep: primary_id, merge: [old_id1, old_id2] }` 형태의 리스트를 받습니다.
    *   **동작**: Neo4j의 `mergeNodes(primary, old)`를 호출합니다.
    *   **결과**: `old_id`가 가지고 있던 모든 관계(누가 누구를 만났고, 어디에 갔는지 등)가 `primary_id`로 옮겨집니다. 그래프가 깔끔하게 하나로 연결됩니다.
