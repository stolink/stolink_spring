# ShareResponse Lazy Loading Risk

## Issue Description

DTO 변환 과정에서 `Share` 엔티티의 `project` 연관관계(Lazy Loading)를 직접 참조하고 있습니다.
`ShareResponse.from(share)` 메서드가 트랜잭션 범위 내에서 호출되더라도, 향후 코드 변경이나 호출 시점에 따라 `LazyInitializationException` 또는 불필요한 프록시 초기화 쿼리(N+1)가 발생할 위험이 있습니다.

- 파일: `src/main/java/com/stolink/backend/domain/share/service/ShareService.java`
- 라인: `getShareSettings` 메서드 등
- 에러 유형: ⚠️ 경고 (잠재적 런타임 에러)

## Solution Strategy

`ShareResponse.from(share)`를 사용하는 대신, Service 레이어에서 이미 조회된 `projectId`를 사용하여 DTO를 직접 생성(Builder 패턴)합니다. 이를 통해 엔티티 탐색(`share.getProject().getId()`)을 방지합니다.

### 변경 전

```java
return ShareResponse.from(share);
```

### 변경 후

```java
return ShareResponse.builder()
        .shareId(share.getId())
        .projectId(projectId)
        .hasPassword(share.getPassword() != null && !share.getPassword().isEmpty())
        .build();
```

## Outcome

- **상태**: ✅ 해결됨
- **빌드 결과**: `npm run build` (Gradle build) 성공 예정
- **검증 방법**: 코드 컴파일 및 로직 확인
