# Share Password Inquiry

## Issue Description

공유 비밀번호가 평문으로 DB에 저장되어 있어 보안 취약점이 존재합니다.

- 파일: `src/main/java/com/stolink/backend/domain/share/service/ShareService.java`
- 에러 유형: 🔴 치명적 (보안)

## Review Decision

**사용자 요청에 의해 수정을 연기함 (MVP 단계 제외).**

- 사유: "비밀번호 수정은 빼고" 진행 요청.
- 향후 계획: 정식 릴리즈 전 `PasswordEncoder` 적용 및 마이그레이션 필요.

## Outcome

- **상태**: ⏸️ 연기됨 (Deferred)
