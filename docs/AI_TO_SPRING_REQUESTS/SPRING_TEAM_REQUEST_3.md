# Spring 팀 요청사항 (Request #3)

> **작성일**: 2026-01-02  
> **주제**: 대용량 데이터 처리 고도화 및 인프라 최적화 요청  
> **참고 문서**: `docs/big_data_processing.md` (Q7 섹션)

---

## 1. 🐘 PostgreSQL pgvector 도입 요청 (Priority: High)

현재 `sections` 테이블의 `embedding` 컬럼은 JSONB 또는 일반 배열 형태로 관리되고 있어, 벡터 유사도 검색(Semantic Search) 속도에 한계가 있습니다. 이를 `pgvector`로 전환 요청드립니다.

### 1-1. Docker 환경 업데이트
PostgreSQL 이미지에 `pgvector` 확장이 설치되어 있어야 합니다.
- **예시**: `ankane/pgvector` 이미지 사용 또는 기존 Postgres 이미지에 pgvector 빌드

### 1-2. DB 스키마 변경
`sections` 테이블의 스키마를 다음과 같이 마이그레이션 요청드립니다.

```sql
-- 1. 확장 활성화
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 컬럼 타입 변경 (현재 JSON/Text -> vector)
-- 주의: Amazon Titan Text Embeddings V2는 **1024차원**입니다.
ALTER TABLE sections 
ALTER COLUMN embedding TYPE vector(1024) 
USING (embedding::jsonb)::vector(1024); -- 데이터 변환 필요 시

-- 3. 검색 인덱스 생성
CREATE INDEX idx_sections_embedding 
ON sections 
USING ivfflat (embedding vector_cosine_ops) 
WITH (lists = 100);
```

### 1-3. Spring Entity 확인
Spring의 `Section` 엔티티에서 `embedding` 필드를 처리하는 방식(예: hibernate-vector 라이브러리 사용 또는 네이티브 쿼리)에 대한 검토가 필요합니다. 단순 조회만 한다면 JSON 변환으로 유지해도 무방하지만, **유사도 검색(RAG)** 기능을 위해 DB 레벨의 지원이 필수적입니다.

---

## 2. 🌐 Global Merge 결과의 Neo4j 반영 전략 (Priority: Medium)

Python AI가 **"동일 인물 식별(Global Merge)"**을 수행한 후, 그 결과를 Neo4j 그래프에 어떻게 반영할지 정책 결정이 필요합니다.

### 논의 포인트
- **Option A (Hard Merge)**: Neo4j에서 물리적으로 노드를 병합 (`apoc.refactor.mergeNodes`)
  - 장점: 그래프가 깔끔함
  - 단점: 원본 챕터의 참조가 꼬일 수 있음
- **Option B (Soft Merge)**: `SAME_AS` 엣지로 연결만 수행
  - 장점: 데이터 손실 없음, 구현 간단
  - 단점: 그래프 순회 시 추가 로직 필요

**요청사항**: Spring 팀에서 선호하는 그래프 데이터 모델링 방향을 알려주시면, 그에 맞춰 `GlobalMergeConsumer`의 후처리 로직(Callback Payload)을 확정하겠습니다.

---

## 3. 🧪 Semantic Chunking 테스트 데이터 요청 (Priority: Low)

"의미 기반 장면 전환(Semantic Chunking)" 알고리즘을 고도화하기 위해, **명확한 장면 전환이 포함된** 테스트용 챕터 텍스트가 필요합니다.

- **요청 데이터**: 동일 챕터 내에서 배경(장소)이나 시간이 급격히 바뀌는 구간이 포함된 긴 텍스트 (약 3000자 이상)
- **용도**: 임베딩 유사도 그래프를 그려보고, 자동 Chunking이 사람이 의도한 구간과 일치하는지 튜닝

---
감사합니다.
