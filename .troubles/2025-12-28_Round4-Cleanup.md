# AI Review Round 4 Refinements

## Issue Description

최신 AI 코드 리뷰에서 제기된 사소한 경고 및 잠재적 로직 결함을 해결했습니다.

- 파일 및 원인:
  - `DocumentRepository`: 트리 조회 시 부모 테이블 조인으로 인한 중복 결과 가능성 보완 필수.
  - `ShareResponse`: 사용되지 않는 `shareUrl` 필드(Dead Code) 존재.
  - `ShareController`: `@RequestBody`의 일관되지 않은 null 처리 패턴.
- 에러 유형: ⚠️ 경고

## Solution Strategy

1.  **DocumentRepository**: `findByProjectWithParent` 쿼리에 `DISTINCT`를 추가하여 중복 엔티티 반환을 방지하고, `ORDER BY d.order ASC`를 명시하여 정렬 보장.
2.  **ShareResponse**: Dead code인 `shareUrl` 필드 삭제.
3.  **ShareController**: `@RequestBody(required = false)`를 기본값인 `true`로 변경하고 컨트롤러 수준의 수동 null 체크 로직을 제거하여 표준 스프링 패턴 준수.

## Outcome

- **상태**: ✅ 해결됨
- **빌드 결과**: `./gradlew build` 성공
- **검증 방법**: 코드 리뷰 사항을 모두 반영하고 빌드 성공 확인.
