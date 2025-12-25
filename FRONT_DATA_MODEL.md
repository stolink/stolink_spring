# StoLink 데이터 모델 명세

> **버전**: 1.2
> **최종 수정**: 2024년 12월 25일
> **상태**: 현재 구현 기준

---

## 개요

이 문서는 StoLink 프로젝트에서 사용되는 모든 **엔티티(Entity)**와 **DTO(Data Transfer Object)**를 정의합니다.

> 📡 API 명세 → [API_SPEC.md](./API_SPEC.md)
> 📋 기능 명세 → [SPEC.md](./SPEC.md)

---

## 데이터 저장소 아키텍처

```
┌─────────────────────────────────────────────────────────────────────┐
│                        데이터 저장소 분리                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   ┌─────────────────────────────┐   ┌─────────────────────────────┐ │
│   │      PostgreSQL (RDS)       │   │        Neo4j                │ │
│   │      정형 데이터 저장        │   │   그래프/관계 데이터 저장     │ │
│   ├─────────────────────────────┤   ├─────────────────────────────┤ │
│   │  • User                     │   │  • Character (노드)         │ │
│   │  • Project                  │   │  • Relationship (엣지)      │ │
│   │  • Document                 │   │  • Place (노드)             │ │
│   │  • Foreshadowing            │   │  • Item (노드)              │ │
│   │  • ForeshadowingAppearance  │   │  • 자연어 파싱 결과          │ │
│   │  • Export/Share 기록        │   │  • AI 분석 결과             │ │
│   └─────────────────────────────┘   └─────────────────────────────┘ │
│                                                                     │
│   ┌─────────────────────────────┐                                   │
│   │          AWS S3             │                                   │
│   │    대용량 파일 저장          │                                   │
│   ├─────────────────────────────┤                                   │
│   │  • 문서 스냅샷 (10분 주기)   │                                   │
│   │  • 표지 이미지               │                                   │
│   │  • 캐릭터 이미지             │                                   │
│   │  • 내보내기 파일             │                                   │
│   └─────────────────────────────┘                                   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

# Part 1: PostgreSQL 엔티티

> 정형 데이터, 트랜잭션, CRUD 중심

---

## 1. 인증 (Auth) - PostgreSQL

### 1.1 User Entity

| 필드      | 타입      | 필수 | 설명           |
| --------- | --------- | ---- | -------------- |
| id        | UUID      | ✅   | PK             |
| email     | VARCHAR   | ✅   | UNIQUE, 로그인 |
| password  | VARCHAR   | ✅   | bcrypt 해시    |
| nickname  | VARCHAR   | ✅   | 필명/닉네임    |
| avatarUrl | VARCHAR   | ❌   | S3 URL         |
| createdAt | TIMESTAMP | ✅   | 가입일시       |
| updatedAt | TIMESTAMP | ✅   | 수정일시       |

### 1.2 Auth DTOs

```typescript
interface LoginInput {
  email: string;
  password: string;
}

interface RegisterInput {
  email: string;
  password: string;
  nickname: string;
}

