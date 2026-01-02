# Spring 팀 연동 준비 완료 보고

> **작성일**: 2026-01-02  
> **작성자**: Spring Backend 팀  
> **목적**: AI 팀에 연동 테스트 준비 완료 알림

---

## ✅ 완료된 작업

### 1. Docker Compose 설정 업데이트

`docker-compose.spring.yml` 환경 변수 수정:

```yaml
# Agent RabbitMQ (AI 분석용)
APP_RABBITMQ_AGENT_HOST: stolink-rabbitmq
APP_RABBITMQ_AGENT_PORT: 5672
APP_RABBITMQ_AGENT_USERNAME: stolink
APP_RABBITMQ_AGENT_PASSWORD: stolink123
APP_RABBITMQ_AGENT_VIRTUAL_HOST: stolink  # ← 변경됨

# RabbitMQ Queue Names
APP_RABBITMQ_QUEUES_DOCUMENT_ANALYSIS: document_analysis_queue
APP_RABBITMQ_QUEUES_GLOBAL_MERGE: global_merge_queue

# AI Callback URL
APP_CALLBACK_BASE_URL: http://stolink-backend:8080
```

### 2. 테스트 API 엔드포인트 생성

| 엔드포인트 | 설명 |
|-----------|------|
| `POST /api/test/analysis/project/{projectId}/start` | 프로젝트 전체 분석 시작 |
| `POST /api/test/analysis/document/{documentId}/analyze` | 단일 문서 분석 |
| `POST /api/test/analysis/manual` | 수동 메시지 발행 |
| `GET /api/test/analysis/health` | RabbitMQ 연결 확인 |

### 3. Callback 엔드포인트 분기 처리

`/api/ai-callback`에서 `message_type` 분기:
- `DOCUMENT_ANALYSIS_RESULT` → `handleDocumentAnalysisCallback()`
- `GLOBAL_MERGE_RESULT` → `handleGlobalMergeCallback()`

---

## ⚠️ AI 팀 확인 필요

### RabbitMQ vhost 생성

Spring에서 `stolink` vhost로 연결합니다.

**AI 팀의 RabbitMQ에 vhost가 생성되어 있나요?**

확인 방법:
```bash
docker exec rabbitmq rabbitmqctl list_vhosts
```

없다면 생성:
```bash
docker exec rabbitmq rabbitmqctl add_vhost stolink
docker exec rabbitmq rabbitmqctl set_permissions -p stolink stolink ".*" ".*" ".*"
```

---

## 📋 연동 테스트 시작

### Step 1: AI 팀 서버 실행

```bash
cd sto-link-AI-backend
docker-compose -f docker-compose.standalone.yml up
```

### Step 2: Spring 서버 실행

```bash
cd sto-link-backend
docker-compose -f docker-compose.spring.yml up --build
```

### Step 3: 연결 확인

```bash
curl http://localhost:8080/api/test/analysis/health
```

### Step 4: 테스트 메시지 발행

```bash
curl -X POST http://localhost:8080/api/test/analysis/manual \
  -H "Content-Type: application/json" \
  -d '{
    "documentId": "test-doc-001",
    "projectId": "test-project-001",
    "parentFolderId": "test-folder-001",
    "chapterTitle": "제1장"
  }'
```

### Step 5: AI 팀 로그 확인

```bash
docker-compose logs -f fastapi
```

---

## 📞 다음 단계

1. AI 팀에서 vhost 확인 후 연락
2. 양쪽 서버 실행
3. 연동 테스트 진행

---

> 테스트 시작 준비가 되면 알려주세요! 🚀
