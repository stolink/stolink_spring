# Document Tree Performance Consideration

## Issue Description

프로젝트의 문서를 트리 구조로 반환하기 위해 `findAllByProjectWithParent`를 사용하여 전체 문서를 메모리에 로드한 후 구성하고 있습니다.
문서 수가 많아질 경우 메모리 사용량 증가 및 응답 지연이 발생할 수 있습니다.
사용자는 페이지네이션 또는 깊이 제한 쿼리를 제안했습니다.

- 파일: `src/main/java/com/stolink/backend/domain/share/service/ShareService.java`
- 에러 유형: ⚠️ 경고 (성능)

## Architectural Decision

현재 API 스펙은 전체 트리 구조 (`SharedProjectResponse`) 반환을 전제로 하고 있습니다.
페이징이나 Lazy Loading을 적용하려면 프론트엔드 API 계약 변경 및 복잡한 재귀적 쿼리(Recursive CTE)가 필요합니다.
따라서 **MVP 단계에서는 전체 로딩 방식을 유지**하되, N+1 문제(`@JoinFetch parent`)는 해결하여 쿼리 수는 최소화했습니다.
향후 대용량 프로젝트 지원 시 API 구조 변경(루트만 조회 후 Lazy loading)을 검토하기로 합니다.

## Outcome

- **상태**: ⏸️ 보류 (Architectural Decision)
- **조치**: `MAX_TREE_DEPTH`상수로 깊이 제한 로직은 존재함.