interface AuthResponse {
  user: User;
  accessToken: string;
  refreshToken: string;
}
```

---

## 2. 프로젝트 (Project) - PostgreSQL

### 2.1 Project Entity

| 필드        | 타입      | 필수 | FK/제약조건 | 설명              |
| ----------- | --------- | ---- | ----------- | ----------------- |
| id          | UUID      | ✅   | PK          | 고유 식별자       |
| userId      | UUID      | ✅   | FK → User   | 소유자            |
| title       | VARCHAR   | ✅   |             | 작품 제목         |
| genre       | ENUM      | ✅   |             | 장르              |
| description | TEXT      | ❌   |             | 시놉시스          |
| coverImage  | VARCHAR   | ❌   |             | S3 URL            |
| status      | ENUM      | ✅   |             | writing/completed |
| author      | VARCHAR   | ❌   |             | 작가명 (표시용)   |
| extras      | JSONB     | ❌   |             | 동적 메타데이터   |
| createdAt   | TIMESTAMP | ✅   |             | 생성일시          |
| updatedAt   | TIMESTAMP | ✅   |             | 수정일시          |

### 2.2 ProjectStats (계산 필드 or 별도 테이블)

```typescript
interface ProjectStats {
  totalCharacters: number; // 집계
  totalWords: number; // 집계
  chapterCount: number; // 집계
  characterCount: number; // Neo4j에서 조회
  foreshadowingRecoveryRate: number; // 계산
  consistencyScore: number; // AI 결과 (Neo4j)
}
```

---

## 3. 문서 (Document) - PostgreSQL ⭐ 핵심

> Scrivener 스타일의 통합 문서 모델

### 3.1 Document Entity

| 필드             | 타입      | 필수 | FK/제약조건   | 설명                 |
| ---------------- | --------- | ---- | ------------- | -------------------- |
| id               | UUID      | ✅   | PK            | 고유 식별자          |
| projectId        | UUID      | ✅   | FK → Project  | 프로젝트             |
| parentId         | UUID      | ❌   | FK → Document | 상위 폴더 (self-ref) |
| type             | ENUM      | ✅   |               | folder/text          |
| title            | VARCHAR   | ✅   |               | 문서 제목            |
| content          | TEXT      | ✅   |               | 본문 (HTML)          |
| synopsis         | TEXT      | ✅   |               | 요약                 |
| order            | INTEGER   | ✅   |               | 형제 간 순서         |
| status           | ENUM      | ✅   |               | draft/revised/final  |
| label            | VARCHAR   | ❌   |               | POV 캐릭터 등        |
| labelColor       | VARCHAR   | ❌   |               | #hex                 |
| wordCount        | INTEGER   | ✅   |               | 글자수               |
| targetWordCount  | INTEGER   | ❌   |               | 목표 글자수          |
| includeInCompile | BOOLEAN   | ✅   | DEFAULT true  | 내보내기 포함        |
| keywords         | VARCHAR[] | ❌   |               | 태그 배열            |
| notes            | TEXT      | ❌   |               | 작가 메모            |
| createdAt        | TIMESTAMP | ✅   |               | 생성일시             |
| updatedAt        | TIMESTAMP | ✅   |               | 수정일시             |

### 3.2 Document-Character 연결 테이블

| 필드        | 타입 | FK              |
| ----------- | ---- | --------------- |
| documentId  | UUID | FK → Document   |
| characterId | UUID | FK → Neo4j 참조 |

### 3.3 Document DTOs

```typescript
interface CreateDocumentInput {
  projectId: string;
  parentId?: string;
  type: "folder" | "text";
  title: string;
  synopsis?: string;
  targetWordCount?: number;
}

