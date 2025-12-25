# StoLink Backend

AI 기반 작가용 스토리 관리 플랫폼의 Spring Boot 백엔드 서버입니다.

## 기술 스택

- **Spring Boot**: 4.0.1
- **Java**: 21 (AWS Amazon Corretto)
- **Database**: PostgreSQL 16 (정형 데이터), Neo4j 5.15 (그래프 관계)
- **Message Queue**: RabbitMQ 3.13
- **Storage**: 로컬 파일 시스템 (개발), AWS S3 (프로덕션)

## 주요 기능

### 1. 인증 (Auth)
- 간단한 헤더 기반 인증 (`X-User-Id`)
- 회원가입, 로그인
- 프로필 관리

### 2. 프로젝트 관리 (Projects)
- 작품 CRUD
- 페이지네이션 및 정렬
- 통계 (총 단어수, 챕터 수)

### 3. 문서 관리 (Documents)
- 계층적 문서 구조 (폴더/텍스트)
- 트리 형태 조회
- 실시간 단어수 계산
- 내용 자동저장

### 4. 캐릭터 관계도 (Characters - Neo4j)
- 캐릭터 노드 관리
- 관계 (우호/적대/가족/로맨스 등)
- 그래프 시각화용 데이터 제공

### 5. 복선 관리 (Foreshadowing)
- 복선 태그 등록
- 등장 위치 추적
- 회수 상태 관리

### 6. AI 통합 (RabbitMQ)
- 비동기 분석 작업 발행
- AI Worker 콜백 처리
- 이미지 생성 작업 큐잉

## 시작하기

### 사전 요구사항

- Java 21+
- Docker & Docker Compose

### 1. 데이터베이스 시작

```bash
docker-compose up -d
```

다음 서비스가 시작됩니다:
- PostgreSQL: `localhost:5432`
- Neo4j: `localhost:7474` (브라우저), `localhost:7687` (Bolt)
- RabbitMQ: `localhost:15672` (Management UI)

### 2. 애플리케이션 실행

#### Gradle Wrapper 사용 (권장)

Windows:
```bash
gradlew.bat bootRun
```

Linux/Mac:
```bash
./gradlew bootRun
```

#### IDE에서 실행
`BackendApplication.java`의 `main` 메서드를 실행합니다.

### 3. API 테스트

서버가 `http://localhost:8080`에서 실행됩니다.

#### 회원가입
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "test123",
    "nickname": "작가테스트"
  }'
```

#### 작품 생성
```bash
curl -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" \
  -H "X-User-Id: {userId}" \
  -d '{
    "title": "나의 첫 소설",
    "genre": "fantasy",
    "description": "판타지 소설입니다"
  }'
```

## 프로젝트 구조

```
src/main/java/com/stolink/backend/
├── global/
│   ├── common/         # 공통 DTO, 엔티티, 예외
│   ├── config/         # 설정 (RabbitMQ, CORS, Neo4j)
│   └── util/           # 파일 저장 등 유틸리티
├── domain/
│   ├── user/           # 사용자 인증
│   ├── project/        # 작품 관리
│   ├── document/       # 문서 관리
│   ├── character/      # 캐릭터 (Neo4j)
│   ├── foreshadowing/  # 복선 관리
│   └── ai/             # AI 연동 (RabbitMQ)
└── BackendApplication.java
```

각 도메인은 다음 구조를 따릅니다:
```
domain/{name}/
├── entity/       # JPA 엔티티 또는 Neo4j 노드
├── dto/          # 요청/응답 DTO
├── repository/   # 데이터 액세스
├── service/      # 비즈니스 로직
└── controller/   # REST API
```

## API 명세

### 인증
- `POST /api/auth/register` - 회원가입
- `POST /api/auth/login` - 로그인
- `GET /api/auth/me` - 내 정보 조회
- `PATCH /api/auth/me` - 프로필 수정

### 프로젝트
- `GET /api/projects` - 작품 목록 (페이지네이션)
- `POST /api/projects` - 작품 생성
- `GET /api/projects/{id}` - 작품 상세
- `PATCH /api/projects/{id}` - 작품 수정
- `DELETE /api/projects/{id}` - 작품 삭제

### 문서
- `GET /api/projects/{pid}/documents` - 문서 트리
- `POST /api/projects/{pid}/documents` - 문서 생성
- `GET /api/documents/{id}` - 문서 상세
- `PATCH /api/documents/{id}/content` - 내용 수정
- `DELETE /api/documents/{id}` - 문서 삭제

### 캐릭터
- `GET /api/projects/{pid}/characters` - 캐릭터 목록
- `POST /api/projects/{pid}/characters` - 캐릭터 생성
- `GET /api/projects/{pid}/relationships` - 관계 포함 조회
- `POST /api/relationships` - 관계 생성
- `DELETE /api/characters/{id}` - 캐릭터 삭제

### AI
- `POST /api/ai/analyze` - 분석 요청 (202 Accepted)
- `GET /api/ai/jobs/{jobId}` - 작업 상태 조회
- `POST /api/internal/ai/callback` - AI Worker 콜백 (내부용)

## 데이터베이스 스키마

### PostgreSQL
- `users` - 사용자 정보
- `projects` - 작품
- `documents` - 문서 (계층 구조)
- `foreshadowing` - 복선
- `foreshadowing_appearances` - 복선 등장 위치

### Neo4j
- `Character` 노드 - 캐릭터 정보
- `RELATED_TO` 관계 - 캐릭터 간 관계

## 환경 설정

`src/main/resources/application.yml`에서 다음 항목을 설정할 수 있습니다:

- 데이터베이스 연결 정보
- RabbitMQ 큐 이름
- 파일 저장 경로
- 로깅 레벨

## 개발 시 주의사항

### 인증
현재는 간단한 헤더 기반(`X-User-Id`) 인증을 사용합니다.
프로덕션에서는 JWT 또는 Spring Security 기반 인증으로 전환하세요.

### 비밀번호
현재 비밀번호를 해시하지 않고 평문으로 저장합니다.
프로덕션에서는 반드시 BCrypt 등으로 해시하세요.

### CORS
현재 `localhost:3000`, `localhost:5173`을 허용합니다.
프로덕션 도메인을 추가하세요.

## 트러블슈팅

### PostgreSQL 연결 실패
```bash
docker-compose ps  # 컨테이너 상태 확인
docker-compose logs postgres  # 로그 확인
```

### Neo4j 연결 실패
Neo4j 브라우저(`http://localhost:7474`)에서 직접 연결 테스트:
- URL: `bolt://localhost:7687`
- Username: `neo4j`
- Password: `stolink123`

### RabbitMQ 큐 확인
Management UI(`http://localhost:15672`)에서 큐 상태 확인:
- Username: `guest`
- Password: `guest`

## 라이센스

이 프로젝트는 교육/데모 목적으로 제공됩니다.

## 관련 프로젝트

- **Frontend**: React + TypeScript + Tiptap
- **AI Worker**: FastAPI + LangGraph
