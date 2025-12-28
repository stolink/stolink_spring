# StoLink API 명세

> **버전**: 1.1
> **최종 수정**: 2025년 12월 26일
> **Base URL**: `https://api.stolink.com` (Production) / `http://localhost:8080` (Development)

---

## 개요

이 문서는 StoLink 백엔드 API의 전체 엔드포인트를 정의합니다.

> 📋 기능 명세 → [SPEC.md](./SPEC.md)
> 🗂️ 데이터 모델 → [DATA_MODEL.md](./DATA_MODEL.md)

### 공통 응답 형식

```json
// 성공 (표준)
{
  "success": true,
  "data": { ... }
}

// 성공 (대안 - 일부 API)
{
  "status": "OK",
  "code": 200,
  "data": { ... }
}

// 에러
{
  "success": false,
  "error": {
    "code": "AUTH_INVALID_TOKEN",
    "message": "토큰이 유효하지 않습니다."
  }
}
```

### 인증 헤더

```
Authorization: Bearer {accessToken}
X-User-Id: {userId}  // 일부 API에서 사용
```

---

## 1. 인증 (Auth)

| Method | Endpoint                    | 인증 | 설명          |
| ------ | --------------------------- | ---- | ------------- |
| POST   | `/api/auth/register`        | ❌   | 회원가입      |
| POST   | `/api/auth/login`           | ❌   | 로그인        |
| POST   | `/api/auth/logout`          | ✅   | 로그아웃      |
| POST   | `/api/auth/refresh`         | ❌   | 토큰 갱신     |
| POST   | `/api/auth/forgot-password` | ❌   | 비밀번호 찾기 |
| GET    | `/api/auth/me`              | ✅   | 내 정보 조회  |
| PATCH  | `/api/auth/me`              | ✅   | 내 정보 수정  |

### 1.1 POST /api/auth/register

**Request:**

```json
{
  "email": "user@example.com",
  "password": "securePassword123",
  "nickname": "작가닉네임"
}
```

**Response:** `201 Created`

```json
{
  "success": true,
  "data": {
    "user": {
      "id": "uuid",
      "email": "user@example.com",
      "nickname": "작가닉네임",
      "createdAt": "2024-12-25T00:00:00Z"
    },
    "accessToken": "jwt...",
    "refreshToken": "jwt..."
  }
}
```

### 1.2 POST /api/auth/login

**Request:**

```json
{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

**Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "user": { ... },
    "accessToken": "jwt...",
    "refreshToken": "jwt..."
  }
}
```

### 1.3 POST /api/auth/refresh

**Request:**

```json
{
  "refreshToken": "jwt..."
}
```

**Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "accessToken": "jwt...",
    "refreshToken": "jwt..."
  }
}
```

---

## 2. 프로젝트 (Projects)

| Method | Endpoint                      | 인증 | 설명           |
| ------ | ----------------------------- | ---- | -------------- |
| GET    | `/api/projects`               | ✅   | 내 작품 목록   |
| POST   | `/api/projects`               | ✅   | 작품 생성      |
| GET    | `/api/projects/:id`           | ✅   | 작품 상세 조회 |
| PATCH  | `/api/projects/:id`           | ✅   | 작품 수정      |
| DELETE | `/api/projects/:id`           | ✅   | 작품 삭제      |
| GET    | `/api/projects/:id/stats`     | ✅   | 작품 통계      |
| POST   | `/api/projects/:id/duplicate` | ✅   | 작품 복제      |

### 2.1 GET /api/projects

**Query Parameters:**

| 파라미터 | 타입   | 기본값    | 설명                        |
| -------- | ------ | --------- | --------------------------- |
| status   | string | all       | writing, completed          |
| genre    | string | all       | fantasy, romance, ...       |
| sort     | string | updatedAt | updatedAt, createdAt, title |
| order    | string | desc      | asc, desc                   |
| page     | number | 1         | 페이지 번호                 |
| limit    | number | 20        | 페이지 크기                 |

**Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "projects": [
      {
        "id": "uuid",
        "title": "작품 제목",
        "genre": "fantasy",
        "status": "writing",
        "coverImage": "https://...",
        "stats": {
          "totalWords": 15000,
          "chapterCount": 5
        },
        "createdAt": "2024-12-25T00:00:00Z",
        "updatedAt": "2024-12-25T00:00:00Z"
      }
    ],
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 42,
      "totalPages": 3
    }
  }
}
```