interface UpdateDocumentInput {
  title?: string;
  content?: string;
  synopsis?: string;
  order?: number;
  status?: "draft" | "revised" | "final";
  characterIds?: string[]; // Neo4j 노드 ID
  foreshadowingIds?: string[];
}
```

---

## 4. 복선 (Foreshadowing) - PostgreSQL

### 4.1 Foreshadowing Entity

| 필드        | 타입      | 필수 | FK/제약조건  | 설명                      |
| ----------- | --------- | ---- | ------------ | ------------------------- |
| id          | UUID      | ✅   | PK           | 고유 식별자               |
| projectId   | UUID      | ✅   | FK → Project | 프로젝트                  |
| tag         | VARCHAR   | ✅   | UNIQUE(proj) | 태그명 (예: 전설의검)     |
| status      | ENUM      | ✅   |              | pending/recovered/ignored |
| description | TEXT      | ❌   |              | 설명                      |
| importance  | ENUM      | ❌   |              | major/minor               |
| createdAt   | TIMESTAMP | ✅   |              | 생성일시                  |
| updatedAt   | TIMESTAMP | ✅   |              | 수정일시                  |

### 4.2 ForeshadowingAppearance Entity

| 필드            | 타입      | 필수 | FK                 | 설명           |
| --------------- | --------- | ---- | ------------------ | -------------- |
| id              | UUID      | ✅   | PK                 | 고유 식별자    |
| foreshadowingId | UUID      | ✅   | FK → Foreshadowing | 복선           |
| documentId      | UUID      | ✅   | FK → Document      | 등장 문서      |
| line            | INTEGER   | ✅   |                    | 라인 번호      |
| context         | TEXT      | ✅   |                    | 주변 텍스트    |
| isRecovery      | BOOLEAN   | ✅   | DEFAULT false      | 회수 지점 여부 |
| createdAt       | TIMESTAMP | ✅   |                    | 생성일시       |

---

# Part 2: Neo4j 엔티티

> 그래프 데이터, 관계 분석, AI 파싱 결과

---

## 5. 캐릭터 (Character) - Neo4j 노드

```cypher
(:Character {
  id: "uuid",
  projectId: "uuid",
  name: "주인공",
  role: "protagonist",
  imageUrl: "https://s3.../image.jpg",

  // 동적 속성 (extras)
  age: 25,
  species: "human",
  personality: ["용감", "정의로움"],
  description: "..."
})
```

### 5.1 Character 속성

| 속성      | 타입   | 필수 | 설명        |
| --------- | ------ | ---- | ----------- |
| id        | string | ✅   | UUID        |
| projectId | string | ✅   | 프로젝트 ID |
| name      | string | ✅   | 캐릭터 이름 |
| role      | string | ❌   | 역할        |
| imageUrl  | string | ❌   | S3 URL      |
| extras.\* | any    | ❌   | 동적 속성   |

### 5.2 CharacterRole Enum

```typescript
type CharacterRole =
  | "protagonist"
  | "antagonist"
  | "supporting"
  | "mentor"
  | "sidekick"
  | "other";
```

---

## 6. 캐릭터 관계 (Relationship) - Neo4j 엣지

```cypher
(:Character)-[:RELATED_TO {
  id: "uuid",
  type: "friendly",
  strength: 8,
  description: "어린시절 친구",
  since: "1장"
}]->(:Character)
```

### 6.1 Relationship 속성

| 속성        | 타입   | 필수 | 설명      |
| ----------- | ------ | ---- | --------- |
| id          | string | ✅   | UUID      |
| type        | string | ✅   | 관계 유형 |
| strength    | int    | ✅   | 1-10      |
| description | string | ❌   | 관계 설명 |
| since       | string | ❌   | 시작 시점 |

### 6.2 RelationshipType Enum

```typescript
type RelationshipType =
  | "friendly"
  | "hostile"
  | "neutral"
  | "romantic"
  | "family";
```

---

## 7. 장소 (Place) - Neo4j 노드

```cypher
(:Place {
  id: "uuid",
  projectId: "uuid",
  name: "왕국 아르카나",
  type: "region",
  imageUrl: "https://...",

  // 동적 속성
  climate: "온대",
  population: 100000,
  description: "..."
})
```

### 7.1 Place 속성

| 속성      | 타입   | 필수 | 설명        |
| --------- | ------ | ---- | ----------- |
| id        | string | ✅   | UUID        |
| projectId | string | ✅   | 프로젝트 ID |
| name      | string | ✅   | 장소 이름   |
| type      | string | ❌   | 장소 유형   |
| imageUrl  | string | ❌   | S3 URL      |

### 7.2 장소 관계

```cypher
// 장소 포함 관계
(:Place)-[:CONTAINS]->(:Place)

// 캐릭터 거주 관계
(:Character)-[:LIVES_IN]->(:Place)

// 문서 배경 관계 (PostgreSQL 참조)
(:Place)-[:SETTING_OF {documentId: "uuid"}]->(:DocumentRef)
```

---

## 8. 아이템 (Item) - Neo4j 노드

```cypher
(:Item {
  id: "uuid",
  projectId: "uuid",
  name: "전설의 검",
  type: "weapon",
  imageUrl: "https://...",

  // 동적 속성
  power: 100,
  origin: "고대 드워프 제작",
  specialAbility: "마법 저항"
})
```

### 8.1 아이템 관계

```cypher
// 소유 관계
(:Character)-[:OWNS {since: "3장"}]->(:Item)

