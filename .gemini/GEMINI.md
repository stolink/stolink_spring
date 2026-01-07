# CLAUDE.md - StoLink Backend Project Constitution

> 이 문서는 AI 모델이 백엔드 프로젝트 컨텍스트를 이해하고, 코드 품질을 일관되게 유지하기 위한 **프로젝트 헌법(Constitution)**입니다.

**버전:** 3.0
**최종 수정:** 2025년 12월 26일
**문서 상태:** 활성

---

<project_info>
<description>
StoLink Backend - 작가용 AI 기반 스토리 관리 플랫폼 백엔드
Spring Boot 기반 REST API 서버로, FastAPI AI 서비스와 RabbitMQ를 통해 연동
복선 관리, 캐릭터 관계도(Neo4j), 세계관 설정, AI 기반 일관성 체크를 지원
</description>

<tech_stack>

<!-- 2025.12.26 기준 실제 버전 -->

- **Language**: Java 21 (LTS)
- **Framework**: Spring Boot 3.4.1, Spring Data JPA, Spring Data Neo4j
- **ORM**: Hibernate 6.x, QueryDSL 5.1.0
- **Database**:
  - PostgreSQL 16 (메인 RDBMS, JSONB 활용)
  - Neo4j 5.26 (캐릭터 관계 그래프)
- **Message Queue**: RabbitMQ 3.13 (AI 작업 비동기 처리)
- **AI Integration**: FastAPI 연동 (WebFlux 기반 HTTP Client)
- **Validation**: Jakarta Validation (Bean Validation 3.0)
- **JSON**: Jackson Databind, Hypersistence Utils (JSONB 타입 지원)
- **Utility**: Lombok 1.18.36, Apache Commons Lang3, Guava 33
- **Build**: Gradle 8.x
- **Container**: Docker, Docker Compose

</tech_stack>

<core_entities>

<!-- com.stolink.backend.domain 기준 -->

- **User**: 사용자 (email, nickname, avatar)
- **Project**: 작품 프로젝트 (User 소유, stats 통계 포함)
- **Document**: 폴더(folder) 또는 텍스트(text) - Scrivener 스타일 재귀 구조 ⭐ 핵심
- **Character**: 캐릭터 (extras JsonNode로 동적 속성, Neo4j 연동)
- **CharacterRelationship**: 캐릭터 관계 (Neo4j 그래프 엣지)
- **Foreshadowing**: 복선 (tag, status, appearances 배열)
- **AIJob**: AI 작업 (RabbitMQ 비동기 처리)

</core_entities>
</project_info>

---

<coding_rules>
<java>

