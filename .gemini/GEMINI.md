# StoLink Backend Constitution v3.1

> Spring Boot 백엔드 프로젝트 헌법. AI 모델이 코드 품질을 일관되게 유지하기 위한 규칙.

## 프로젝트 개요

**StoLink Backend** - 작가용 AI 스토리 관리 플랫폼

- Spring Boot 3.4.1 + Java 21, PostgreSQL 16 + Neo4j 5.26, RabbitMQ 3.13
- 핵심 엔티티: User, Project, Document(재귀 트리), Character(Neo4j), Foreshadowing, AIJob

## 코딩 규칙

### MUST (필수)

- Controller → Service → Repository 레이어 분리
- Entity ↔ DTO 분리 (Entity 직접 노출 금지)
- `@Transactional` 범위 최소화, 읽기는 `readOnly=true`
- N+1 방지: `@EntityGraph` 또는 `fetch join`
- Lazy Loading 기본, 필요시 DTO Projection
- JSONB: `@Type(JsonBinaryType.class)`, Array: `@Type(ListArrayType.class)`
- Neo4j: 관계 그래프 전용, 깊이 제한 필수
- AI 작업: RabbitMQ 비동기, WebClient + Timeout + Circuit Breaker
- 응답: `ApiResponse<T>` 래퍼, 예외는 `GlobalExceptionHandler`
- 테스트: Service 단위테스트, Repository는 `@DataJpaTest`/Testcontainers

### MUST NOT (금지)

- `System.out.println` → SLF4J Logger
- `SELECT *`, 루프 내 DB 쿼리
- 트랜잭션 내 외부 API 호출
- main/develop 직접 push
- 하드코딩 credential

### 네이밍

- 패키지: 소문자 | 클래스: PascalCase | 메서드: camelCase (동사 시작)
- DTO: `CreateXxxRequest`, `XxxResponse` | 상수: UPPER_SNAKE_CASE

## Git 컨벤션

| 브랜치  | 용도      | PR 대상          |
| ------- | --------- | ---------------- |
| main    | 프로덕션  | hotfix/\*        |
| develop | 개발 통합 | feature/_, fix/_ |

**커밋**: `feat|fix|docs|refactor|perf|test|chore|db(scope): 설명`

## AI 코드 리뷰 기준

### 🔴 치명적 (즉시 수정)

N+1 | SQL Injection | 트랜잭션 내 외부 API | Entity 노출 | 동시성 이슈 | 메모리 누수

### ⚠️ 경고 (권장 수정)

SELECT \* | OFFSET 페이지네이션 | 과도한 트랜잭션 범위 | 루프 내 쿼리 | 인덱스 누락

### 💡 제안 (선택)

코드 스타일 | 리팩토링 | 캐시 적용

## 명령어

| 명령어                 | 설명        |
| ---------------------- | ----------- |
| `./gradlew bootRun`    | 개발 서버   |
| `./gradlew test`       | 테스트      |
| `docker-compose up -d` | DB 컨테이너 |

## DB 연결 (로컬)

- **PostgreSQL**: localhost:5432 / stolink / stolink123
- **Neo4j**: bolt://localhost:7687 / neo4j / stolink123
- **RabbitMQ**: localhost:5672 / guest / guest

## 참고 문서

API_SPEC.md | API_EXAMPLES.md | PROJECT_SPEC.md | README.md

---

_응답은 한국어로 작성_
