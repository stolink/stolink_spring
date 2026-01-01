# 🔐 인증 아키텍처 및 프론트엔드 통합 명세서 (Authentication Spec)

본 문서는 **SPA(Single Page Application)** 환경에서 보안성과 사용자 경험을 극대화하기 위해 채택한 **Hybrid Token 전략**의 기술적 배경과 구현 가이드를 설명합니다.

---

## 1. 아키텍처 철학 (Architecture Decision)

우리는 **Stateless(JWT)의 성능**과 **Stateful(RDB Backed)의 제어권**을 결합한 하이브리드 방식을 채택했습니다.

### **핵심 결정: JWT + RDB-backed Refresh Cookie**

| 구성 요소         | 기술적 선택               | 채택 이유 (Why)                                                                                                                                         |
| ----------------- | ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Access Token**  | **JWT (Stateless)**       | **성능 최적화.** API 요청마다 DB를 조회하지 않고, 서명 검증만으로 빠르게 인증을 처리하기 위함입니다.                                                    |
| **Refresh Token** | **RDB 저장소 (Stateful)** | **보안 통제권 확보.** 순수 JWT의 단점인 '통제 불능'을 극복하고, 토큰 탈취 시 **즉각적인 폐기(Revoke)** 및 **강제 로그아웃** 기능을 구현하기 위함입니다. |

### **1.1 검토된 대안 비교 (Alternatives Considered)**

아키텍처 결정 과정에서 다음 세 가지 옵션을 비교 분석했습니다.

| 옵션                       | 설명                                  | 장점                                                                    | 단점                                                                                                    | 선정 여부       |
| -------------------------- | ------------------------------------- | ----------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- | --------------- |
| **A. Server-side Session** | 전통적인 `JSESSIONID` 방식            | 보안성 최상, 구현 단순                                                  | 트래픽 증가 시 서버 메모리 부하, 멀티 서버(Scale-out) 시 Sticky Session 또는 Redis 필수 (인프라 비용 ↑) | ❌              |
| **B. Stateless JWT**       | 토큰에 모든 정보 포함, 서버 상태 없음 | 구현 간편, 무한 확장성                                                  | **토큰 탈취 시 대응 불가(Critical)**, 로그아웃 기능 구현 불가능, 토큰 사이즈 증가로 대역폭 낭비         | ❌              |
| **C. JWT + RDB (Hybrid)**  | Access(Stateless) + Refresh(RDB)      | **보안과 성능의 균형**, RDB만으로 구현 가능, 탈취 시 강제 로그아웃 가능 | DB 조회 오버헤드 존재 (로그인/갱신 시에만 발생하므로 허용 가능)                                         | ✅ **Selected** |

### **보안 최적화 (Security First)**

1.  **NO LocalStorage**: 브라우저 로컬 스토리지에 토큰을 저장하면 XSS(스크립트 삽입 공격)에 취약해집니다. 악성 스크립트가 `localStorage.getItem`으로 토큰을 탈취할 수 있습니다.
2.  **Yes HttpOnly Cookie**: Refresh Token은 반드시 **`HttpOnly`**, **`Secure`** 속성이 적용된 쿠키로 전달합니다. 이는 자바스크립트가 쿠키에 접근하는 것을 원천 차단하여 XSS 공격을 무력화합니다.

---

## 2. 토큰 수명 주기 관리 및 프론트엔드 책임

프론트엔드 엔지니어는 다음 두 가지 핵심 흐름을 반드시 구현해야 합니다.

### **A. 토큰 관리 전략**

1.  **Access Token (단기 인증권)**

    - **저장 원칙**: **메모리(React State / Context)에만 저장**합니다. 새로고침 시 사라지는 것이 정상입니다.
    - **전송 방식**: API 호출 시 헤더에 포함 (`Authorization: Bearer <token>`)

2.  **Refresh Token (장기 갱신권)**
    - **저장 원칙**: **브라우저 쿠키(Browser Cookie)**가 자동으로 관리합니다. 프론트엔드 코드에서 직접 접근하거나 조작할 필요가 없습니다.
    - **전송 방식**: `/refresh` 호출 시 브라우저가 자동으로 쿠키를 동봉하여 전송합니다.

### **B. 사일런트 리프레시 (Silent Refresh) 메커니즘**

Access Token이 메모리에 있으므로, 앱 실행 시 혹은 만료 시 **사용자가 모르게(Silent)** 재발급받는 로직이 필수입니다.

