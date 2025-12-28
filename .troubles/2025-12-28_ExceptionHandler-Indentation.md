# GlobalExceptionHandler Indentation Fix

## Issue Description

`GlobalExceptionHandler.handleAccessDenied` 메서드 등의 들여쓰기가 일관되지 않거나 과도한 들여쓰기(8 space)가 적용되어 있었습니다. 코드 가독성을 해치고 AI 리뷰에서 경고를 발생시켰습니다.

- 파일: `src/main/java/com/stolink/backend/global/common/exception/GlobalExceptionHandler.java`
- 에러 유형: ⚠️ 경고 (스타일)

## Solution Strategy

전체 파일의 들여쓰기를 표준 Java 스타일(4 space)로 재포맷팅했습니다.

## Outcome

- **상태**: ✅ 해결됨
- **검증**: 육안 확인 및 빌드 테스트