// 소유권 이전 기록
(:Character)-[:TRANSFERRED {
  to: "characterId",
  at: "5장"
}]->(:Item)
```

---

## 9. AI 분석 결과 - Neo4j

### 9.1 자연어 파싱 결과

```cypher
// 문서에서 추출된 엔티티
(:ParsedEntity {
  id: "uuid",
  documentId: "uuid",  // PostgreSQL 참조
  type: "character" | "place" | "item" | "event",
  name: "아린",
  confidence: 0.95,
  context: "아린이 검을 들었다",
  line: 42
})

// 엔티티 간 추출된 관계
(:ParsedEntity)-[:MENTIONED_WITH {
  context: "함께 여행을 떠났다",
  sentiment: "positive"
}]->(:ParsedEntity)
```

### 9.2 일관성 검사 결과

```cypher
(:ConsistencyIssue {
  id: "uuid",
  projectId: "uuid",
  type: "character_contradiction" | "timeline_error" | "item_missing",
  severity: "warning" | "error",
  documentId: "uuid",
  line: 42,
  message: "아린은 물을 무서워한다고 설정했으나...",
  suggestion: "물을 극복하는 계기 필요",
  isIgnored: false,
  createdAt: datetime()
})
```

---

# Part 3: 하이브리드 쿼리 패턴

## PostgreSQL → Neo4j 참조

```typescript
// 문서에 등장하는 캐릭터 조회
async function getDocumentCharacters(documentId: string) {
  // 1. PostgreSQL에서 characterIds 조회
  const doc = await pgClient.query(
    "SELECT character_ids FROM documents WHERE id = $1",
    [documentId],
  );

  // 2. Neo4j에서 캐릭터 상세 조회
  const result = await neo4j.run(
    `
    MATCH (c:Character)
    WHERE c.id IN $ids
    RETURN c
  `,
    { ids: doc.character_ids },
  );

  return result.records.map((r) => r.get("c").properties);
}
```

## Neo4j에서 관계 그래프 조회

```cypher
// 캐릭터 관계도 조회
MATCH (c:Character {projectId: $projectId})
OPTIONAL MATCH (c)-[r:RELATED_TO]-(other:Character)
RETURN c, r, other

// 복선과 연결된 캐릭터 조회
MATCH (c:Character)-[:MENTIONED_IN]->(d:DocumentRef)
WHERE d.foreshadowingId = $foreshadowingId
RETURN DISTINCT c
```

---

## UI 전용 타입

### ChapterNode (사이드바용)

> PostgreSQL Document를 UI 표시용으로 간소화

```typescript
interface ChapterNode {
  id: string;
  title: string;
  type: "part" | "chapter" | "section";
  characterCount?: number;
  isPlot?: boolean;
  isModified?: boolean;
  status?: "todo" | "inProgress" | "done" | "revised";
  children?: ChapterNode[];
}
```

### React Flow 노드 타입

```typescript
// Neo4j Character → React Flow 노드로 변환
interface CharacterNode {
  id: string;
  type: "character";
  position: { x: number; y: number };
  data: Character;
}

interface RelationshipEdge {
  id: string;
  source: string;
  target: string;
  type: "relationship";
  data: CharacterRelationship;
}
```

---

## 버전 이력

| 버전 | 날짜       | 변경 내용                                     |
| ---- | ---------- | --------------------------------------------- |
| 1.0  | 2024.12.25 | 현재 구현 기준 최초 작성                      |
| 1.1  | 2024.12.25 | API 엔드포인트 섹션 제거 (API_SPEC.md로 통합) |
| 1.2  | 2024.12.25 | PostgreSQL/Neo4j 저장소 분리 명시             |

---

## 관련 문서

| 문서              | 설명                      |
| ----------------- | ------------------------- |
| `API_SPEC.md`     | API 엔드포인트 명세       |
| `ARCHITECTURE.md` | 프로젝트 아키텍처         |
| `SPEC.md`         | 전체 기능 명세            |
| `src/types/`      | TypeScript 타입 정의 파일 |