1.  **최초 진입 (App Initialization)**

    - 앱이 로드되자마자 즉시 `POST /api/auth/refresh`를 호출합니다.
    - 성공 시: 발급받은 Access Token으로 로그인 상태 유지.
    - 실패 시: 로그인 페이지로 리다이렉트 (또는 게스트 모드).

2.  **요청 인터셉터 (Interceptor) & 재시도**
    - API 요청 중 `401 Unauthorized` 발생 시, ** Axios Interceptor**가 이를 가로챕니다.
    - 백그라운드에서 `/refresh`를 호출하여 새 토큰을 받아옵니다.
    - 실패했던 원래 요청 헤더를 갈아끼우고 **재시도(Retry)**합니다.

> **🔥 기술 면접 Point: 동시성 처리는 어떻게? (Race Condition)**
> 페이지 로딩 시 5개의 API를 동시에 호출했는데 토큰이 만료되었다면? 5번의 리프레시 요청이 발생하여 서버에서 토큰 충돌(Rotation 오류)이 날 수 있습니다.
> **해결책:** `isRefreshing` 플래그와 `Promise Queue`를 사용하여, 첫 번째 요청만 리프레시를 수행하고 나머지 4개는 대기했다가 새 토큰을 공유받도록 **Mutex(상호 배제) 패턴**을 구현해야 합니다.

---

## 3. API 명세서 (Specification)

모든 엔드포인트는 `/api/auth`로 시작합니다.

### **1. 로그인 (Login)**

- **POST** `/login`
- **설명**: ID/PW 또는 OAuth 코드를 제출하여 인증합니다.
- **Response**:
  - **Body**: `{ "accessToken": "eyJh...", "user": { ... } }`
  - **Header**: `Set-Cookie: refresh_token=uuid...; HttpOnly; Secure; SameSite=Strict`

### **2. 토큰 갱신 (Silent Refresh)**

- **POST** `/refresh`
- **설명**: HttpOnly 쿠키에 담긴 Refresh Token으로 새 Access Token을 요청합니다.
- **Request Body**: 비워둡니다 (쿠키가 대신함).
- **Response**:
  - **200 OK**: `{ "accessToken": "new_token..." }` (필요 시 Refresh Token도 갱신되어 쿠키 재설정됨)
  - **401 Unauthorized**: 세션 만료. **강제 로그아웃 처리**.

### **3. 로그아웃 (Logout)**

- **POST** `/logout`
- **설명**: 서버 DB에서 토큰을 삭제하고, 브라우저 쿠키를 만료시킵니다.
- **Response**:
  - **Header**: `Set-Cookie: refresh_token=; Max-Age=0` (쿠키 삭제 명령)
- **Frontend Action**: 메모리의 Access Token을 비우고 로그인 화면으로 이동.

---

## 5. [추가] OAuth2 (Google) 로그인 구현 가이드

백엔드 설정은 완료되었습니다. 프론트엔드에 **로그인 버튼**과 **콜백 처리 페이지**를 추가해야 합니다.

### **Step 1: 구글 로그인 버튼 추가**

`AuthPage.tsx` 또는 로그인 컴포넌트에 아래 버튼을 추가하십시오. 백엔드 엔드포인트로 직접 이동해야 합니다.

```tsx
// AuthPage.tsx
const handleGoogleLogin = () => {
  // 백엔드의 OAuth2 인가 URL로 리다이렉트 (새 창이 아님)
  window.location.href = "http://localhost:8080/oauth2/authorization/google";
};

return (
  <div>
    {/* ... 기존 로그인 폼 ... */}

    <div className="divider">OR</div>

    <button type="button" onClick={handleGoogleLogin} className="google-btn">
      Google로 계속하기
    </button>
  </div>
);
```

### **Step 2: 콜백 처리 페이지 (Handler) 구현**

구글 로그인 후 백엔드가 `http://localhost:3000/oauth2/callback?accessToken=...` 주소로 리다이렉트 시킬 것입니다. 이 URL을 처리할 페이지가 필요합니다.

**1. 라우트 추가 (`App.tsx` 등)**

```tsx
<Route path="/oauth2/callback" element={<OAuth2Callback />} />
```

**2. 컴포넌트 구현 (`OAuth2Callback.tsx`)**

