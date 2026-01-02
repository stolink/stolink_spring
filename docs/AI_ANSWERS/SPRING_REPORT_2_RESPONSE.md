# AI 팀 답변 - Spring 구현 보고서 2 확인

> **작성일**: 2026-01-02  
> **작성자**: AI Backend 팀 (Python/FastAPI)  
> **참조**: SPRING_IMPLEMENTATION_REPORT_2.md

---

## ✅ 구현 확인 완료

Spring 팀이 구현한 내용을 확인했습니다:

| 항목 | 상태 | 비고 |
|------|------|------|
| `/api/ai-callback` 분기 처리 | ✅ | `message_type` 기반 분기 |
| DTO `@JsonProperty` | ✅ | snake_case 매핑 완료 |
| pgvector | ⏳ | Phase 1에서는 JSON 유지 |

---

## ❓ 질문에 대한 답변

### 1. RabbitMQ vhost 설정

**필요합니다.**

```yaml
app:
  rabbitmq:
    agent:
      virtual-host: stolink  # ← 필수
```

Python 측 `.env` 및 `config.py`에서:
```python
rabbitmq_vhost: str = "stolink"
```

둘이 일치해야 연결됩니다.

---

### 2. 테스트용 데이터

**Spring 팀에서 생성해 주세요.**

테스트에 필요한 최소 데이터:

```sql
-- 1. 테스트 프로젝트
INSERT INTO projects (id, name) VALUES (
  'test-project-001', 
  '연동 테스트 프로젝트'
);

-- 2. 챕터 폴더 (FOLDER)
INSERT INTO documents (id, project_id, type, title, parent_id) VALUES (
  'test-folder-001',
  'test-project-001',
  'FOLDER',
  '제1장',
  NULL
);

-- 3. 텍스트 문서 (TEXT) - 분석 대상
INSERT INTO documents (id, project_id, type, title, content, parent_id) VALUES (
  'test-doc-001',
  'test-project-001',
  'TEXT',
  '1-1. 도입부',
  '눈을 떴을 때 가장 먼저 느낀 건 차가운 바닥이었다.
  
  이안은 천천히 몸을 일으켰다. 주변은 온통 폐허였다.
  
  "여기가 어디지..."
  
  낯선 목소리가 머릿속에 울렸다.
  
  [시스템 활성화. 사용자 이안을 인식합니다.]',
  'test-folder-001'
);
```

**생성 후 공유해 주세요:**
- Project ID: `test-project-001`
- Document ID (FOLDER): `test-folder-001`
- Document ID (TEXT): `test-doc-001`

---

## 🧪 연동 테스트 계획

### 테스트 순서

1. **RabbitMQ 연결 확인**
   ```bash
   # Python 서버 시작
   docker-compose -f docker-compose.standalone.yml up
   ```

2. **Spring에서 메시지 발행**
   ```java
   DocumentAnalysisMessage msg = DocumentAnalysisMessage.builder()
       .documentId("test-doc-001")
       .projectId("test-project-001")
       .parentFolderId("test-folder-001")
       .chapterTitle("제1장")
       .documentOrder(1)
       .analysisPass(1)
       .callbackUrl("http://localhost:8080/api/ai-callback")
       .build();
   
   rabbitTemplate.convertAndSend("document_analysis_queue", msg);
   ```

3. **Python 로그 확인**
   ```bash
   docker-compose logs -f fastapi
   ```

4. **Spring Callback 수신 확인**

---

## 📞 연락

테스트 데이터 생성이 완료되면 바로 연동 테스트를 시작하겠습니다! 🚀
