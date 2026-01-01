# 🛠️ 백엔드 구현 과제: 고급 인증 기능 (Advanced Auth)

본 문서는 [ADR-001]에 기반하여, 인증 시스템의 보안성과 안정성을 강화하기 위한 백엔드 구현 로드맵입니다.

## 0. 아키텍처 검증 (Distributed System Validation)

**결론: 현재 설계(RDB + HttpOnly Cookie)는 Scale-out 환경에서 완벽하게 동작합니다.**

- **Sticky Session 불필요**: 쿠키에는 `JSESSIONID`가 아닌 **데이터(Refresh Token)**가 들어있으며, 모든 서버가 **공유 DB**를 참조하여 검증하므로, 어떤 서버가 요청을 처리하든 결과는 동일합니다.
- **Stateless Server**: 서버 메모리에 세션 상태를 저장하지 않으므로 오토스케일링에 자유롭습니다.

---

## 1. 토큰 데이터 수명 주기 관리 (Token Cleanup)

RDB에 `RefreshToken`이 무한히 쌓이는 것을 방지하기 위해 배치(Batch) 작업을 구현해야 합니다.

- [ ] **스케줄링 작업 생성 (`TokenCleanupService`)**
  - `@Scheduled(cron = "0 0 3 * * *")` (매일 새벽 3시 실행) 적용.
  - `deleteExpiredTokens()`: 만료일(`expiryDate`)이 현재 시간보다 이전인 레코드를 Hard Delete.
  - **Monitoring**: 삭제된 토큰 개수를 로그로 남겨 데이터 추이 관찰.

## 2. 쿠키 보안 및 도메인 전략 (Cookie & Domain)

단순한 쿠키 전송을 넘어, 배포 환경을 고려한 정교한 보안 설정이 필요합니다.

- [ ] **`AuthController` 쿠키 로직 고도화**
  - 단순 `String` 반환이 아닌 `ResponseCookie` 객체 사용.
  - **핵심 속성 적용**:
    - `httpOnly: true`: 자바스크립트 접근 불가 (XSS 방어).
    - `secure: true`: HTTPS 통신에서만 전송 (Local에서도 API 테스트 시 필요할 수 있음).
    - `sameSite: Strict` (또는 `Lax`): CSRF 공격 방어.
  - **도메인 정책 설정 (환경별 분리 필요)**:
    - **Local**: 설정 없음 (`localhost` 자동 적용).
    - **Prod**: `domain("service.com")`와 같이 상위 도메인을 지정하여 `api.service.com`과 `www.service.com` 간 쿠키 공유 허용 전략 수립.

## 3. 동시성 및 정보 보호 (Concurrency & Integrity)

짧은 시간 내 다수의 요청이 발생할 때 토큰이 꼬이거나 탈취되는 것을 방지합니다.

- [ ] **경쟁 상태(Race Condition) 제어**
  - 프론트엔드에서 요청 큐(Queue)를 구현하는 것이 1차적 방어선이지만, 백엔드에서도 방어 로직 필요.
  - **Option A (DB Lock)**: `RefreshTokenRepository` 조회 시 `@Lock(LockModeType.PESSIMISTIC_WRITE)`를 걸어 동시 갱신 방지.
  - **Option B (Graceful Fail)**: 이미 사용된 토큰으로 요청 시, 명확한 에러 코드(`TokenReusedException`)를 반환하여 클라이언트가 강제 로그아웃 후 재로그인하도록 유도.

## 4. 테스트 전략 (Testing Strategy)

OAuth2와 DB 의존성이 있는 로직을 견고하게 검증합니다.

- [ ] **Repository 단위 테스트** (`@DataJpaTest`)
  - `save`, `findByToken` 기본 동작 검증.
  - **Cascade Delete 검증**: `User` 삭제 시 연관된 `RefreshToken`도 함께 삭제되는지 확인.
- [ ] **서비스 로직 테스트**
  - 만료된 토큰으로 접근 시 예외 발생 여부.
  - 로그아웃 시 DB 및 쿠키 만료 헤더가 정상 동작하는지 검증.
- [ ] **통합 테스트** (`@SpringBootTest`)
  - 전체 흐름 시뮬레이션: 로그인 -> Access Token 발급 -> (시간 경과 가정) -> Refresh 요청 -> 새 토큰 발급 성공.
