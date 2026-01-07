---
description: 백엔드 아키텍처 원칙 검증 프로토콜
---

# Backend Verification Protocol

> PR 생성 전 CLAUDE.md 준수 여부 검증

## 검증 체크리스트

### 1. 아키텍처

- [ ] DTO는 Record 타입 (불변)
- [ ] Controller → Service → Repository 분리
- [ ] Entity 직접 노출 없음 (`ApiResponse<Dto>`)
- [ ] `@Transactional` 적절 (읽기: readOnly=true)

### 2. DB 무결성

- [ ] PostgreSQL: N+1 방지 (Fetch Join/@EntityGraph)
- [ ] Neo4j: 관계/ID만 저장, 깊이 제한 있음
- [ ] QueryDSL Projection 사용

### 3. 비동기/외부연동

- [ ] AI 작업: RabbitMQ 비동기
- [ ] 외부 API: WebClient + Timeout + Circuit Breaker
- [ ] 메시지 발행: 트랜잭션 커밋 후

### 4. 테스트/품질

- [ ] Service: 단위 테스트
- [ ] Repository: @DataJpaTest/Testcontainers
- [ ] SLF4J 로깅 (System.out 없음)

## 핵심 원칙

1. **Neo4j=지도, Postgres=도서관** - 혼용 금지
2. **AI=비서** - 응답 지연이 서버 블로킹 불가 (비동기)
3. **모든 입력 검증** - Jakarta Validation
