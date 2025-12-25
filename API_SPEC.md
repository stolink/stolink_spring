# StoLink Backend API 명세서

> **Version**: 1.0.0  
> **Base URL**: `http://localhost:8080/api`  
> **Last Updated**: 2025-12-25

---

## 목차

1. [공통 사항](#1-공통-사항)
2. [인증 (Auth)](#2-인증-auth)
3. [프로젝트 (Projects)](#3-프로젝트-projects)
4. [문서 (Documents)](#4-문서-documents)
5. [캐릭터 (Characters)](#5-캐릭터-characters)
6. [AI 분석 (AI)](#6-ai-분석-ai)
7. [에러 코드](#7-에러-코드)

---

## 1. 공통 사항

### 1.1 요청 형식

- **Content-Type**: `application/json`
- **인증 헤더**: `X-User-Id: {user-uuid}` (로그인 후 필수)

### 1.2 응답 형식

모든 API 응답은 다음 형식을 따릅니다:

**성공 응답:**
```json
{
  "code": 200,
  "status": "OK",
  "message": "OK",
  "data": { ... }
}
```

**에러 응답:**
```json
{
  "code": 400,
  "status": "BAD_REQUEST",
  "message": "에러 메시지",
  "data": null
}
```

### 1.3 HTTP 상태 코드

| 코드 | 설명 |
|------|------|
| `200 OK` | 요청 성공 |
| `201 Created` | 리소스 생성 성공 |
| `202 Accepted` | 비동기 작업 수락 |
| `204 No Content` | 삭제 성공 |
| `400 Bad Request` | 잘못된 요청 |
| `401 Unauthorized` | 인증 필요 |
| `403 Forbidden` | 권한 없음 |
| `404 Not Found` | 리소스 없음 |
| `500 Internal Server Error` | 서버 오류 |

---

## 2. 인증 (Auth)

### 2.1 회원가입

새로운 사용자 계정을 생성합니다.

```
POST /api/auth/register
```

**Request Body:**
```json
{
  "email": "string (required)",
  "password": "string (required)",
  "nickname": "string (required)"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `email` | string | ✓ | 이메일 주소 (고유) |
| `password` | string | ✓ | 비밀번호 |
| `nickname` | string | ✓ | 사용자 닉네임 (최대 100자) |

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "email": "user@example.com",
    "nickname": "작가닉네임",
    "avatarUrl": null,
    "createdAt": "2025-12-25T10:00:00"
  }
}
```

**에러:**
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 400 | EMAIL_DUPLICATED | 이미 사용 중인 이메일 |
| 400 | INVALID_REQUEST | 필수 필드 누락 |

---

### 2.2 로그인

사용자 인증을 수행합니다.

```
POST /api/auth/login
```

**Request Body:**
```json
{
  "email": "string (required)",
  "password": "string (required)"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `email` | string | ✓ | 이메일 주소 |
| `password` | string | ✓ | 비밀번호 |

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "email": "user@example.com",
    "nickname": "작가닉네임",
    "avatarUrl": null,
    "createdAt": "2025-12-25T10:00:00"
  }
}
```

> **Note**: 응답의 `id`를 이후 모든 API 요청의 `X-User-Id` 헤더에 사용합니다.

**에러:**
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 400 | INVALID_CREDENTIALS | 잘못된 이메일 또는 비밀번호 |

---

### 2.3 내 정보 조회

현재 로그인한 사용자의 정보를 조회합니다.

```
GET /api/auth/me
```

**Headers:**
```
X-User-Id: {user-uuid}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "email": "user@example.com",
    "nickname": "작가닉네임",
    "avatarUrl": "https://example.com/avatar.jpg",
    "createdAt": "2025-12-25T10:00:00"
  }
}
```

---

### 2.4 프로필 수정

사용자 프로필을 수정합니다.

```
PATCH /api/auth/me
```

**Headers:**
```
X-User-Id: {user-uuid}
```

**Query Parameters:**
| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `nickname` | string | ✗ | 새 닉네임 |
| `avatarUrl` | string | ✗ | 새 아바타 URL |

**Example:**
```
PATCH /api/auth/me?nickname=새닉네임&avatarUrl=https://example.com/new-avatar.jpg
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "email": "user@example.com",
    "nickname": "새닉네임",
    "avatarUrl": "https://example.com/new-avatar.jpg",
    "createdAt": "2025-12-25T10:00:00"
  }
}
```

---

## 3. 프로젝트 (Projects)

### 3.1 프로젝트 목록 조회

사용자의 프로젝트 목록을 페이지네이션하여 조회합니다.

```
GET /api/projects
```

**Headers:**
```
X-User-Id: {user-uuid}
```

**Query Parameters:**
| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `page` | int | 1 | 페이지 번호 (1부터 시작) |
| `limit` | int | 20 | 페이지당 항목 수 |
| `sort` | string | "updatedAt" | 정렬 기준 필드 |
| `order` | string | "desc" | 정렬 방향 (asc, desc) |

**정렬 가능 필드:**
- `createdAt` - 생성일
- `updatedAt` - 수정일
- `title` - 제목

**Example:**
```
GET /api/projects?page=1&limit=10&sort=updatedAt&order=desc
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "projects": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "title": "판타지 모험",
        "genre": "FANTASY",
        "description": "마법과 모험이 가득한 이야기",
        "coverImage": null,
        "status": "writing",
        "author": "홍길동",
        "stats": {
          "totalWords": 50000,
          "chapterCount": 10
        },
        "createdAt": "2025-12-20T10:00:00",
        "updatedAt": "2025-12-25T15:30:00"
      }
    ],
    "pagination": {
      "page": 1,
      "limit": 10,
      "total": 25,
      "totalPages": 3
    }
  }
}
```

---

### 3.2 프로젝트 생성

새로운 프로젝트를 생성합니다.

```
POST /api/projects
```

**Headers:**
```
X-User-Id: {user-uuid}
Content-Type: application/json
```

**Request Body:**
```json
{
  "title": "string (required)",
  "genre": "string (optional)",
  "description": "string (optional)"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `title` | string | ✓ | 프로젝트 제목 |
| `genre` | string | ✗ | 장르 (FANTASY, ROMANCE, SF, MYSTERY, THRILLER, HORROR, DRAMA, OTHER) |
| `description` | string | ✗ | 프로젝트 설명 |

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "판타지 모험",
    "genre": "FANTASY",
    "description": "마법과 모험이 가득한 이야기",
    "coverImage": null,
    "status": "writing",
    "author": null,
    "stats": null,
    "createdAt": "2025-12-25T10:00:00",
    "updatedAt": "2025-12-25T10:00:00"
  }
}
```

---

### 3.3 프로젝트 상세 조회

특정 프로젝트의 상세 정보를 조회합니다.

```
GET /api/projects/{id}
```

**Headers:**
```
X-User-Id: {user-uuid}
```

**Path Parameters:**
| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `id` | UUID | 프로젝트 ID |

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "판타지 모험",
    "genre": "FANTASY",
    "description": "마법과 모험이 가득한 이야기",
    "coverImage": null,
    "status": "writing",
    "author": "홍길동",
    "stats": {
      "totalWords": 50000,
      "chapterCount": 10
    },
    "createdAt": "2025-12-20T10:00:00",
    "updatedAt": "2025-12-25T15:30:00"
  }
}
```

**에러:**
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 404 | PROJECT_NOT_FOUND | 프로젝트를 찾을 수 없음 |
| 403 | ACCESS_DENIED | 접근 권한 없음 |

---

### 3.4 프로젝트 수정

프로젝트 정보를 수정합니다.

```
PATCH /api/projects/{id}
```

**Headers:**
```
X-User-Id: {user-uuid}
Content-Type: application/json
```

**Path Parameters:**
| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `id` | UUID | 프로젝트 ID |

**Request Body:**
```json
{
  "title": "string (optional)",
  "genre": "string (optional)",
  "description": "string (optional)"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "수정된 제목",
    "genre": "ROMANCE",
    "description": "수정된 설명",
    "coverImage": null,
    "status": "writing",
    "author": null,
    "stats": null,
    "createdAt": "2025-12-20T10:00:00",
    "updatedAt": "2025-12-25T16:00:00"
  }
}
```

---

### 3.5 프로젝트 삭제

프로젝트를 삭제합니다.

```
DELETE /api/projects/{id}
```

**Headers:**
```
X-User-Id: {user-uuid}
```

**Path Parameters:**
| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `id` | UUID | 프로젝트 ID |

**Response (204 No Content):**
- 본문 없음

---

## 4. 문서 (Documents)

### 4.1 문서 트리 조회

프로젝트의 문서를 트리 구조로 조회합니다.

```
GET /api/projects/{pid}/documents
```

**Headers:**
```
X-User-Id: {user-uuid}
```

**Path Parameters:**
| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `pid` | UUID | 프로젝트 ID |

**Response (200 OK):**
```json
{
  "success": true,
  "data": [
    {
      "id": "doc-uuid-1",
      "type": "folder",
      "title": "1부: 시작",
      "order": 0,
      "metadata": {
        "status": "draft",
        "wordCount": 0,
        "createdAt": "2025-12-25T10:00:00"
      },
      "children": [
        {
          "id": "doc-uuid-2",
          "type": "text",
          "title": "1장: 운명의 만남",
          "order": 0,
          "metadata": {
            "status": "draft",
            "wordCount": 2500,
            "createdAt": "2025-12-25T10:30:00"
          },
          "children": []
        },
        {
          "id": "doc-uuid-3",
          "type": "text",
          "title": "2장: 여정의 시작",
          "order": 1,
          "metadata": {
            "status": "revised",
            "wordCount": 3200,
            "createdAt": "2025-12-25T11:00:00"
          },
          "children": []
        }
      ]
    },
    {
      "id": "doc-uuid-4",
      "type": "folder",
      "title": "2부: 모험",
      "order": 1,
      "metadata": {
        "status": "draft",
        "wordCount": 0,
        "createdAt": "2025-12-25T12:00:00"
      },
      "children": []
    }
  ]
}
```

**응답 필드 설명:**

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | UUID | 문서 ID |
| `type` | string | 문서 타입 (folder, text) |
| `title` | string | 문서 제목 |
| `order` | int | 정렬 순서 |
| `metadata.status` | string | 문서 상태 (draft, revised, final) |
| `metadata.wordCount` | int | 단어 수 |
| `metadata.createdAt` | datetime | 생성일 |
| `children` | array | 하위 문서 배열 |

---

### 4.2 문서 생성

새로운 문서(폴더 또는 텍스트)를 생성합니다.

```
POST /api/projects/{pid}/documents
```

**Headers:**
```
X-User-Id: {user-uuid}
Content-Type: application/json
```

**Path Parameters:**
| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `pid` | UUID | 프로젝트 ID |

**Request Body:**
```json
{
  "projectId": "uuid (required)",
  "parentId": "uuid (optional)",
  "type": "string (required)",
  "title": "string (required)",
  "synopsis": "string (optional)",
  "targetWordCount": "int (optional)"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `projectId` | UUID | ✓ | 프로젝트 ID |
| `parentId` | UUID | ✗ | 부모 문서(폴더) ID |
| `type` | string | ✓ | 문서 타입 (folder, text) |
| `title` | string | ✓ | 문서 제목 |
| `synopsis` | string | ✗ | 시놉시스 |
| `targetWordCount` | int | ✗ | 목표 단어 수 |

**Example (폴더 생성):**
```json
{
  "projectId": "550e8400-e29b-41d4-a716-446655440000",
  "type": "folder",
  "title": "1부: 시작"
}
```

**Example (텍스트 문서 생성):**
```json
{
  "projectId": "550e8400-e29b-41d4-a716-446655440000",
  "parentId": "doc-folder-uuid",
  "type": "text",
  "title": "1장: 운명의 만남",
  "synopsis": "주인공이 마법 검을 발견한다",
  "targetWordCount": 3000
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "id": "new-doc-uuid",
    "type": "text",
    "title": "1장: 운명의 만남",
    "order": 0,
    "metadata": {
      "status": "draft",
      "wordCount": 0,
      "createdAt": "2025-12-25T10:00:00"
    },
    "children": []
  }
}
```

---

### 4.3 문서 상세 조회

특정 문서의 전체 정보를 조회합니다.

```
GET /api/documents/{id}
```

**Headers:**
```
X-User-Id: {user-uuid}
```

**Path Parameters:**
| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `id` | UUID | 문서 ID |

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": "doc-uuid",
    "type": "TEXT",
    "title": "1장: 운명의 만남",
    "content": "<p>이 검을 가져가거라. 너에게 필요한 물건이다.</p>",
    "synopsis": "주인공이 마법 검을 발견한다",
    "order": 0,
    "status": "DRAFT",
    "label": null,
    "labelColor": null,
    "wordCount": 150,
    "targetWordCount": 3000,
    "includeInCompile": true,
    "keywords": null,
    "notes": null,
    "createdAt": "2025-12-25T10:00:00",
    "updatedAt": "2025-12-25T15:00:00"
  }
}
```

---

### 4.4 문서 내용 수정

문서의 본문 내용을 수정합니다.

```
PATCH /api/documents/{id}/content
```

**Headers:**
```
X-User-Id: {user-uuid}
Content-Type: application/json
```

**Path Parameters:**
| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `id` | UUID | 문서 ID |

**Request Body:**
```json
{
  "content": "string (required)"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `content` | string | ✓ | HTML 형식의 본문 내용 |

**Example:**
```json
{
  "content": "<p>이 검을 가져가거라. 너에게 필요한 물건이다.</p><p>주인공은 검을 받아들었다.</p>"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": "doc-uuid",
    "wordCount": 250,
    "updatedAt": "2025-12-25T16:00:00"
  }
}
```

> **Note**: `wordCount`는 HTML 태그를 제거하고 자동 계산됩니다.

---

### 4.5 문서 삭제

문서를 삭제합니다.

```
DELETE /api/documents/{id}
```

**Headers:**
```
X-User-Id: {user-uuid}
```

**Path Parameters:**
| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `id` | UUID | 문서 ID |

**Response (204 No Content):**
- 본문 없음

> **Warning**: 폴더를 삭제하면 하위 문서도 함께 삭제됩니다.

---

## 5. 캐릭터 (Characters)

### 5.1 캐릭터 목록 조회

프로젝트의 캐릭터 목록을 조회합니다.

```
GET /api/projects/{pid}/characters
```

**Headers:**
```
X-User-Id: {user-uuid}
```

**Path Parameters:**
| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `pid` | UUID | 프로젝트 ID |

**Response (200 OK):**
```json
{
  "success": true,
  "data": [
    {
      "id": "char-uuid-1",
      "projectId": "project-uuid",
      "name": "아린",
      "role": "protagonist",
      "imageUrl": "https://example.com/arin.jpg",
      "extras": {
        "age": 25,
        "species": "elf",
        "personality": ["용감", "정의로움"]
      },
      "relationships": []
    },
    {
      "id": "char-uuid-2",
      "projectId": "project-uuid",
      "name": "카엘",
      "role": "antagonist",
      "imageUrl": null,
      "extras": {
        "age": 30,
        "species": "human"
      },
      "relationships": []
    }
  ]
}
```

---

### 5.2 관계 포함 캐릭터 조회

캐릭터 간의 관계를 포함하여 조회합니다.

```
GET /api/projects/{pid}/relationships
```

**Headers:**
```
X-User-Id: {user-uuid}
```

**Path Parameters:**
| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `pid` | UUID | 프로젝트 ID |

**Response (200 OK):**
```json
{
  "success": true,
  "data": [
    {
      "id": "char-uuid-1",
      "projectId": "project-uuid",
      "name": "아린",
      "role": "protagonist",
      "imageUrl": "https://example.com/arin.jpg",
      "extras": {
        "age": 25
      },
      "relationships": [
        {
          "type": "friendly",
          "strength": 8,
          "description": "어린 시절 친구",
          "targetCharacter": {
            "id": "char-uuid-2",
            "name": "카엘",
            "role": "supporting"
          }
        },
        {
          "type": "rival",
          "strength": 6,
          "description": "라이벌 관계",
          "targetCharacter": {
            "id": "char-uuid-3",
            "name": "다르크",
            "role": "antagonist"
          }
        }
      ]
    }
  ]
}
```

---

### 5.3 캐릭터 생성

새로운 캐릭터를 생성합니다.

```
POST /api/projects/{pid}/characters
```

**Headers:**
```
X-User-Id: {user-uuid}
Content-Type: application/json
```

**Path Parameters:**
| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `pid` | UUID | 프로젝트 ID |

**Request Body:**
```json
{
  "name": "string (required)",
  "role": "string (optional)",
  "imageUrl": "string (optional)",
  "extras": "object (optional)"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `name` | string | ✓ | 캐릭터 이름 |
| `role` | string | ✗ | 역할 (protagonist, antagonist, supporting, mentor, sidekick, other) |
| `imageUrl` | string | ✗ | 이미지 URL |
| `extras` | object | ✗ | 추가 정보 (나이, 종족, 성격 등) |

**Example:**
```json
{
  "name": "아린",
  "role": "protagonist",
  "imageUrl": null,
  "extras": {
    "age": 25,
    "species": "elf",
    "personality": ["용감", "정의로움"],
    "skills": ["검술", "마법"]
  }
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "id": "new-char-uuid",
    "projectId": "project-uuid",
    "name": "아린",
    "role": "protagonist",
    "imageUrl": null,
    "extras": {
      "age": 25,
      "species": "elf",
      "personality": ["용감", "정의로움"],
      "skills": ["검술", "마법"]
    },
    "relationships": []
  }
}
```

---

### 5.4 캐릭터 관계 생성

두 캐릭터 간의 관계를 생성합니다.

```
POST /api/relationships
```

**Headers:**
```
X-User-Id: {user-uuid}
Content-Type: application/json
```

**Request Body:**
```json
{
  "sourceId": "string (required)",
  "targetId": "string (required)",
  "type": "string (required)",
  "strength": "int (optional)",
  "description": "string (optional)"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `sourceId` | string | ✓ | 출발 캐릭터 ID |
| `targetId` | string | ✓ | 도착 캐릭터 ID |
| `type` | string | ✓ | 관계 타입 (friendly, rival, family, romantic, mentor, other) |
| `strength` | int | ✗ | 관계 강도 (1-10) |
| `description` | string | ✗ | 관계 설명 |

**관계 타입:**
| 타입 | 설명 |
|------|------|
| `friendly` | 우호적 관계 |
| `rival` | 라이벌/적대 관계 |
| `family` | 가족 관계 |
| `romantic` | 연인/로맨스 관계 |
| `mentor` | 스승-제자 관계 |
| `other` | 기타 |

**Example:**
```json
{
  "sourceId": "char-uuid-1",
  "targetId": "char-uuid-2",
  "type": "friendly",
  "strength": 8,
  "description": "어린 시절 친구, 함께 모험을 떠남"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": null
}
```

---

### 5.5 캐릭터 삭제

캐릭터를 삭제합니다.

```
DELETE /api/characters/{id}
```

**Headers:**
```
X-User-Id: {user-uuid}
```

**Path Parameters:**
| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `id` | string | 캐릭터 ID |

**Response (204 No Content):**
- 본문 없음

> **Warning**: 캐릭터를 삭제하면 관련된 모든 관계도 함께 삭제됩니다.

---

## 6. AI 분석 (AI)

### 6.1 AI 분석 요청

문서 내용에 대한 AI 분석을 요청합니다. 비동기로 처리됩니다.

```
POST /api/ai/analyze
```

**Headers:**
```
X-User-Id: {user-uuid}
Content-Type: application/json
```

**Request Body:**
```json
{
  "projectId": "string (required)",
  "documentId": "string (required)",
  "content": "string (required)",
  "options": "object (optional)"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `projectId` | UUID | ✓ | 프로젝트 ID |
| `documentId` | UUID | ✓ | 문서 ID |
| `content` | string | ✓ | 분석할 텍스트 내용 |
| `options` | object | ✗ | 분석 옵션 |

**Example:**
```json
{
  "projectId": "550e8400-e29b-41d4-a716-446655440000",
  "documentId": "doc-uuid",
  "content": "아린은 검을 받아들었다. 카엘이 그녀를 바라보았다.",
  "options": {
    "extractCharacters": true,
    "detectForeshadowing": true
  }
}
```

**Response (202 Accepted):**
```json
{
  "success": true,
  "data": {
    "jobId": "job-uuid-12345",
    "status": "processing"
  }
}
```

> **Note**: 분석 결과는 비동기로 처리되며, 콜백 URL로 결과가 전달됩니다.

---

### 6.2 작업 상태 조회

AI 분석 작업의 현재 상태를 조회합니다.

```
GET /api/ai/jobs/{jobId}
```

**Headers:**
```
X-User-Id: {user-uuid}
```

**Path Parameters:**
| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `jobId` | string | 작업 ID |

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "jobId": "job-uuid-12345",
    "status": "processing"
  }
}
```

**작업 상태:**
| 상태 | 설명 |
|------|------|
| `processing` | 처리 중 |
| `completed` | 완료 |
| `failed` | 실패 |

---

### 6.3 내부 콜백 API (AI Worker용)

> **Note**: 이 API는 내부 AI Worker에서만 호출합니다.

#### 분석 결과 콜백

```
POST /api/internal/ai/analysis/callback
```

**Request Body:**
```json
{
  "jobId": "string",
  "status": "string",
  "result": {
    "characters": [...],
    "foreshadowing": [...],
    "summary": "..."
  },
  "error": "string (if failed)"
}
```

#### 이미지 생성 결과 콜백

```
POST /api/internal/ai/image/callback
```

**Request Body:**
```json
{
  "jobId": "string",
  "characterId": "string",
  "status": "string",
  "imageUrl": "string",
  "error": "string (if failed)"
}
```

---

## 7. 에러 코드

### 7.1 공통 에러

| 에러 코드 | HTTP 상태 | 설명 |
|----------|----------|------|
| `INVALID_REQUEST` | 400 | 잘못된 요청 형식 |
| `UNAUTHORIZED` | 401 | 인증 필요 |
| `ACCESS_DENIED` | 403 | 접근 권한 없음 |
| `NOT_FOUND` | 404 | 리소스를 찾을 수 없음 |
| `INTERNAL_ERROR` | 500 | 서버 내부 오류 |

### 7.2 인증 에러

| 에러 코드 | HTTP 상태 | 설명 |
|----------|----------|------|
| `EMAIL_DUPLICATED` | 400 | 이미 사용 중인 이메일 |
| `INVALID_CREDENTIALS` | 400 | 잘못된 이메일 또는 비밀번호 |
| `USER_NOT_FOUND` | 404 | 사용자를 찾을 수 없음 |

### 7.3 프로젝트 에러

| 에러 코드 | HTTP 상태 | 설명 |
|----------|----------|------|
| `PROJECT_NOT_FOUND` | 404 | 프로젝트를 찾을 수 없음 |

### 7.4 문서 에러

| 에러 코드 | HTTP 상태 | 설명 |
|----------|----------|------|
| `DOCUMENT_NOT_FOUND` | 404 | 문서를 찾을 수 없음 |
| `INVALID_PARENT` | 400 | 잘못된 부모 문서 |

### 7.5 캐릭터 에러

| 에러 코드 | HTTP 상태 | 설명 |
|----------|----------|------|
| `CHARACTER_NOT_FOUND` | 404 | 캐릭터를 찾을 수 없음 |
| `RELATIONSHIP_ALREADY_EXISTS` | 400 | 이미 존재하는 관계 |

---

## 부록

### A. curl 사용 예시

자세한 curl 사용 예시는 [API_EXAMPLES.md](./API_EXAMPLES.md)를 참조하세요.

### B. Postman Collection

Postman에서 API를 테스트하려면 다음 환경변수를 설정하세요:

| 변수명 | 값 | 설명 |
|--------|-----|------|
| `baseUrl` | http://localhost:8080/api | API 기본 URL |
| `userId` | (로그인 후 설정) | 사용자 UUID |
| `projectId` | (프로젝트 생성 후 설정) | 프로젝트 UUID |

### C. 업데이트 이력

| 버전 | 날짜 | 변경 사항 |
|------|------|----------|
| 1.0.0 | 2025-12-25 | 최초 작성 |
