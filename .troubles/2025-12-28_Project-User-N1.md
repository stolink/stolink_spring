# Project User N+1 Issue

## Issue Description

`ShareService.getShareSettings` 및 `createShareLink`에서 `project.getUser().getId()`를 호출하여 권한을 검증합니다. `Project.user`가 LAZY 로딩일 경우, `findById`로 프로젝트만 조회한 상태에서 User를 접근하면 추가 쿼리가 발생합니다. 트랜잭션 범위 내라 에러는 안 나지만 성능 저하 원인이 됩니다.

- 파일: `src/main/java/com/stolink/backend/domain/share/service/ShareService.java`
- 라인: 38, 57 (`project.getUser().getId()` 호출 시점)
- 에러 유형: 🔴 치명적 (성능)

## Solution Strategy

`ProjectRepository`에 `user`를 패치 조인으로 함께 조회하는 메서드를 추가하거나, EntityGraph를 사용합니다.

### 변경 전

```java
// ShareService
Project project = projectRepository.findById(projectId).orElseThrow(...);
if (!project.getUser().getId().equals(userId)) ...
```

### 변경 후

```java
// ProjectRepository
@Query("SELECT p FROM Project p JOIN FETCH p.user WHERE p.id = :id")
Optional<Project> findByIdWithUser(@Param("id") UUID id);

// ShareService
Project project = projectRepository.findByIdWithUser(projectId).orElseThrow(...);
```

## Outcome

- **상태**: ✅ 해결됨
- **검증**: 빌드 및 코드 리뷰