### 2.2 POST /api/projects

**Request:**

```json
{
  "title": "새 작품 제목",
  "genre": "fantasy",
  "description": "시놉시스..."
}
```

**Response:** `201 Created`

### 2.3 GET /api/projects/:id/stats

**Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "totalCharacters": 152340,
    "totalWords": 48291,
    "chapterCount": 24,
    "characterCount": 18,
    "foreshadowingRecoveryRate": 67,
    "consistencyScore": 94,
    "writingDays": 45,
    "estimatedPages": 380
  }
}
```

---

## 3. 문서 (Documents)

| Method | Endpoint                       | 인증 | 설명             |
| ------ | ------------------------------ | ---- | ---------------- |
| GET    | `/api/projects/:pid/documents` | ✅   | 문서 목록 (트리) |
| POST   | `/api/projects/:pid/documents` | ✅   | 문서 생성        |
| GET    | `/api/documents/:id`           | ✅   | 문서 상세 조회   |
| PATCH  | `/api/documents/:id`           | ✅   | 문서 수정        |
| DELETE | `/api/documents/:id`           | ✅   | 문서 삭제        |
| GET    | `/api/documents/:id/content`   | ✅   | 본문만 조회      |
| PATCH  | `/api/documents/:id/content`   | ✅   | 본문만 수정      |
| POST   | `/api/documents/reorder`       | ✅   | 순서 변경        |
| POST   | `/api/documents/bulk-update`   | ✅   | 일괄 수정        |

### 3.1 GET /api/projects/:pid/documents

**Query Parameters:**

| 파라미터 | 타입    | 기본값 | 설명             |
| -------- | ------- | ------ | ---------------- |
| tree     | boolean | true   | 트리 구조로 반환 |
| type     | string  | all    | folder, text     |

**Response:** `200 OK` (tree=true)

```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "type": "folder",
      "title": "1부",
      "order": 0,
      "children": [
        {
          "id": "uuid",
          "type": "text",
          "title": "1장",
          "order": 0,
          "status": "draft",
          "wordCount": 2340,
          "children": []
        }
      ]
    }
  ]
}
```

> ⚠️ **Note**: 백엔드는 `type`을 대문자로 반환할 수 있음 (`FOLDER`, `TEXT`). 프론트엔드에서 lowercase 변환 필요.

### 3.2 POST /api/projects/:pid/documents

**Request:**

```json
{
  "type": "text",
  "title": "새 문서",
  "parentId": "parent-uuid",
  "synopsis": "시놉시스...",
  "targetWordCount": 3000
}
```

**Response:** `201 Created`

### 3.3 PATCH /api/documents/:id/content

**Request:**

```json
{
  "content": "<p>HTML 콘텐츠...</p>"
}
```

**Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "wordCount": 1234,
    "updatedAt": "2024-12-25T00:00:00Z"
  }
}
```

> ⚠️ **Note**: `wordCount`는 백엔드에서 content 저장 시 자동 계산됨. 프론트엔드에서 직접 업데이트하면 **실패함**.

### 3.4 POST /api/documents/reorder

**Request:**

```json
{
  "parentId": "uuid",
  "orderedIds": ["doc1", "doc2", "doc3"]
}
```

---

## 4. 캐릭터 (Characters)

| Method | Endpoint                         | 인증 | 설명          |
| ------ | -------------------------------- | ---- | ------------- |
| GET    | `/api/projects/:pid/characters`  | ✅   | 캐릭터 목록   |
| POST   | `/api/projects/:pid/characters`  | ✅   | 캐릭터 생성   |
| GET    | `/api/characters/:id`            | ✅   | 캐릭터 상세   |
| PATCH  | `/api/characters/:id`            | ✅   | 캐릭터 수정   |
| DELETE | `/api/characters/:id`            | ✅   | 캐릭터 삭제   |
| POST   | `/api/characters/:id/regenerate` | ✅   | 이미지 재생성 |

### 4.1 GET /api/projects/:pid/characters

**Response:** `200 OK`

```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "name": "주인공",
      "role": "protagonist",
      "imageUrl": "https://...",
      "extras": {
        "age": 25,
        "species": "human"
      }
    }
  ]
}
```

### 4.2 POST /api/projects/:pid/characters

