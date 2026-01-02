# Questions / Feedback for Request #3

## Regarding 1. pgvector
- `sections` 테이블의 기존 데이터를 마이그레이션할 때, 기존 `embedding` 데이터가 호환되지 않는 형식(예: 단순 텍스트)이라면 재계산(re-embedding)이 필요한가요, 아니면 단순히 NULL 처리하고 이후 배치로 채우나요? (현재 스키마에는 jsonb로 캐스팅하여 변환하는 쿼리가 포함되어 있어 1차적으로는 해결될 것으로 보입니다)
- Spring Data JPA + Hibernate 환경에서 `vector` 타입을 지원하기 위해 `hibernate-vector` 라이브러리를 사용할 예정입니다. 이에 대한 제약사항이 있다면 확인 부탁드립니다.

## Regarding 2. Global Merge Strategy
- (별도 채팅으로 논의 중) -> 개발 편의성과 데이터 정합성을 위해 **Option A (Hard Merge)**를 기반으로 하되, 병합 시 프로퍼티 충돌 정책(예: 더 긴 텍스트 유지, 리스트로 병합 등)을 명확히 할 필요가 있습니다.

## Regarding 3. Semantic Chunking
- 테스트 데이터는 프로젝트 내의 더미 데이터 생성 시나리오를 활용하여 추출하도록 하겠습니다.
