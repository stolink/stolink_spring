# Image Job Polling 500 Error (NPE)

## Issue Description

프론트엔드에서 이미지 생성 작업 상태를 폴링할 때 `/api/ai/image/jobs/{jobId}` 엔드포인트에서 500 Internal Server Error가 발생.

- 파일: `AIController.java`
- 라인: 128-134
- 에러 유형: 🔴 치명적 (NPE)

## Root Cause

`getImageJobStatus` 메서드에서 `Map.of()`를 사용해 응답을 구성할 때, `task.getProjectId().toString()` 및 `task.getCharacterId().toString()` 호출 시 해당 필드가 `null`인 경우 `NullPointerException`이 발생.

```java
// 문제 코드
return ApiResponse.ok(Map.of(
    "projectId", task.getProjectId().toString(), // NPE if null
    "characterId", task.getCharacterId().toString(), // NPE if null
    ...
));
```

## Solution Strategy

`HashMap`을 사용하고 null 체크를 추가하여 안전하게 응답 구성.

### 변경 전

```java
return ApiResponse.ok(Map.of(
    "jobId", task.getJobId(),
    "projectId", task.getProjectId().toString(),
    "characterId", task.getCharacterId().toString(),
    ...
));
```

### 변경 후

```java
java.util.Map<String, Object> response = new java.util.HashMap<>();
response.put("jobId", task.getJobId());
response.put("status", task.getStatus().name());
response.put("imageUrl", task.getImageUrl() != null ? task.getImageUrl() : "");
response.put("errorMessage", task.getErrorMessage() != null ? task.getErrorMessage() : "");

if (task.getProjectId() != null) {
    response.put("projectId", task.getProjectId().toString());
}
if (task.getCharacterId() != null) {
    response.put("characterId", task.getCharacterId().toString());
}

return ApiResponse.ok(response);
```

## Outcome

- **상태**: ✅ 해결됨
- **빌드 결과**: `./gradlew clean compileJava` 성공
- **검증 방법**: 이미지 생성 작업 폴링 API 호출 시 500 에러 없이 정상 응답 확인
