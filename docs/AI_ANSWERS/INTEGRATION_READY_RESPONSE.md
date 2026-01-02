# AI 팀 연동 준비 완료 응답

> **작성일**: 2026-01-02  
> **작성자**: AI Backend 팀 (Python/FastAPI)  
> **참조**: SPRING_INTEGRATION_READY.md

---

## ✅ 확인 완료

### 1. Docker Compose 업데이트 완료

`docker-compose.standalone.yml` 수정사항:

```yaml
# RabbitMQ 설정 (Spring과 일치)
rabbitmq:
  environment:
    RABBITMQ_DEFAULT_USER: stolink
    RABBITMQ_DEFAULT_PASS: stolink123
    RABBITMQ_DEFAULT_VHOST: stolink  # ← vhost 자동 생성

# Python Backend 설정
ai-backend:
  environment:
    RABBITMQ_USER: stolink
    RABBITMQ_PASSWORD: stolink123
    RABBITMQ_VHOST: stolink
    DOCUMENT_ANALYSIS_QUEUE: document_analysis_queue
    GLOBAL_MERGE_QUEUE: global_merge_queue
    SPRING_BACKEND_URL: http://host.docker.internal:8080
```

### 2. vhost 자동 생성

`RABBITMQ_DEFAULT_VHOST: stolink` 설정으로 **컨테이너 시작 시 자동 생성**됩니다.

별도 명령어 실행 불필요!

---

## 🚀 연동 테스트 시작

### Step 1: Python 서버 실행

```bash
cd sto-link-AI-backend

# 기존 볼륨 삭제 (vhost 재생성 위해)
docker-compose -f docker-compose.standalone.yml down -v

# 서버 시작
docker-compose -f docker-compose.standalone.yml up --build
```

### Step 2: RabbitMQ 연결 확인

```bash
# Management UI
http://localhost:15672
# ID: stolink / PW: stolink123
```

### Step 3: Spring 서버 실행

```bash
cd sto-link-backend
docker-compose -f docker-compose.spring.yml up --build
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

### Step 5: Python 로그 확인

```bash
docker-compose -f docker-compose.standalone.yml logs -f ai-backend
```

---

## ⚠️ 주의사항

1. **기존 RabbitMQ 볼륨 삭제 필요**
   - vhost 설정 변경 시 `down -v`로 볼륨 삭제 후 재시작

2. **Spring 콜백 URL**
   - Docker 내부에서 Spring 연결: `http://host.docker.internal:8080`
   - 로컬에서 Spring 연결: `http://localhost:8080`

---

## 📞 준비 완료

Python 서버 시작 준비 완료!

Spring 팀에서 서버 시작하시면 테스트 진행하겠습니다. 🚀
