# API 사용 예시

이 파일은 빠른 API 테스트를 위한 curl 명령어 모음입니다.

## 1. 회원가입 및 로그인

```bash
# 회원가입
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "writer@example.com",
    "password": "test123",
    "nickname": "소설작가"
  }'

# 응답 예시:
# {
#   "code": 201,
#   "status": "CREATED",
#   "message": "Created",
#   "data": {
#     "id": "123e4567-e89b-12d3-a456-426614174000",
#     "email": "writer@example.com",
#     "nickname": "소설작가",
#     "avatarUrl": null,
#     "createdAt": "2024-12-25T16:00:00"
#   }
# }

# 로그인
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "writer@example.com",
    "password": "test123"
  }'
```

**중요**: 로그인/회원가입 후 받은 `accessToken`을 이후 요청의 `Authorization: Bearer` 헤더에 사용하세요. `refreshToken`은 쿠키로 자동 관리됩니다.

## 2. 작품 관리

```bash
# 작품 생성
curl -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {accessToken}" \
  -d '{
    "title": "판타지 모험",
    "genre": "fantasy",
    "description": "마법과 모험이 가득한 이야기"
  }'

# 작품 목록 조회 (페이지네이션)
curl -X GET "http://localhost:8080/api/projects?page=1&limit=10" \
  -H "Authorization: Bearer {accessToken}"

# 작품 상세 조회
curl -X GET "http://localhost:8080/api/projects/{projectId}" \
  -H "Authorization: Bearer {accessToken}"
```

## 3. 문서 관리

```bash
# 문서 트리 조회
curl -X GET "http://localhost:8080/api/projects/{projectId}/documents" \
  -H "Authorization: Bearer {accessToken}"

# 폴더 생성
curl -X POST "http://localhost:8080/api/projects/{projectId}/documents" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {accessToken}" \
  -d '{
    "projectId": "{projectId}",
    "type": "folder",
    "title": "1부: 시작"
  }'

# 텍스트 문서 생성
curl -X POST "http://localhost:8080/api/projects/{projectId}/documents" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {accessToken}" \
  -d '{
    "projectId": "{projectId}",
    "parentId": "{folderId}",
    "type": "text",
    "title": "1장: 운명의 만남",
    "synopsis": "주인공이 검을 발견한다",
    "targetWordCount": 3000
  }'

# 문서 내용 수정
curl -X PATCH "http://localhost:8080/api/documents/{documentId}/content" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {accessToken}" \
  -d '{
    "content": "<p>이 검을 가져가거라. 너에게 필요한 물건이다.</p>"
  }'
```

## 4. 캐릭터 및 관계도

```bash
# 캐릭터 생성
curl -X POST "http://localhost:8080/api/projects/{projectId}/characters" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {accessToken}" \
  -d '{
    "name": "아린",
    "role": "protagonist",
    "imageUrl": null,
    "extras": {
      "age": 25,
      "species": "elf",
      "personality": ["용감", "정의로움"]
    }
  }'

# 캐릭터 관계 생성
curl -X POST "http://localhost:8080/api/relationships" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {accessToken}" \
  -d '{
    "sourceId": "{character1Id}",
    "targetId": "{character2Id}",
    "type": "friendly",
    "strength": 8,
    "description": "어린 시절 친구"
  }'

# 관계 포함 캐릭터 조회
curl -X GET "http://localhost:8080/api/projects/{projectId}/relationships" \
  -H "Authorization: Bearer {accessToken}"
```

## 5. AI 분석

```bash
# 분석 요청
curl -X POST "http://localhost:8080/api/ai/analyze" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {accessToken}" \
  -d '{
    "projectId": "{projectId}",
    "documentId": "{documentId}",
    "content": "이 검을 가져가거라. 아린은 검을 받았다.",
    "options": {}
  }'

# 응답:
# {
#   "code": 202,
#   "status": "ACCEPTED",
#   "message": "Analysis started",
#   "data": {
#     "jobId": "abc-123",
#     "status": "processing"
#   }
# }

# 작업 상태 조회
curl -X GET "http://localhost:8080/api/ai/jobs/{jobId}" \
  -H "Authorization: Bearer {accessToken}"
```

## 환경 변수 설정 (선택)

편의를 위해 환경 변수를 설정할 수 있습니다:

```bash
# Windows (PowerShell)
$USER_ID = "your-user-id-here"
$PROJECT_ID = "your-project-id-here"

# Linux/Mac
export ACCESS_TOKEN="your-access-token-here"
export PROJECT_ID="your-project-id-here"

# 그 다음 요청에서:
curl -X GET "http://localhost:8080/api/projects/$PROJECT_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```
