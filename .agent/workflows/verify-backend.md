---
description: StoLink 백엔드 시스템의 아키텍처 원칙 준수 여부와 데이터 무결성을 검증하기 위한 프로토콜
---

# CHECK_CODE_BACKEND.md (Server Verification Protocol)

> **문서 목적:** `CLAUDE.md` v3.0 헌법에 의거하여, StoLink 백엔드 시스템의 아키텍처 원칙 준수 여부와 데이터 무결성을 최종 승인(Sign-off)하기 위한 프로토콜입니다.
> **적용 시점:** 기능 구현 완료 후, PR 생성 직전, CI 파이프라인 통과 전
> **권한:** 수석 연구원 및 리드 개발자

---

## 1. 검증 철학 (Verification Philosophy)

**"StoLink의 백엔드는 단순한 API 서버가 아니라, '관계(Relation)'와 '서사(Narrative)'를 지탱하는 이중 뇌(Dual-Brain) 시스템이다."**

우리는 **RDBMS의 견고함**과 **Graph DB의 유연함**, 그리고 **비동기 메시징의 확장성**이 조화를 이루는지 검증합니다. `CLAUDE.md`에 명시된 기술 스택(Java 21, QueryDSL, WebFlux)을 벗어난 코드는 기능이 동작하더라도 '결함'으로 간주합니다.

---

## 2. 정밀 검증 프로세스 (The 5-Step Protocol)

### Phase 1. Java 21 & Spring Boot 3.4 아키텍처 검증

**목표:** 최신 모던 자바 스펙 준수 및 레이어 침범 방지

<check_list>

1. **Modern Java Syntax Compliance**

- **Record DTO:** 모든 DTO(`Request`, `Response`)는 불변성을 보장하는 `record` 타입으로 선언되었는가? (Lombok `@Data` 지양)
- **Pattern Matching:** `instanceof`나 `switch` 문 사용 시 Java 21의 Pattern Matching을 활용하여 간결성을 확보했는가?

2. **Layer Strictness (Controller → Service → Repository)**

- **Entity Isolation:** `Controller` 레벨에서 `Entity`가 파라미터나 리턴값으로 노출된 경우 즉시 리젝트. (반드시 `ApiResponse<DtoRecord>` 형태여야 함)
- **Service Transaction:** 비즈니스 로직이 포함된 모든 `public` 메서드에 `@Transactional`이 명시되었는가? (조회는 `readOnly = true`)

3. **Response Standardization**

- 모든 API 응답이 `global.common.ApiResponse<T>` 래퍼로 감싸져 있는가?
- 예외 발생 시 `GlobalExceptionHandler`를 거쳐 정의된 `ErrorCode`가 반환되는가?

</check_list>

### Phase 2. Polyglot Persistence 무결성 (Postgres + Neo4j)

**목표:** 데이터의 성격에 맞는 저장소 사용 및 동기화 전략 검증

<anti_pattern_detection>
**다음 위반 사항 발견 시 즉시 코드 폐기:**

1. **Graph Pollution (그래프 오염):**

- `Neo4j` 노드(`@Node`)에 텍스트 본문(Content)이나 대용량 메타데이터를 저장하려 했는가?
- _Rule:_ Neo4j에는 오직 `ID`, `Name`, `Type` 등 관계 탐색에 필요한 최소 데이터만 저장한다.

2. **Relational Complexity (관계형 복잡도):**

- `PostgreSQL`에서 3-hop 이상의 관계(`친구의 친구의 적`)를 `JOIN`이나 서브쿼리로 해결하려 했는가?
- _Rule:_ 복잡한 관계 질의는 반드시 `Spring Data Neo4j` 리포지토리를 통해 수행한다.

3. **Transaction Alignment:**

- Postgres와 Neo4j에 동시 쓰기가 발생하는 로직에서, 실패 시 데이터 불일치를 방지할 방어 로직(보상 트랜잭션 등)이 고려되었는가?

</anti_pattern_detection>

### Phase 3. 비동기 처리 및 외부 연동 (RabbitMQ & FastAPI)

**목표:** 시스템의 응답성(Responsiveness) 및 격리(Isolation) 보장

<check_list>

1. **RabbitMQ Interaction**

- AI 작업 요청(AIJob)은 반드시 `RabbitMQ` Producer를 통해 비동기로 발행되는가? (Controller에서 AI 서비스 동기 호출 금지)
- 메시지 발행(Publish)이 트랜잭션 커밋 **이후**에 실행되도록 보장되었는가? (`@TransactionalEventListener` 활용 권장)

2. **FastAPI Integration (WebFlux)**

- 외부 API 호출 시 `RestTemplate` 대신 `WebClient`(Non-blocking)를 사용했는가?
- 모든 외부 호출에 명시적인 `Timeout` 설정과 `Circuit Breaker`(Resilience4j)가 적용되었는가?

3. **JSON Compatibility**

