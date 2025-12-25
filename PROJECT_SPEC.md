# StoLink Backend 프로젝트 명세서

> **Version**: 1.0.0  
> **Last Updated**: 2025-12-25  
> **Author**: StoLink Development Team

---

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [기술 스택](#2-기술-스택)
3. [시스템 아키텍처](#3-시스템-아키텍처)
4. [프로젝트 구조](#4-프로젝트-구조)
5. [도메인 모델](#5-도메인-모델)
6. [데이터베이스 스키마](#6-데이터베이스-스키마)
7. [설정 및 환경변수](#7-설정-및-환경변수)
8. [실행 방법](#8-실행-방법)
9. [개발 가이드라인](#9-개발-가이드라인)

---

## 1. 프로젝트 개요

### 1.1 서비스 소개

**StoLink**는 AI 기반 작가용 스토리 관리 플랫폼입니다. 작가들이 소설이나 시나리오를 작성할 때 필요한 다양한 기능을 제공합니다.

### 1.2 주요 기능

| 기능 | 설명 |
|------|------|
| **사용자 인증** | 회원가입, 로그인, 프로필 관리 |
| **프로젝트 관리** | 작품(소설/시나리오) CRUD, 장르별 분류, 통계 제공 |
| **문서 관리** | 계층적 문서 구조, 폴더/텍스트 타입, 실시간 단어수 계산 |
| **캐릭터 관계도** | Neo4j 기반 그래프 데이터베이스, 캐릭터 관계 시각화 |
| **복선 관리** | 복선 태그 등록, 등장 위치 추적, 회수 상태 관리 |
| **AI 통합** | RabbitMQ 기반 비동기 분석, 이미지 생성 작업 큐잉 |

### 1.3 시스템 요구사항

- **Java**: 21+ (Amazon Corretto 권장)
- **Docker**: 20.10+
- **Docker Compose**: 2.0+

---

## 2. 기술 스택

### 2.1 Backend Framework

| 기술 | 버전 | 용도 |
|------|------|------|
| Spring Boot | 3.4.1 | 메인 프레임워크 |
| Spring Data JPA | 3.x | PostgreSQL ORM |
| Spring Data Neo4j | 7.x | Neo4j 그래프 DB 연동 |
| Spring AMQP | 3.x | RabbitMQ 메시지 큐 |
| Lombok | 1.18+ | 보일러플레이트 코드 감소 |
| QueryDSL | 5.x | JPA 쿼리 생성 |

### 2.2 Database

| 데이터베이스 | 버전 | 용도 |
|--------------|------|------|
| PostgreSQL | 16 | 정형 데이터 (사용자, 프로젝트, 문서) |
| Neo4j | 5.15 | 그래프 데이터 (캐릭터 관계도) |

### 2.3 Message Queue

| 기술 | 버전 | 용도 |
|------|------|------|
| RabbitMQ | 3.13 | AI 작업 비동기 처리 |

### 2.4 Additional Libraries

| 라이브러리 | 용도 |
|------------|------|
| Hypersistence Utils | PostgreSQL JSONB 타입 지원 |
| Jackson | JSON 직렬화/역직렬화 |

---

## 3. 시스템 아키텍처

### 3.1 전체 아키텍처

```
┌───────────────────────────────────────────────────────────────────────────── ┐
│                              Client (React + TypeScript)                     │
└───────────────────────────────────────────────────────────────────────────── ┘
                                        │
                                        ▼
┌───────────────────────────────────────────────────────────────────────────── ┐
│                          Spring Boot Backend (:8080)                         │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                           REST API Controllers                          │ │
│  │  ┌──────────┐ ┌───────────┐ ┌────────────┐ ┌────────────┐ ┌──────────┐  │ │
│  │  │  Auth    │ │  Project  │ │  Document  │ │  Character │ │    AI    │  │ │
│  │  │Controller│ │ Controller│ │ Controller │ │ Controller │ │Controller│  │ │
│  │  └──────────┘ └───────────┘ └────────────┘ └────────────┘ └──────────┘  │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                              Service Layer                              │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                            Repository Layer                             │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────────────── ┘
                    │                                      │
          ┌─────────┴─────────┐                   ┌────────┴────────┐
          ▼                   ▼                   ▼                  ▼
    ┌──────────┐        ┌──────────┐        ┌──────────┐      ┌──────────┐
    │PostgreSQL│        │  Neo4j   │        │ RabbitMQ │      │  Storage │
    │  :5432   │        │  :7687   │        │  :5672   │      │  (Local) │
    └──────────┘        └──────────┘        └──────────┘      └──────────┘
                                                  │
                                    ┌─────────────┴─────────────┐
                                    ▼                           ▼
                            ┌──────────────┐           ┌──────────────┐
                            │Analysis Queue│           │ Image Queue  │
                            └──────────────┘           └──────────────┘
                                    │                           │
                                    ▼                           ▼
                            ┌────────────── ┐           ┌──────────────┐
                            │   FastAPI     │           │   FastAPI    │
                            │Analysis Worker│           │ Image Worker │
                            └────────────── ┘           └──────────────┘
```

### 3.2 데이터 흐름

1. **클라이언트 → Backend**: REST API 요청
2. **Backend → PostgreSQL**: 정형 데이터 CRUD
3. **Backend → Neo4j**: 캐릭터 관계 그래프 관리
4. **Backend → RabbitMQ**: AI 분석/이미지 생성 작업 발행
5. **AI Worker → Backend**: 콜백 API로 결과 전달

---

## 4. 프로젝트 구조

```
src/main/java/com/stolink/backend/
├── BackendApplication.java          # 애플리케이션 진입점
│
├── global/                           # 글로벌 설정 및 공통 모듈
│   ├── common/
│   │   ├── dto/
│   │   │   └── ApiResponse.java     # 통합 API 응답 DTO
│   │   ├── entity/
│   │   │   └── BaseEntity.java      # 공통 엔티티 (createdAt, updatedAt)
│   │   └── exception/               # 커스텀 예외 및 핸들러
│   ├── config/
│   │   ├── CorsConfig.java          # CORS 설정
│   │   ├── Neo4jConfig.java         # Neo4j 연결 설정
│   │   └── RabbitMQConfig.java      # RabbitMQ 큐 설정
│   └── util/
│       └── FileStorageUtil.java     # 파일 저장 유틸리티
│
└── domain/                           # 도메인별 모듈
    ├── user/                         # 사용자 도메인
    │   ├── entity/User.java
    │   ├── dto/
    │   │   ├── RegisterRequest.java
    │   │   ├── LoginRequest.java
    │   │   └── UserResponse.java
    │   ├── repository/UserRepository.java
    │   ├── service/AuthService.java
    │   └── controller/AuthController.java
    │
    ├── project/                      # 프로젝트 도메인
    │   ├── entity/Project.java
    │   ├── dto/
    │   │   ├── CreateProjectRequest.java
    │   │   └── ProjectResponse.java
    │   ├── repository/ProjectRepository.java
    │   ├── service/ProjectService.java
    │   └── controller/ProjectController.java
    │
    ├── document/                     # 문서 도메인
    │   ├── entity/Document.java
    │   ├── dto/
    │   │   ├── CreateDocumentRequest.java
    │   │   └── DocumentTreeResponse.java
    │   ├── repository/DocumentRepository.java
    │   ├── service/DocumentService.java
    │   └── controller/DocumentController.java
    │
    ├── character/                    # 캐릭터 도메인 (Neo4j)
    │   ├── node/Character.java
    │   ├── relationship/CharacterRelationship.java
    │   ├── repository/CharacterRepository.java
    │   ├── service/CharacterService.java
    │   └── controller/CharacterController.java
    │
    ├── foreshadowing/               # 복선 도메인
    │   ├── entity/
    │   │   ├── Foreshadowing.java
    │   │   └── ForeshadowingAppearance.java
    │   └── repository/
    │       ├── ForeshadowingRepository.java
    │       └── ForeshadowingAppearanceRepository.java
    │
    └── ai/                           # AI 연동 도메인
        ├── dto/
        │   ├── AnalysisTaskDTO.java
        │   ├── AnalysisCallbackDTO.java
        │   ├── ImageGenerationTaskDTO.java
        │   └── ImageCallbackDTO.java
        ├── service/
        │   ├── RabbitMQProducerService.java
        │   └── AICallbackService.java
        └── controller/AIController.java
```

---

## 5. 도메인 모델

### 5.1 User (사용자)

사용자 계정 정보를 관리합니다.

| 필드 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| `id` | UUID | PK, auto-generated | 사용자 고유 식별자 |
| `email` | String | NOT NULL, UNIQUE | 이메일 주소 |
| `password` | String | NOT NULL | 비밀번호 (현재 평문 저장) |
| `nickname` | String(100) | NOT NULL | 닉네임 |
| `avatarUrl` | String(500) | nullable | 프로필 이미지 URL |
| `createdAt` | LocalDateTime | auto | 생성 시간 |
| `updatedAt` | LocalDateTime | auto | 수정 시간 |

### 5.2 Project (프로젝트/작품)

작품 정보를 관리합니다.

| 필드 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| `id` | UUID | PK, auto-generated | 프로젝트 고유 식별자 |
| `user` | User | FK, NOT NULL | 소유자 |
| `title` | String | NOT NULL | 작품 제목 |
| `genre` | Genre (Enum) | nullable | 장르 |
| `description` | TEXT | nullable | 작품 설명 |
| `coverImage` | String(500) | nullable | 커버 이미지 URL |
| `status` | ProjectStatus | NOT NULL, default: WRITING | 작품 상태 |
| `author` | String(100) | nullable | 저자명 |
| `extras` | JSONB | nullable | 추가 정보 (JSON) |

**Genre Enum 값:**
- `FANTASY`, `ROMANCE`, `SF`, `MYSTERY`, `THRILLER`, `HORROR`, `DRAMA`, `OTHER`

**ProjectStatus Enum 값:**
- `WRITING` (작성 중)
- `COMPLETED` (완료)

### 5.3 Document (문서)

계층적 문서 구조를 관리합니다.

| 필드 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| `id` | UUID | PK, auto-generated | 문서 고유 식별자 |
| `project` | Project | FK, NOT NULL | 소속 프로젝트 |
| `parent` | Document | FK, nullable | 부모 문서 (폴더) |
| `type` | DocumentType | NOT NULL | 문서 타입 |
| `title` | String | NOT NULL | 문서 제목 |
| `content` | TEXT | default: "" | 본문 내용 (HTML) |
| `synopsis` | TEXT | default: "" | 시놉시스 |
| `order` | Integer | NOT NULL, default: 0 | 정렬 순서 |
| `status` | DocumentStatus | default: DRAFT | 문서 상태 |
| `label` | String(50) | nullable | 라벨 |
| `labelColor` | String(7) | nullable | 라벨 색상 (#RRGGBB) |
| `wordCount` | Integer | NOT NULL, default: 0 | 단어 수 |
| `targetWordCount` | Integer | nullable | 목표 단어 수 |
| `includeInCompile` | Boolean | default: true | 컴파일 포함 여부 |
| `keywords` | TEXT | nullable | 키워드 (쉼표 구분) |
| `notes` | TEXT | nullable | 메모 |

**DocumentType Enum 값:**
- `FOLDER` (폴더)
- `TEXT` (텍스트 문서)

**DocumentStatus Enum 값:**
- `DRAFT` (초안)
- `REVISED` (수정됨)
- `FINAL` (최종)

### 5.4 Character (캐릭터 - Neo4j)

캐릭터 정보를 Neo4j 그래프 노드로 관리합니다.

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | String | UUID 자동 생성 |
| `projectId` | String | 소속 프로젝트 ID |
| `name` | String | 캐릭터 이름 |
| `role` | String | 역할 (protagonist, antagonist, supporting, mentor, sidekick, other) |
| `imageUrl` | String | 캐릭터 이미지 URL |
| `extras` | Map<String, Object> | 추가 정보 (나이, 종족, 성격 등) |
| `relationships` | List<CharacterRelationship> | 관계 목록 (OUTGOING) |

**CharacterRelationship (관계):**

| 필드 | 타입 | 설명 |
|------|------|------|
| `type` | String | 관계 타입 (friendly, rival, family, romantic, mentor, other) |
| `strength` | Integer | 관계 강도 (1-10) |
| `description` | String | 관계 설명 |
| `targetCharacter` | Character | 대상 캐릭터 |

### 5.5 Foreshadowing (복선)

복선 정보를 관리합니다.

| 필드 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| `id` | UUID | PK, auto-generated | 복선 고유 식별자 |
| `project` | Project | FK, NOT NULL | 소속 프로젝트 |
| `tag` | String(100) | NOT NULL, UNIQUE per project | 복선 태그 |
| `status` | ForeshadowingStatus | default: PENDING | 회수 상태 |
| `description` | TEXT | nullable | 복선 설명 |
| `importance` | Importance | default: MINOR | 중요도 |
| `appearances` | List<ForeshadowingAppearance> | cascade | 등장 위치 목록 |

**ForeshadowingStatus Enum 값:**
- `PENDING` (미회수)
- `RECOVERED` (회수됨)
- `IGNORED` (무시)

**Importance Enum 값:**
- `MAJOR` (주요)
- `MINOR` (부수)

---

## 6. 데이터베이스 스키마

### 6.1 PostgreSQL 테이블

```sql
-- 사용자 테이블
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 프로젝트 테이블
CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    genre VARCHAR(50),
    description TEXT,
    cover_image VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'WRITING',
    author VARCHAR(100),
    extras JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 문서 테이블
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id),
    parent_id UUID REFERENCES documents(id),
    type VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT DEFAULT '',
    synopsis TEXT DEFAULT '',
    "order" INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) DEFAULT 'DRAFT',
    label VARCHAR(50),
    label_color VARCHAR(7),
    word_count INTEGER NOT NULL DEFAULT 0,
    target_word_count INTEGER,
    include_in_compile BOOLEAN DEFAULT TRUE,
    keywords TEXT,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 복선 테이블
CREATE TABLE foreshadowing (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id),
    tag VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    description TEXT,
    importance VARCHAR(20) DEFAULT 'MINOR',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(project_id, tag)
);

-- 복선 등장 위치 테이블
CREATE TABLE foreshadowing_appearances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    foreshadowing_id UUID NOT NULL REFERENCES foreshadowing(id),
    document_id UUID NOT NULL REFERENCES documents(id),
    start_offset INTEGER,
    end_offset INTEGER,
    context TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 6.2 Neo4j 스키마

```cypher
// 캐릭터 노드
(:Character {
    id: "uuid-string",
    projectId: "project-uuid",
    name: "캐릭터 이름",
    role: "protagonist",
    imageUrl: "https://...",
    extras: { age: 25, species: "human", personality: ["용감", "정의로움"] }
})

// 관계
(:Character)-[:RELATED_TO {
    type: "friendly",
    strength: 8,
    description: "어린 시절 친구"
}]->(:Character)
```

---

## 7. 설정 및 환경변수

### 7.1 application.yml

```yaml
spring:
  application:
    name: sto-link-backend

  # PostgreSQL
  datasource:
    url: jdbc:postgresql://localhost:5432/stolink
    username: stolink
    password: stolink123

  # JPA
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

  # Neo4j
  neo4j:
    uri: bolt://localhost:7687
    authentication:
      username: neo4j
      password: stolink123

  # RabbitMQ
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

  # File Upload
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB

server:
  port: 8080

app:
  storage:
    base-path: ./storage/uploads
  rabbitmq:
    queues:
      analysis: stolink.analysis.queue
      image: stolink.image.queue
  ai:
    callback-base-url: http://localhost:8080/api/internal/ai
```

### 7.2 Docker Compose 서비스

| 서비스 | 이미지 | 포트 |
|--------|--------|------|
| PostgreSQL | postgres:16-alpine | 5432 |
| Neo4j | neo4j:5.15-community | 7474 (HTTP), 7687 (Bolt) |
| RabbitMQ | rabbitmq:3.13-management-alpine | 5672 (AMQP), 15672 (UI) |

---

## 8. 실행 방법

### 8.1 Docker 서비스 시작

```bash
docker-compose up -d
```

### 8.2 애플리케이션 실행

**Windows:**
```bash
gradlew.bat bootRun
```

**Linux/Mac:**
```bash
./gradlew bootRun
```

### 8.3 접속 정보

| 서비스 | URL | 인증 정보 |
|--------|-----|----------|
| Backend API | http://localhost:8080 | - |
| Neo4j Browser | http://localhost:7474 | neo4j / stolink123 |
| RabbitMQ UI | http://localhost:15672 | guest / guest |

---

## 9. 개발 가이드라인

### 9.1 인증

현재는 간단한 헤더 기반 인증을 사용합니다:

```http
X-User-Id: {user-uuid}
```

> ⚠️ **주의**: 프로덕션에서는 JWT 또는 Spring Security 기반 인증으로 전환하세요.

### 9.2 비밀번호

현재 비밀번호를 평문으로 저장합니다.

> ⚠️ **주의**: 프로덕션에서는 BCrypt 등으로 해시하여 저장하세요.

### 9.3 CORS 설정

현재 허용된 Origin:
- `http://localhost:3000`
- `http://localhost:5173`

### 9.4 API 응답 형식

모든 API는 통일된 응답 형식을 사용합니다:

```json
{
  "code": 200,
  "status": "OK",
  "message": "OK",
  "data": { ... }
}
```

에러 응답:
```json
{
  "code": 400,
  "status": "BAD_REQUEST",
  "message": "에러 메시지",
  "data": null
}
```

---

## 부록

### A. 관련 프로젝트

| 프로젝트 | 설명 |
|----------|------|
| Frontend | React + TypeScript + Tiptap 에디터 |
| AI Worker | FastAPI + LangGraph 기반 AI 분석 |
| Image Worker | FastAPI + 이미지 생성 모델 |

### B. 참고 자료

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Spring Data Neo4j](https://docs.spring.io/spring-data/neo4j/docs/current/reference/html/)
- [RabbitMQ Tutorials](https://www.rabbitmq.com/getstarted.html)
