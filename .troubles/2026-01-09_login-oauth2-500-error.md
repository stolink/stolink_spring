# 로그인/OAuth2 500 Internal Server Error 트러블슈팅

**날짜**: 2026-01-09  
**상태**: ✅ 해결됨  
**영향 범위**: stolink_spring 백엔드  

---

## 증상

- stolink 프론트엔드에서 로그인 시 **500 Internal Server Error** 발생
- OAuth2 Google 로그인 시에도 동일한 500 에러 발생
- storead는 정상 로그인 가능

---

## 에러 로그

### 1차 에러: 컬럼 미존재
```
PSQLException: ERROR: column u1_0.ai_suggestion_notification does not exist
```

### 2차 에러: JSONB 타입 캐스팅 실패
```
PSQLException: ERROR: column "inventory_json" cannot be cast automatically to type jsonb
Hint: You might need to specify "USING inventory_json::jsonb".
```

여러 테이블의 `*_json` 컬럼들이 TEXT 타입으로 되어있었으나, Hibernate 엔티티에서 JSONB 타입으로 정의됨.

### 3차 에러: pgvector 확장 미활성화
```
PSQLException: ERROR: type "vector" does not exist
```

### 4차 에러: OAuth2SuccessHandler NullPointerException
```
java.lang.NullPointerException: Cannot invoke "String.length()" because "<parameter1>" is null
  at java.base/java.util.UUID.fromString(Unknown Source)
  at com.stolink.backend.global.security.oauth2.OAuth2SuccessHandler.onAuthenticationSuccess(OAuth2SuccessHandler.java:47)
```

Docker 이미지가 최신 코드로 빌드되지 않아 발생.

---

## 원인 분석

| 원인 | 설명 |
|------|------|
| **스키마 불일치** | User 엔티티에 알림 설정 필드들이 추가되었으나 DB에는 컬럼이 없었음 |
| **타입 불일치** | 여러 테이블의 JSON 컬럼들이 TEXT 타입이었으나 엔티티에서 JSONB로 정의 |
| **pgvector 미설치** | PostgreSQL에 vector 확장이 활성화되지 않음 |
| **Docker 캐싱 문제** | Gradle 빌드 없이 Docker 빌드만 해서 캐싱된 JAR 사용 |

---

## 해결 방법

### 1. users 테이블에 알림 컬럼 추가
```sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS goal_notification BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN IF NOT EXISTS foreshadowing_notification BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS ai_suggestion_notification BOOLEAN NOT NULL DEFAULT true;
```

### 2. pgvector 확장 활성화
```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

### 3. TEXT → JSONB 타입 변환

#### characters 테이블 (10개 컬럼)
```sql
ALTER TABLE characters ALTER COLUMN aliases_json TYPE jsonb USING aliases_json::jsonb;
ALTER TABLE characters ALTER COLUMN appearance_json TYPE jsonb USING appearance_json::jsonb;
ALTER TABLE characters ALTER COLUMN current_mood_json TYPE jsonb USING current_mood_json::jsonb;
ALTER TABLE characters ALTER COLUMN embedding_json TYPE jsonb USING embedding_json::jsonb;
ALTER TABLE characters ALTER COLUMN inventory_json TYPE jsonb USING inventory_json::jsonb;
ALTER TABLE characters ALTER COLUMN meta_json TYPE jsonb USING meta_json::jsonb;
ALTER TABLE characters ALTER COLUMN personality_json TYPE jsonb USING personality_json::jsonb;
ALTER TABLE characters ALTER COLUMN profile_json TYPE jsonb USING profile_json::jsonb;
ALTER TABLE characters ALTER COLUMN relations_json TYPE jsonb USING relations_json::jsonb;
ALTER TABLE characters ALTER COLUMN visual_json TYPE jsonb USING visual_json::jsonb;
```

#### consistency_reports 테이블 (4개 컬럼)
```sql
ALTER TABLE consistency_reports ALTER COLUMN conflicts_json TYPE jsonb USING conflicts_json::jsonb;
ALTER TABLE consistency_reports ALTER COLUMN neo4j_validation_json TYPE jsonb USING neo4j_validation_json::jsonb;
ALTER TABLE consistency_reports ALTER COLUMN resolution_summary_json TYPE jsonb USING resolution_summary_json::jsonb;
ALTER TABLE consistency_reports ALTER COLUMN warnings_json TYPE jsonb USING warnings_json::jsonb;
```

#### plot_integrations 테이블 (5개 컬럼)
```sql
ALTER TABLE plot_integrations ALTER COLUMN foreshadowing_json TYPE jsonb USING foreshadowing_json::jsonb;
ALTER TABLE plot_integrations ALTER COLUMN multimedia_summary_json TYPE jsonb USING multimedia_summary_json::jsonb;
ALTER TABLE plot_integrations ALTER COLUMN narrative_beats_json TYPE jsonb USING narrative_beats_json::jsonb;
ALTER TABLE plot_integrations ALTER COLUMN tension_curve_json TYPE jsonb USING tension_curve_json::jsonb;
ALTER TABLE plot_integrations ALTER COLUMN three_act_structure_json TYPE jsonb USING three_act_structure_json::jsonb;
```

#### settings 테이블 (1개 컬럼)
```sql
ALTER TABLE settings ALTER COLUMN static_objects_json TYPE jsonb USING static_objects_json::jsonb;
```

#### validation_results 테이블 (2개 컬럼)
```sql
ALTER TABLE validation_results ALTER COLUMN data_completeness_json TYPE jsonb USING data_completeness_json::jsonb;
ALTER TABLE validation_results ALTER COLUMN validation_details_json TYPE jsonb USING validation_details_json::jsonb;
```

### 4. Docker 이미지 리빌드

```bash
# 1. Gradle 빌드 (JAR 파일 생성)
.\gradlew.bat bootJar --no-daemon

# 2. Docker 이미지 리빌드 (캐시 무시)
docker-compose -f docker-compose.local.yml build --no-cache backend

# 3. 컨테이너 재시작
docker-compose -f docker-compose.local.yml up -d backend
```

---

## 검증

```bash
# 컨테이너 상태 확인
docker ps --filter "name=stolink-backend" --format "table {{.Names}}\t{{.Status}}"

# 결과: stolink-backend   Up About a minute (healthy)
```

---

## 교훈 및 예방책

1. **스키마 변경 시**: 엔티티 변경 후 DB 마이그레이션 스크립트 작성 필수
2. **ddl-auto 설정**: prod 환경은 `validate`로 설정하여 스키마 불일치 조기 발견
3. **Docker 빌드 시**: 코드 변경 후에는 반드시 Gradle 빌드 → Docker 빌드 순서로 진행
4. **pgvector**: PostgreSQL 컨테이너 초기화 시 확장 자동 활성화 스크립트 추가 권장

---

## 관련 파일

- `src/main/java/com/stolink/backend/domain/user/entity/User.java` - 알림 필드 정의
- `src/main/java/com/stolink/backend/global/security/oauth2/OAuth2SuccessHandler.java` - OAuth2 핸들러
- `docker-compose.local.yml` - 로컬 Docker 구성