```tsx
import { useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuthStore } from "./store/authStore"; // 상태 관리 라이브러리에 맞게 수정

const OAuth2Callback = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const setAccessToken = useAuthStore((state) => state.setAccessToken);

  useEffect(() => {
    const accessToken = searchParams.get("accessToken");

    if (accessToken) {
      // 1. Access Token 저장 (메모리)
      setAccessToken(accessToken);

      // 2. 홈으로 이동 (Refresh Token은 이미 쿠키에 저장됨)
      navigate("/");
    } else {
      console.error("Login Failed: No access token received");
      navigate("/login");
    }
  }, [searchParams, navigate, setAccessToken]);

  return <div>로그인 처리 중...</div>;
};

export default OAuth2Callback;
```

---

## 6. [추가] 자동 로그인 (Silent Refresh) & UX 가이드

사용자가 사이트에 재방문했을 때, **로그인 버튼을 누르지 않아도 자동으로 로그인 상태가 복구**되도록 하려면 아래 로직을 앱 최상단(`App.tsx` 등)에 구현해야 합니다.

### **1. 앱 시작 시 토큰 갱신 시도 (App.tsx)**

`useEffect`를 사용하여 앱이 마운트될 때 한 번 실행합니다.

```tsx
import { useEffect, useRef } from 'react';
import { useAuthStore } from './store/authStore';
import { authService } from './services/authService';

const App = () => {
    const setAccessToken = useAuthStore((state) => state.setAccessToken);
    const isAuthInitialized = useRef(false); // StrictMode 중복 호출 방지

    useEffect(() => {
        // 개발 모드(StictMode)에서 2번 실행되는 것을 방지
        if (isAuthInitialized.current) return;
        isAuthInitialized.current = true;

        const initAuth = async () => {
            try {
                // 쿠키에 있는 Refresh Token으로 Access Token 발급 시도
                const response = await authService.refresh();
                if (response.data.accessToken) {
                    setAccessToken(response.data.accessToken);
                    console.log("자동 로그인 성공");
                }
            } catch (error) {
                // 401/403 등 실패 시 -> 비로그인 상태 유지 (에러 아님, 자연스러운 현상)
                console.log("비로그인 상태입니다.");
            }
        };

        initAuth();
    }, [setAccessToken]);

    return (
        // ... 라우터 및 렌더링 ...
    );
};
```

### **2. UX 시나리오**

1.  **비로그인 접속**: 앱 실행 -> `refresh()` 실패 -> 우상단 "로그인" 버튼 표시.
2.  **로그인**: 유저가 로그인 -> `accessToken` 발급 -> 우상단 "내 프로필"로 변경.
3.  **재접속**: 앱 종료 후 다시 켬 -> `refresh()` 성공 (쿠키 살아있음) -> 자동으로 로그인 상태 전환 (유저는 로그인 끊긴 줄 모름).

---

## 7. 자주 묻는 질문 (FAQ) & 트러블슈팅

**Q: 크롬 개발자 도구 Network 탭 응답에 Refresh Token이 안 보여요.**
**A:** 의도된 설계입니다. 보안 쿠키는 `Set-Cookie` 헤더를 통해서만 전달되며, 브라우저 내부적으로 처리됩니다. Application 탭의 **Cookies** 섹션에서 확인할 수 있습니다.

**Q: 로컬 개발(`localhost`)에서 CORS 에러가 나거나 쿠키가 안 구워져요.**
**A:** 클라이언트 HTTP 라이브러리(Axios 등) 설정에서 `withCredentials: true` 옵션을 반드시 켜야 합니다. 이는 "서로 다른 포트 간에도 쿠키 주고받기를 허용하겠다"는 약속입니다.

**Q: 왜 Redis가 아닌 RDB를 썼나요? (면접 대비)**
**A:** "오버 엔지니어링 방지(YAGNI)" 원칙을 따랐습니다. 현재 규모에서 Postgres의 PK 조회 속도(0.5ms 이하)는 충분히 빠릅니다. **Redis 도입 비용(인프라 관리, 비용)** 대비 성능 이득이 크지 않다고 판단했습니다.

**[심화: 디자인 패턴 적용]**
현재 백엔드 코드의 `AuthService`는 구체적인 DB 구현체가 아닌 **`RefreshTokenRepository` 인터페이스**에 의존하고 있습니다. 이는 **전략 패턴(Strategy Pattern)**의 원리를 따른 설계입니다.

- **현재**: `JpaRefreshTokenRepository` (RDB 전략)가 주입됨.
- **미래**: 트래픽이 늘어나면 `RedisRefreshTokenRepository` (Redis 전략)를 구현하여 주입만 바꿔주면 됩니다. **비즈니스 로직(`AuthService`) 수정 없이** 저장소 전략을 교체할 수 있는 유연한 구조입니다.
