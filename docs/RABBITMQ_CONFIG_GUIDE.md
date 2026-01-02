# RabbitMQ 설정 가이드

> **목적**: AI Backend 팀과의 연동을 위한 RabbitMQ 설정

---

## 📋 필수 설정

### application.yml (또는 application-local.yml)

```yaml
app:
  rabbitmq:
    queues:
      analysis: stolink.analysis.queue           # 기존 단일 문서 분석
      image: stolink.image.queue                 # 이미지 생성
      document-analysis: document_analysis_queue # 대용량 문서 분석 (신규)
      global-merge: global_merge_queue           # 2차 Pass 병합 (신규)
    
    agent:
      host: localhost              # 로컬: localhost, Docker: rabbitmq
      port: 5672
      username: guest
      password: guest
      virtual-host: stolink        # ⚠️ AI 팀과 동일해야 함
    
    image:
      host: localhost
      port: 5672
      username: guest
      password: guest
      virtual-host: /

  callback:
    base-url: http://localhost:8080  # Spring 서버 주소

  ai:
    callback-base-url: http://localhost:8080/api

  analysis:
    max-retry-count: 3  # 분석 실패 시 최대 재시도 횟수
```

---

## 🐰 RabbitMQ Virtual Host 생성

### 방법 1: Management UI

1. http://localhost:15672 접속
2. `guest` / `guest` 로그인
3. Admin → Virtual Hosts → Add a new virtual host
4. Name: `stolink` 입력 → Add virtual host
5. 사용자 권한 설정: `guest` 사용자에게 `stolink` vhost 권한 부여

### 방법 2: CLI

```bash
# Virtual Host 생성
docker exec rabbitmq rabbitmqctl add_vhost stolink

# 사용자 권한 부여
docker exec rabbitmq rabbitmqctl set_permissions -p stolink guest ".*" ".*" ".*"
```

### 방법 3: Docker Compose 설정

```yaml
# docker-compose.yml
rabbitmq:
  image: rabbitmq:3-management
  environment:
    - RABBITMQ_DEFAULT_VHOST=stolink
    - RABBITMQ_DEFAULT_USER=guest
    - RABBITMQ_DEFAULT_PASS=guest
  ports:
    - "5672:5672"
    - "15672:15672"
```

---

## 📊 큐 목록

| 큐 이름 | 용도 | 방향 |
|--------|------|------|
| `stolink.analysis.queue` | 기존 단일 문서 분석 | Spring → Python |
| `document_analysis_queue` | 대용량 문서 분석 (1차 Pass) | Spring → Python |
| `global_merge_queue` | 캐릭터 병합 (2차 Pass) | Spring → Python |
| `stolink.image.queue` | 이미지 생성 | Spring → Python |

---

## ✅ 설정 확인 방법

### 1. Spring 서버 시작 후 확인

```bash
# 테스트 엔드포인트로 연결 확인
curl http://localhost:8080/api/test/analysis/health
```

예상 응답:
```json
{
  "status": 200,
  "data": {
    "rabbitmq": "connected",
    "queue": "document_analysis_queue",
    "callbackUrl": "http://localhost:8080/api/ai-callback",
    "error": "none"
  }
}
```

### 2. RabbitMQ Management UI

http://localhost:15672 → Queues 탭에서:
- `document_analysis_queue` 존재 확인
- `global_merge_queue` 존재 확인

---

## 🧪 테스트 API 엔드포인트

> **주의**: `dev`, `local`, `test` 프로필에서만 활성화됩니다.

| 메서드 | 엔드포인트 | 설명 |
|-------|-----------|------|
| POST | `/api/test/analysis/project/{projectId}/start` | 프로젝트 전체 분석 시작 |
| POST | `/api/test/analysis/document/{documentId}/analyze` | 단일 문서 분석 |
| POST | `/api/test/analysis/manual` | 수동 메시지 발행 |
| GET | `/api/test/analysis/health` | RabbitMQ 연결 확인 |

### 수동 메시지 발행 예시

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

---

## 🔧 문제 해결

### Virtual Host 연결 실패

```
ACCESS_REFUSED - Login was refused using authentication mechanism PLAIN.
```

**해결**: vhost 권한 확인
```bash
docker exec rabbitmq rabbitmqctl list_permissions -p stolink
```

### 큐 없음 오류

```
NOT_FOUND - no queue 'document_analysis_queue' in vhost 'stolink'
```

**해결**: Spring 서버를 먼저 시작하면 자동 생성됨 (Queue 빈 정의 확인)

---

> 설정 완료 후 연동 테스트를 진행하세요! 🚀
