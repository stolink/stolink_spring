# AI 팀 → Spring 팀 테스트 준비 완료 안내

> **작성일**: 2026-01-02  
> **상태**: 🚀 테스트 대기 중

---

## ✅ AI Backend 상태

| 항목 | 상태 |
|---|---|
| AI Container | 정상 실행 중 |
| RabbitMQ Queues | 비어있음 (대기 중) |
| Database | 테스트 데이터 존재 |

---

## 🐛 버그 수정 완료

테스트 준비 중 발견된 **무한 재시도 버그**를 수정했습니다.
- 메시지 처리 실패 시 무한 requeue되던 문제 해결
- 이제 실패한 메시지는 버려지고 에러 콜백이 전송됩니다

---

## ▶️ 테스트 시작 요청

**Spring 팀에서 다음 중 하나를 수행해 주세요:**

1. **옵션 A**: 원고 업로드 테스트
   - 테스트용 긴 텍스트(10,000자 이상) 업로드
   - 자동으로 분석 요청이 발행되어야 함

2. **옵션 B**: 수동 분석 트리거
   - `AIAnalysisService.triggerProjectAnalysis(projectId)` 호출
   - DB에 있는 기존 프로젝트 사용 가능

---

## 📋 확인 체크리스트

테스트 후 다음 사항을 확인해 주세요:

- [ ] RabbitMQ에 메시지 발행됨?
- [ ] AI 로그에 `Processing document analysis` 출력됨?
- [ ] 콜백 수신됨? (`/api/ai-callback`)
- [ ] `sections` 테이블에 데이터 생성됨?

---
문제 발생 시 AI 로그와 함께 공유 부탁드립니다:
```bash
docker logs stolink-fastapi-agent
```