**Request:**

```json
{
  "name": "캐릭터 이름",
  "role": "protagonist",
  "extras": {
    "age": 25,
    "species": "elf",
    "description": "외형 설명..."
  }
}
```

---

## 5. 캐릭터 관계 (Relationships)

| Method | Endpoint                           | 인증 | 설명      |
| ------ | ---------------------------------- | ---- | --------- |
| GET    | `/api/projects/:pid/relationships` | ✅   | 관계 목록 |
| POST   | `/api/relationships`               | ✅   | 관계 생성 |
| PATCH  | `/api/relationships/:id`           | ✅   | 관계 수정 |
| DELETE | `/api/relationships/:id`           | ✅   | 관계 삭제 |

### 5.1 GET /api/projects/:pid/relationships

**Response:** `200 OK`

```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "sourceId": "char1",
      "targetId": "char2",
      "type": "friendly",
      "strength": 8,
      "extras": {
        "description": "어린시절 친구"
      }
    }
  ]
}
```

---

## 6. 복선 (Foreshadowing)

| Method | Endpoint                                      | 인증 | 설명           |
| ------ | --------------------------------------------- | ---- | -------------- |
| GET    | `/api/projects/:pid/foreshadowing`            | ✅   | 복선 목록      |
| POST   | `/api/projects/:pid/foreshadowing`            | ✅   | 복선 생성      |
| GET    | `/api/foreshadowing/:id`                      | ✅   | 복선 상세      |
| PATCH  | `/api/foreshadowing/:id`                      | ✅   | 복선 수정      |
| DELETE | `/api/foreshadowing/:id`                      | ✅   | 복선 삭제      |
| POST   | `/api/foreshadowing/:id/appearances`          | ✅   | 등장 위치 추가 |
| PATCH  | `/api/foreshadowing/:id/recover`              | ✅   | 회수 처리      |
| GET    | `/api/projects/:pid/foreshadowing/unresolved` | ✅   | 미회수 복선만  |

### 6.1 GET /api/projects/:pid/foreshadowing

**Query Parameters:**

| 파라미터   | 타입   | 기본값 | 설명                        |
| ---------- | ------ | ------ | --------------------------- |
| status     | string | all    | pending, recovered, ignored |
| importance | string | all    | major, minor                |

**Response:** `200 OK`

```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "tag": "전설의검",
      "status": "pending",
      "importance": "major",
      "description": "노인이 건넨 검...",
      "appearances": [
        {
          "chapterId": "uuid",
          "chapterTitle": "1장",
          "line": 32,
          "context": "\"이 검을 가져가거라.\"",
          "isRecovery": false
        }
      ]
    }
  ]
}
```

---

## 7. 장소 (Places)

| Method | Endpoint                    | 인증 | 설명      |
| ------ | --------------------------- | ---- | --------- |
| GET    | `/api/projects/:pid/places` | ✅   | 장소 목록 |
| POST   | `/api/projects/:pid/places` | ✅   | 장소 생성 |
| PATCH  | `/api/places/:id`           | ✅   | 장소 수정 |
| DELETE | `/api/places/:id`           | ✅   | 장소 삭제 |

---

## 8. 아이템 (Items)

| Method | Endpoint                   | 인증 | 설명        |
| ------ | -------------------------- | ---- | ----------- |
| GET    | `/api/projects/:pid/items` | ✅   | 아이템 목록 |
| POST   | `/api/projects/:pid/items` | ✅   | 아이템 생성 |
| PATCH  | `/api/items/:id`           | ✅   | 아이템 수정 |
| DELETE | `/api/items/:id`           | ✅   | 아이템 삭제 |
| PATCH  | `/api/items/:id/transfer`  | ✅   | 소유자 변경 |

---

## 9. 내보내기/가져오기 (Export/Import)

| Method | Endpoint                   | 인증 | 설명          |
| ------ | -------------------------- | ---- | ------------- |
| POST   | `/api/projects/:id/export` | ✅   | 내보내기      |
| POST   | `/api/projects/:id/import` | ✅   | 가져오기      |
| GET    | `/api/exports/:jobId`      | ✅   | 내보내기 상태 |

### 9.1 POST /api/projects/:id/export

**Request:**

```json
{
  "format": "pdf",
  "options": {
    "includeAll": true,
    "documentIds": [],
    "includeForeshadowing": false,
    "pageSize": "a4",
    "fontSize": 12
  }
}
```

