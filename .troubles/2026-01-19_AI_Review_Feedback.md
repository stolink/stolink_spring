# AI 코드 리뷰 피드백 수정 (2026-01-19)

## 1. N+1 Query & List Indeterminacy in Character Cloning

### 이슈 설명 (Round 1 & 2)

- **Round 1**: `CharacterService`에서 루프 내 `save()` 호출로 인한 N+1 성능 저하.
- **Round 2**: `saveAll()` 사용 시 반환 리스트의 순서가 보장되지 않아 `oldToNewCharIdMap` 매핑이 꼬일 위험(List Order Dependency) 지적.

### 해결책

- **Batch Insert**: `saveAll()` 사용하여 DB 접근 횟수 최소화.
- **Robust Mapping**: `characterId` (비즈니스 키)를 기준으로 반환된 엔티티를 Map핑하여, 순서 의존성 제거.

```java
// Logic
Map<String, Character> savedCharMap = savedCharacters.stream()
    .filter(c -> c.getCharacterId() != null)
    .collect(Collectors.toMap(Character::getCharacterId, Function.identity()));
// Mapping safely by key
```

## 2. Security Check (BOLA) - Strict Enforcement

### 이슈 설명 (Round 1 & 2)

- **Round 1**: 프로젝트 소유권 검증 로직 누락.
- **Round 2**: "Relaxed Check"로 Postgres 데이터 유실 시 접근을 허용하면 보안 취약점(BOLA) 발생 가능성 존재.

### 해결책

- **Strict Check**: 예외 발생 시 "주의 허용" 로직 제거. 검증 실패 시 무조건 `ResourceNotFoundException` 발생.

## 3. Transaction Atomicity (Dual Write Problem)

### 이슈 설명 (Round 2)

- `ProjectService.cloneProject`는 `@Transactional` (Postgres) 하에서 실행되지만, Neo4j 작업은 별도 트랜잭션임.
- Neo4j 작업 성공 후 Postgres 커밋 실패 시, Neo4j 데이터만 남아 일관성 깨짐.

### 해결책

- **Compensation Logic**: Postgres 트랜잭션 롤백(Catch Block) 시 `characterService.deleteCharactersByProjectId`를 호출하여 생성된 Neo4j 데이터도 명시적으로 삭제.

## 4. Neo4j Index & Query Performance (Round 3)

### 이슈 설명

- `EventNeo4jRepository`에서 `toLower(p) = toLower(name)` 사용 시 인덱스를 타지 못하고 Full Scan 발생 (Critical).
- `CharacterService` 복제 로직에서 관계 타입별로 별도 트랜잭션(`session.executeWrite`)을 실행하여 네트워크 오버헤드 발생 (Warning).

### 해결책

- **Schema Optimization**: `Event` 노드에 정규화된 `participants_normalized` 속성 추가 및 저장 시 자동 처리.
- **Manual Migration**: 기존 데이터를 위해 Cypher 쿼리 실행.
  `MATCH (e:Event) SET e.participants_normalized = [p IN e.participants | toLower(p)]`
- **Query Optimization**: `CharacterService` 루프 내 쿼리들을 단일 트랜잭션으로 묶어 배치 처리.
- **Clean Code**: 사용하지 않는 `findAll()` 제거.