- MUST: Java 21 기능 활용 (Record, Pattern Matching, Virtual Thread 고려)
- MUST: Lombok 사용 시 `@Builder`, `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 패턴 준수
- MUST: 불변 객체 지향 - DTO는 Record 또는 `@Value` 사용 권장
- MUST: 메서드/클래스당 100줄 이내 유지
- SHOULD: Optional 반환 시 `orElseThrow()` 보다 명시적 예외 처리
- MUST NOT: Primitive type의 null 반환 (`int` 대신 `Integer` 사용 시 null 체크)
- MUST NOT: `System.out.println` 사용 금지 - SLF4J Logger 사용
- MUST NOT: Magic Number/String 직접 사용 - 상수 또는 Enum 정의
  </java>

<spring>
<!-- Spring Boot 3.4.x 특성 반영 -->
- MUST: Controller → Service → Repository 레이어 분리 엄수
- MUST: DTO와 Entity 분리 (Entity 직접 노출 금지)
- MUST: `@Transactional` 범위 최소화 (읽기 전용은 `readOnly = true`)
- MUST: 예외는 `@RestControllerAdvice`에서 전역 처리
- SHOULD: 복잡한 쿼리는 QueryDSL 사용 (Native Query 지양)
- SHOULD: 응답 형식 통일 - `ApiResponse<T>` 래퍼 사용
- MUST NOT: Controller에서 비즈니스 로직 구현
- MUST NOT: Repository에서 직접 HTTP 응답 반환
- MUST NOT: 엔티티에서 양방향 연관관계 무분별 사용 (N+1 주의)
</spring>

<jpa_hibernate>

<!-- Spring Data JPA + Hibernate 6.x -->

- MUST: Lazy Loading 기본 전략 (@ManyToOne, @OneToMany 등)
- MUST: N+1 문제 방지 - `@EntityGraph` 또는 `fetch join` 사용
- MUST: Batch Insert/Update 활용 (`hibernate.jdbc.batch_size` 설정)
- SHOULD: DTO Projection 활용으로 필요한 컬럼만 조회
- SHOULD: `@Version` 낙관적 락 적용 (동시성 이슈 방지)
- MUST NOT: Open Session In View 안티패턴 (비활성화 확인)
- MUST NOT: Entity에서 equals/hashCode 잘못 구현 (ID 기반 필수)
- MUST NOT: `CascadeType.ALL` 무분별 사용
  </jpa_hibernate>

<neo4j>
<!-- Spring Data Neo4j + Neo4j 5.x -->
- MUST: 캐릭터 관계 그래프 전용 (관계형 데이터는 PostgreSQL)
- MUST: `@Node`, `@Relationship` 어노테이션 올바르게 사용
- MUST: Cypher 쿼리 최적화 - 인덱스 활용
- SHOULD: 깊이 제한 (`depth` 파라미터) 설정으로 과도한 탐색 방지
- MUST NOT: 대량 데이터 일괄 조회 (페이지네이션 적용)
</neo4j>

<postgresql>
<!-- PostgreSQL 16 + Hypersistence Utils -->
- MUST: JSONB 타입은 `@Type(JsonBinaryType.class)` 적용
- MUST: Array 타입은 `@Type(ListArrayType.class)` 적용
- MUST: 인덱스 전략 수립 (자주 조회되는 컬럼, JSONB 경로)
- SHOULD: 대용량 데이터는 파티셔닝 고려
- SHOULD: EXPLAIN ANALYZE로 쿼리 플랜 검증
- MUST NOT: SELECT * 사용 (필요한 컬럼만 조회)
- MUST NOT: OFFSET 기반 페이지네이션 (대량 데이터 시 Keyset 사용)
</postgresql>

<rabbitmq_async>

<!-- RabbitMQ + Spring AMQP -->

- MUST: AI 작업은 RabbitMQ로 비동기 처리
- MUST: 메시지 직렬화는 Jackson JSON 사용
- MUST: Dead Letter Queue 설정으로 실패 메시지 관리
- SHOULD: 재시도 정책 설정 (exponential backoff)
- SHOULD: 멱등성 보장 - 중복 메시지 처리 대비
- MUST NOT: 메시지 핸들러에서 장시간 블로킹
- MUST NOT: 트랜잭션과 메시지 발행 순서 혼동 (발행은 트랜잭션 커밋 후)
  </rabbitmq_async>

<fastapi_integration>

<!-- FastAPI AI 서비스 연동 -->

- MUST: WebFlux WebClient 사용 (RestTemplate 지양)
- MUST: 타임아웃 설정 (connection, read, write)
- MUST: Circuit Breaker 패턴 적용 (Resilience4j 권장)
- SHOULD: 요청/응답 DTO 버전 관리
- SHOULD: 비동기 콜백 시 `@Async` 또는 CompletableFuture 활용
- MUST NOT: 동기 블로킹 호출로 스레드 고갈
- MUST NOT: AI 서비스 장애가 전체 시스템 장애로 전파
  </fastapi_integration>

<performance>
<!-- 데이터 처리 성능 최적화 -->
- MUST: Connection Pool 적정 크기 설정 (HikariCP)
- MUST: 2차 캐시 활용 (Hibernate L2 Cache, Redis 가능)
- MUST: 벌크 연산 시 `@Modifying` + `clearAutomatically = true`
- SHOULD: 응답 압축 (GZIP) 활성화
- SHOULD: 페이지네이션 기본 적용 (limit 20)
- SHOULD: 조회 API는 DTO Projection으로 최적화
- MUST NOT: 루프 안에서 DB 쿼리 실행 (Batch 쿼리로 변환)
- MUST NOT: 대용량 데이터 메모리 로딩 (Stream/Cursor 사용)
</performance>

<naming>
- 패키지: 소문자 (예: `com.stolink.backend.domain.document`)
- 클래스: PascalCase (예: `DocumentService`)
- 메서드: camelCase, 동사로 시작 (예: `findByProjectId`, `createDocument`)
- 인터페이스: I 접두사 지양 (예: `DocumentRepository`, not `IDocumentRepository`)
- DTO: 용도 명시 (예: `CreateDocumentRequest`, `DocumentResponse`)
- Entity: 테이블명과 일치, 단수형 (예: `Document`, `Project`)
- 상수: UPPER_SNAKE_CASE (예: `DEFAULT_PAGE_SIZE`)
- Enum: PascalCase, 값은 UPPER_SNAKE_CASE
</naming>

<testing>
- MUST: Service 레이어 단위 테스트 필수
- MUST: `@DataJpaTest`로 Repository 테스트
- SHOULD: `@WebMvcTest`로 Controller 테스트
- SHOULD: Testcontainers로 통합 테스트 (PostgreSQL, Neo4j)
- MUST NOT: 프로덕션 DB에 테스트 수행
- MUST NOT: 테스트 간 상태 공유 (격리 필수)
</testing>
</coding_rules>

---

<restrictions>
<!-- 이것만은 절대 하지 마 (Negative Constraints) -->

🔴 **MUST NOT (절대 금지)**:

- Entity 직접 API 응답으로 반환 (DTO 변환 필수)
- `System.out.println` 로깅 (SLF4J Logger 사용)
- Controller에서 비즈니스 로직 구현
- N+1 쿼리 발생 (fetch join 또는 @EntityGraph)
- SELECT \* 쿼리 사용
- 루프 내 DB 쿼리 실행
- 트랜잭션 범위 내 외부 API 호출
- main/develop 브랜치에 직접 push
- PR 없이 main에 머지
- 프로덕션 DB 직접 조작
- 하드코딩된 credential

⚠️ **SHOULD NOT (지양)**:

- 500줄 이상의 단일 파일
- Native Query 사용 (QueryDSL 우선)
- CascadeType.ALL 무분별 사용
- 양방향 연관관계 과도한 사용
- OFFSET 기반 대용량 페이지네이션
- 동기 블로킹 외부 API 호출
- 테스트 없는 코드 커밋
  </restrictions>

---

<workflow_protocol>

<!-- AI 모델이 따라야 할 단계별 프로토콜 -->

1. **Analyze (분석)**

   - 사용자 요청을 파악하고 관련 파일 경로 확인
   - 기존 코드베이스에서 유사한 패턴 검색
   - domain/{도메인}/service, repository 에서 기존 로직 확인
   - API_SPEC.md와의 호환성 확인

2. **Plan (계획 수립)**

   - 변경 계획을 단계별로 수립
   - 영향 받는 Entity, DTO, Service, Repository, Controller 나열
   - 데이터베이스 스키마 변경 필요 여부 확인
   - N+1 문제, 트랜잭션 범위, 동시성 이슈 사전 검토

3. **Implement (구현)**

   - 계획에 따라 코드 작성
   - Entity → DTO → Repository → Service → Controller 순서
   - 예외 처리 GlobalExceptionHandler에 추가
   - 기존 API 응답 형식(ApiResponse) 준수

4. **Verify (검증)**
   - 컴파일 에러 확인 (`./gradlew compileJava`)
   - 테스트 실행 (`./gradlew test`)
   - API 동작 확인 (`./gradlew bootRun` + curl/Postman 테스트)
   - 쿼리 로그 확인 (N+1 체크)
     </workflow_protocol>

---

<branch_strategy>

<!-- 3-Layer 브랜치 전략 -->

| 브랜치      | 용도      | 직접 Push | PR 대상          |
| ----------- | --------- | --------- | ---------------- |
| `main`      | 프로덕션  | ❌ 금지   | hotfix/\*        |
| `develop`   | 개발 통합 | ❌ 금지   | feature/_, fix/_ |
| `feature/*` | 기능 개발 | ✅ 허용   | → develop        |
| `fix/*`     | 버그 수정 | ✅ 허용   | → develop        |
| `hotfix/*`  | 긴급 수정 | ✅ 허용   | → main           |

**상세 가이드**: [GIT_STRATEGY.md](GIT_STRATEGY.md) (있을 경우)
</branch_strategy>

---

<commit_convention>

<!-- Conventional Commits -->

```
feat: 새 기능 추가
fix: 버그 수정
docs: 문서 변경
style: 코드 포맷팅 (동작 변화 X)
refactor: 리팩토링 (동작 변화 X)
perf: 성능 개선
test: 테스트 추가
chore: 빌드, 설정, 의존성 변경
ci: CI/CD 설정 변경
hotfix: 긴급 수정
db: 데이터베이스 스키마/마이그레이션 변경
```

**예시**:

- `feat(document): 문서 일괄 순서 변경 API 추가`
- `fix(character): Neo4j 관계 삭제 시 NPE 수정`
- `perf(project): 프로젝트 목록 조회 N+1 해결`
- `db(user): users 테이블 nickname 컬럼 인덱스 추가`
  </commit_convention>

---

<ai_code_review>

<!--
  이 섹션은 GitHub Actions의 ai-review.yml 워크플로우에서 사용됩니다.
  수정 시 워크플로우에도 영향을 미칩니다.
-->

## AI 코드 리뷰어 페르소나

당신은 **StoLink 백엔드 프로젝트의 시니어 백엔드 개발자이자 DBA 전문가**입니다.

### 프로젝트 컨텍스트

- Spring Boot 3.4.1 기반 REST API 서버
- PostgreSQL 16 + Neo4j 5.26 이중 데이터베이스 구조
- FastAPI AI 서비스와 RabbitMQ 비동기 연동
- 기술 스택: Java 21, Spring Boot 3.4.1, Spring Data JPA, QueryDSL, Hibernate 6.x
- 핵심 엔티티: User, Project, Document, Character, CharacterRelationship

### 리뷰 우선순위

1. **치명적** (🔴): 런타임 에러, SQL Injection, N+1 쿼리, 데이터 정합성 이슈
2. **경고** (⚠️): 성능 이슈, 트랜잭션 범위 문제, 안티패턴
3. **제안** (💡): 코드 스타일, 리팩토링 (선택사항)

## 🔴 치명적 (즉시 수정)

- N+1 쿼리 발생 (EXPLAIN ANALYZE 확인)
- SQL Injection 취약점
- 트랜잭션 내 외부 API 호출
- Entity 직접 API 응답 노출 (민감 정보 유출)
- 데드락 가능성 있는 코드
- 동시성 이슈 (낙관적/비관적 락 미적용)
- 메모리 누수 (Stream close 누락, 대용량 데이터 로딩)
- 캐릭터 관계성 준수: Neo4j 관계 타입은 반드시 `friend`, `lover`, `enemy` 중 하나여야 함
- CharacterRelationship의 JSON 매핑: `source`, `target`이 ID 문자열로 출력되는지 확인
- 인증/인가 우회 가능성

## ⚠️ 경고 (권장 수정)

- SELECT \* 사용 (필요한 컬럼만 조회)
- OFFSET 기반 대용량 페이지네이션
- 트랜잭션 범위 과도하게 넓음
- 루프 내 DB 쿼리 실행
- 불필요한 Lazy Loading (DTO Projection 권장)
- 인덱스 누락 가능성 (WHERE 조건 분석)
- 예외 처리 누락
- 500줄 이상의 단일 파일

## 💡 제안 (선택)

- 코드 스타일 개선
- 리팩토링 기회
- 더 나은 패턴 제안
- 캐시 적용 가능 지점

## 출력 규칙

1. 🔴 치명적, ⚠️ 경고가 하나라도 있으면 해당 섹션 출력
2. 🔴, ⚠️가 없으면 '✅ 코드 리뷰 통과 - 수정 필요 사항 없음' 출력
3. 💡 제안은 선택사항이므로 '수정 필요'로 취급하지 않음

## 출력 형식

```
### 🔴 치명적 (N건)
**파일:라인** - 이슈 제목
- 문제: 설명
- 개선: 코드 예시

### ⚠️ 경고 (N건)
**파일:라인** - 이슈 제목
> 설명

---
💡 **참고 제안** (선택사항)
- 제안 내용
```

</ai_code_review>

---

<file_structure>

<!-- 2025.12.26 기준 실제 구조 -->

```
stolink_spring/
├── src/main/java/com/stolink/backend/
│   ├── BackendApplication.java              # Spring Boot 메인 클래스
│   ├── domain/                              # 도메인별 모듈
│   │   ├── user/                            # 사용자 도메인
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── repository/
│   │   │   ├── entity/
│   │   │   └── dto/
│   │   ├── project/                         # 프로젝트 도메인
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── repository/
│   │   │   ├── entity/
│   │   │   └── dto/
│   │   ├── document/                        # 문서 도메인 ⭐ 핵심
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── repository/
│   │   │   ├── entity/
│   │   │   └── dto/
│   │   ├── character/                       # 캐릭터 도메인 (Neo4j 연동)
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── repository/                  # Neo4jRepository
│   │   │   ├── entity/                      # @Node 엔티티
│   │   │   └── dto/
│   │   ├── foreshadowing/                   # 복선 도메인
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── repository/
│   │   │   ├── entity/
│   │   │   └── dto/
│   │   └── ai/                              # AI 연동 도메인
│   │       ├── controller/                  # 콜백 엔드포인트
│   │       ├── service/                     # AI 요청 처리
│   │       ├── message/                     # RabbitMQ 메시지 핸들러
│   │       └── dto/
│   └── global/                              # 전역 설정
│       ├── common/                          # 공통 응답, 예외
│       │   ├── ApiResponse.java
│       │   ├── GlobalExceptionHandler.java
│       │   └── ErrorCode.java
│       ├── config/                          # 설정 클래스
│       │   ├── WebConfig.java
│       │   ├── RabbitMQConfig.java
│       │   └── Neo4jConfig.java
│       └── util/                            # 유틸리티
│           └── HtmlUtils.java               # HTML → 텍스트 변환 (wordCount)
├── src/main/resources/
│   └── application.yml                      # 설정 파일
├── build.gradle                             # Gradle 빌드 설정
├── docker-compose.yml                       # 개발 환경 (PostgreSQL, Neo4j, RabbitMQ)
├── Dockerfile                               # 프로덕션 컨테이너
└── *.md                                     # 문서
```

</file_structure>

---

<commands>
<!-- 자주 사용하는 명령어 -->

| 명령어                   | 설명                            |
| ------------------------ | ------------------------------- |
| `./gradlew bootRun`      | 개발 서버 시작 (localhost:8080) |
| `./gradlew build`        | 프로덕션 빌드                   |
| `./gradlew compileJava`  | 컴파일만 수행                   |
| `./gradlew test`         | 테스트 실행                     |
| `./gradlew clean build`  | 클린 빌드                       |
| `docker-compose up -d`   | 개발용 DB 컨테이너 시작         |
| `docker-compose down`    | 컨테이너 중지                   |
| `docker-compose logs -f` | 로그 확인                       |

</commands>

---

<database_config>

<!-- 개발 환경 데이터베이스 설정 -->

### PostgreSQL (docker-compose)

- **Host**: localhost:5432
- **Database**: stolink
- **User**: stolink
- **Password**: stolink123

### Neo4j (docker-compose)

- **Bolt**: bolt://localhost:7687
- **HTTP**: http://localhost:7474 (브라우저)
- **User**: neo4j
- **Password**: stolink123

### RabbitMQ (docker-compose)

- **AMQP**: localhost:5672
- **Management UI**: http://localhost:15672
- **User**: guest
- **Password**: guest

</database_config>

---

<reference_docs>

<!-- 참고 문서 (SSOT) -->

| 문서              | 내용                          |
| ----------------- | ----------------------------- |
| `API_SPEC.md`     | 백엔드 REST API 명세 (v1.0.0) |
| `API_EXAMPLES.md` | API 호출 예시                 |
| `PROJECT_SPEC.md` | 전체 프로젝트 기능 명세서     |
| `README.md`       | 프로젝트 개요 및 시작 가이드  |

</reference_docs>

---

<request_guidelines>

<!-- 요청 시 주의사항 -->

1. 새 API는 기존 응답 형식(`ApiResponse<T>`)과 호환성 확인
2. Entity 변경 시 기존 데이터 마이그레이션 계획 수립
3. 쿼리 추가 시 N+1 문제 사전 검토 (`@EntityGraph` 또는 fetch join)
4. AI 연동 로직은 RabbitMQ 비동기 처리 원칙 준수
5. 캐릭터 관계 관련은 Neo4j Repository 확인
6. 성능 민감한 API는 페이지네이션 필수 적용
7. 복잡한 조회는 QueryDSL Projection 활용
8. 응답은 한국어로 작성
   </request_guidelines>

---

## 버전 이력

| 버전 | 날짜       | 변경 내용                                                                                                |
| ---- | ---------- | -------------------------------------------------------------------------------------------------------- |
| 1.0  | 2024.12    | 최초 작성 (프론트엔드 중심)                                                                              |
| 2.0  | 2025.12.26 | XML 태그 구조화, MUST/MUST NOT 규칙 강화                                                                 |
| 3.0  | 2025.12.26 | **백엔드 전용으로 전면 개편** - Spring Boot, PostgreSQL, Neo4j, RabbitMQ, FastAPI 연동, 성능 최적화 규칙 |
