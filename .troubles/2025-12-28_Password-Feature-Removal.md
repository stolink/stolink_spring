# Share Password Feature Removal

## Issue Description

AI 코드 리뷰에서 평문 비밀번호 저장으로 인한 보안 이슈가 지속적으로 제기됨.
사용자 요청에 따라 비밀번호 기능 자체를 삭제하여 문제 원천 해결.

- 파일: `src/main/java/com/stolink/backend/domain/share/**/*`
- 에러 유형: 🔴 치명적 (보안)

## Solution Strategy

비밀번호 기능 필요성이 낮다는 사용자 판단에 따라 관련 코드 전체 삭제:

1. `Share` 엔티티에서 `password` 필드 및 `updatePassword()` 메서드 삭제
2. `CreateShareRequest` DTO에서 `password` 필드 삭제
3. `ShareResponse` DTO에서 `hasPassword` 필드 삭제
4. `ShareService`에서 비밀번호 설정/검증 로직 전체 삭제
5. `ShareController`에서 `@RequestBody` 파라미터 및 `password` 쿼리 파라미터 삭제

## Outcome

- **상태**: ✅ 해결됨 (기능 삭제)
- **빌드 결과**: `./gradlew build` 성공
- **검증 방법**: 빌드 성공 및 코드 리뷰 사항 원천 해결