- Postgres의 `JSONB` 컬럼과 RabbitMQ 메시지 페이로드가 `Jackson`을 통해 올바르게 직렬화/역직렬화되는가?

</check_list>

### Phase 4. 성능 최적화 (Query & Memory)

**목표:** N+1 문제 원천 차단 및 대용량 데이터 처리 안전장치

<check_list>

1. **JPA Performance**

- `@OneToMany` 컬렉션 조회 시 `Fetch Join` 또는 `@EntityGraph`가 적용되어 N+1 쿼리가 방지되었는가?
- 단순 조회 쿼리는 `QueryDSL`의 `Projections.constructor`를 사용하여 필요한 컬럼만 `Select` 했는가?

2. **Neo4j Optimization**

- 그래프 탐색 쿼리(Cypher)에 깊이 제한(예: `-[*1..3]-`)이 설정되어 있는가? (전체 그래프 탐색 금지)
- 자주 조회되는 노드 속성(User ID, Character Name)에 인덱스가 생성되어 있는가?

3. **Document Structure (Scrivener Style)**

- 문서(`Document`) 트리 구조 조회 시 재귀 호출 대신 `Recursive CTE`나 최적화된 경로 쿼리를 사용했는가?

</check_list>

### Phase 5. 안정성 및 테스트 (Reliability)

**목표:** "작동한다"를 넘어 "증명한다"

<check_list>

1. **Testing Strategy**

- `Service` 테스트는 `Mockito`를 사용한 단위 테스트인가?
- `Repository` 테스트는 `@DataJpaTest` 또는 `Testcontainers`를 사용한 통합 테스트인가? (실제 DB 환경 검증)
- _Critical:_ 테스트 코드 내에 `System.out.println`이 없는가?

2. **Logging Standard**

- 로그는 `SLF4J`를 사용하며, 에러 로그에는 `Stack Trace`뿐만 아니라 요청 컨텍스트(User ID, Project ID)가 포함되어 있는가?

</check_list>

---

## 3. 검증 보고서 포맷 (Report Format)

백엔드 PR 작성 시 Description에 아래 리포트를 포함해야 합니다.

```markdown
# 🛡️ StoLink Backend Verification Report

## 1. Architecture Compliance (CLAUDE.md v3.0)

- [ ] **Java 21**: Record DTO used? (Yes/No)
- [ ] **Layering**: Controller -> Service -> Repo strict? (Yes/No)
- [ ] **Response**: Wrapped in `ApiResponse<T>`? (Yes/No)

## 2. Database Integrity (Polyglot)

- [ ] **PostgreSQL**: N+1 checked (Fetch Join/EntityGraph)? (Yes/No)
- [ ] **Neo4j**: Only topology/relations stored? (No content blob)
- [ ] **QueryDSL**: Used for complex dynamic queries? (Yes/No)

## 3. Async & Integration

- [ ] **RabbitMQ**: AI tasks are async? (Yes/No)
- [ ] **WebClient**: Used for FastAPI calls with Timeout? (Yes/No)

## 4. Quality & Tests

- [ ] **Tests**: Service(Unit) + Repo(Intg) Passed? (Yes/No)
- [ ] **Logging**: SLF4J used (No System.out)? (Yes/No)

## 5. Specific Issues Solved

- (이번 변경으로 해결된 성능 이슈나 버그를 기술)
```

---

## 4. 실행 가이드 (Execution Commands)

에이전트 및 개발자는 다음 명령어를 통해 검증을 수행합니다.

> **Note:** 아래 명령어들은 개념적인 검증 절차를 의미합니다. 실제 스크립트가 존재하지 않는 경우, 에이전트는 위에 명시된 체크리스트를 기반으로 수동 검증(Manual Verification)을 수행해야 합니다.

- **`verify:arch`**: Java 버전, 패키지 구조, DTO/Entity 분리 여부 정적 분석
- **`verify:db`**: JPA N+1 탐지 및 Neo4j 쿼리 깊이 제한 확인
- **`verify:async`**: RabbitMQ 설정 및 WebClient 타임아웃 설정 확인
- **`verify:all`**: 전체 테스트(`test`) 실행 및 정적 분석 리포트 생성

---

## 5. 핵심 원칙 (Golden Rules from CLAUDE.md)

1. **Neo4j는 지도(Map)이고, Postgres는 도서관(Library)이다.** 지도를 도서관에 넣거나, 책을 지도에 그리지 마라.
2. **AI는 비서(Assistant)이지, 서버의 주인(Master)이 아니다.** AI 응답이 늦다고 서버가 멈춰선 안 된다. (비동기 필수)
3. **코드는 읽기 위해 존재한다.** Java 21의 모던 기능을 활용하여 간결하고 명확하게 작성하라.
4. **믿지 마라, 검증하라.** 모든 입력값은 `Jakarta Validation`으로, 모든 쿼리는 `Testcontainers`로 검증하라.
