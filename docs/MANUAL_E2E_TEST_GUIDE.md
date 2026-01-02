# Manual End-to-End Test Guide

대용량 원고 업로드부터 AI 분석 요청 발행까지의 전체 과정을 수동으로 검증하기 위한 가이드입니다.

---

## 🛠 1. 사전 준비 (Prerequisites)

모든 서버가 실행 상태여야 합니다.

```bash
docker ps
# stolink-backend, stolink-postgres, rabbitmq 등이 실행 중이어야 함
```

---

## 📝 2. 테스트 스크립트 작성 (Python)

대용량 JSON은 `curl`로 보내기 어렵기 때문에, Python 스크립트를 사용하여 (1) 대용량 텍스트 생성 후 (2) 업로드 API를 호출합니다.

**파일 생성**: `test_e2e.py`

```python
import requests
import json
import time

# 설정
BASE_URL = "http://localhost:8080/api"
EMAIL = "integration@test.com"
PASSWORD = "password123"
PROJECT_ID = "550e8400-e29b-41d4-a716-446655440000"  # 실제 프로젝트 ID로 변경 필요

def login():
    res = requests.post(f"{BASE_URL}/auth/login", json={"email": EMAIL, "password": PASSWORD})
    if res.status_code != 200:
        print(f"Login failed: {res.text}")
        exit(1)
    return res.json()['data']['accessToken']

def generate_large_text(chapters=100):
    print(f"Generating novel with {chapters} chapters...")
    content = ""
    for i in range(1, chapters + 1):
        content += f"\n\n제{i}장: 테스트 챕터 {i}\n\n"
        content += f"이것은 {i}번째 챕터의 내용입니다. " * 50  # 챕터당 약 1KB
    return content

def upload_manuscript(token, content):
    print(f"Uploading manuscript ({len(content)} chars)...")
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    payload = {
        "projectId": PROJECT_ID,
        "content": content,
        "createFolders": True
    }
    
    # 3. Upload & Trigger Analysis
    # 이 API는 내부적으로 ManuscriptJob 생성 -> 파싱 -> DB저장 -> AI분석요청(Batch) 까지 수행합니다.
    res = requests.post(f"{BASE_URL}/projects/{PROJECT_ID}/manuscript/upload", headers=headers, json=payload)
    
    if res.status_code == 202:
        print("Upload accepted! Job ID:", res.json()['data']['jobId'])
        return res.json()['data']['jobId']
    else:
        print(f"Upload failed: {res.status_code} {res.text}")
        exit(1)

def monitor_job(token, job_id):
    print("Monitoring Job Status...")
    while True:
        res = requests.get(f"{BASE_URL}/jobs/{job_id}", headers={"Authorization": f"Bearer {token}"})
        data = res.json()['data']
        status = data['status']
        progress = data['progress']
        print(f"Status: {status} ({progress}%) - {data.get('message', '')}")
        
        if status in ['COMPLETED', 'FAILED']:
            break
        time.sleep(1)

if __name__ == "__main__":
    token = login()
    text = generate_large_text(chapters=50) # 50챕터 생성
    job_id = upload_manuscript(token, text)
    monitor_job(token, job_id)
```

---

## ▶️ 3. 테스트 실행

### 1단계: 스크립트 실행
```bash
# 의존성 설치 (필요시)
pip install requests

# 실행
python test_e2e.py
```

### 2단계: 결과 확인 (Logs)
스크립트가 `COMPLETED`를 출력하면, 백엔드 로그에서 다음 메시지를 확인하세요.

```bash
docker logs --tail 100 stolink-backend
```

**확인할 로그 패턴**:
1. `Created manuscript job: ...`
2. `Triggering AI analysis for project: ...`
3. `Batch analysis tasks sent: 50/50 successful` (중요: Batch 발행 성공 확인)

---

## 🔎 4. (선택) AI Worker 시뮬레이션

실제 AI 서버가 RabbitMQ에 연결되어 있지 않다면, 보낸 메시지가 큐에 쌓이기만 합니다.
원한다면 `mock_ai_worker.py` (이전에 만든 스크립트)를 실행하여 메시지를 소비하고 콜백을 보낼 수 있습니다.