**Response:** `202 Accepted`

```json
{
  "success": true,
  "data": {
    "jobId": "uuid",
    "status": "processing"
  }
}
```

### 9.2 GET /api/exports/:jobId (Job Polling)

**Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "jobId": "uuid",
    "status": "completed",
    "progress": 100,
    "downloadUrl": "https://...",
    "expiresAt": "2024-12-25T01:00:00Z"
  }
}
```

**Job Status 값:**

| Status       | 설명    |
| ------------ | ------- |
| `pending`    | 대기 중 |
| `processing` | 처리 중 |
| `completed`  | 완료    |
| `failed`     | 실패    |

---

## 10. 공유 (Sharing)

| Method | Endpoint                  | 인증 | 설명           |
| ------ | ------------------------- | ---- | -------------- |
| POST   | `/api/projects/:id/share` | ✅   | 공유 링크 생성 |
| GET    | `/api/projects/:id/share` | ✅   | 공유 설정 조회 |
| DELETE | `/api/projects/:id/share` | ✅   | 공유 비활성화  |
| GET    | `/api/share/:shareId`     | ❌   | 공유 작품 조회 |

---

## 11. AI 기능 (AI)

| Method | Endpoint                    | 인증 | 설명        |
| ------ | --------------------------- | ---- | ----------- |
| POST   | `/api/ai/chat`              | ✅   | AI 챗봇     |
| POST   | `/api/ai/consistency-check` | ✅   | 일관성 검사 |
| POST   | `/api/ai/generate-image`    | ✅   | 이미지 생성 |
| POST   | `/api/ai/analyze`           | ✅   | 작품 분석   |

### 11.1 POST /api/ai/chat

**Request:**

```json
{
  "projectId": "uuid",
  "documentId": "uuid",
  "message": "주인공이 검을 받는 장면 다음에 뭘 써야 할지 모르겠어",
  "context": {
    "includeCharacters": true,
    "includeForeshadowing": true
  }
}
```

**Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "message": "몇 가지 방향을 제안드릴게요...",
    "suggestions": [
      "검의 유래를 밝히는 회상 장면",
      "적의 습격으로 첫 실전",
      "멘토가 검술을 가르치는 수련 장면"
    ]
  }
}
```

### 11.2 POST /api/ai/consistency-check

**Request:**

```json
{
  "projectId": "uuid",
  "documentIds": ["uuid1", "uuid2"]
}
```

**Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "issues": [
      {
        "type": "character_contradiction",
        "severity": "warning",
        "documentId": "uuid",
        "line": 42,
        "message": "아린은 물을 무서워한다고 설정했으나 강에 뛰어들었습니다",
        "suggestion": "물을 극복하는 계기나 설정 수정 필요"
      }
    ],
    "score": 94
  }
}
```

---

## 에러 코드

| 코드                  | HTTP | 설명                  |
| --------------------- | ---- | --------------------- |
| `AUTH_INVALID_TOKEN`  | 401  | 유효하지 않은 토큰    |
| `AUTH_EXPIRED_TOKEN`  | 401  | 만료된 토큰           |
| `AUTH_UNAUTHORIZED`   | 403  | 권한 없음             |
| `RESOURCE_NOT_FOUND`  | 404  | 리소스 없음           |
| `VALIDATION_ERROR`    | 400  | 요청 데이터 검증 실패 |
| `RATE_LIMIT_EXCEEDED` | 429  | 요청 횟수 초과        |
| `INTERNAL_ERROR`      | 500  | 서버 내부 오류        |

---

## 버전 이력

| 버전 | 날짜       | 변경 내용                                                                  |
| ---- | ---------- | -------------------------------------------------------------------------- |
| 1.0  | 2024.12.25 | 전체 API 엔드포인트 초기 정의                                              |
| 1.1  | 2025.12.26 | Job Polling 상태값 문서화, wordCount 백엔드 계산 명시, 응답 형식 대안 추가 |

---

## 관련 문서

| 문서              | 설명               |
| ----------------- | ------------------ |
| `SPEC.md`         | 페이지별 기능 명세 |
| `DATA_MODEL.md`   | 엔티티/DTO 정의    |
| `ARCHITECTURE.md` | 프로젝트 아키텍처  |
