# Character Entity Relationship Type Fix

## Issue Description

Neo4j 관계 매핑에서 `@Relationship` 어노테이션에 `type` 속성이 누락되어, 그래프 데이터 정합성 문제가 발생할 가능성이 있었음. (AI 리뷰 지적 사항: 🔴 치명적)

- 파일: `src/main/java/com/stolink/backend/domain/character/node/Character.java`
- 라인: 34
- 에러 유형: 🔴 치명적

## Solution Strategy

`@Relationship` 어노테이션에 명시적인 관계 타입(`RELATED_TO`)과 방향(`OUTGOING`)을 지정하여 Neo4j 스키마를 명확히 함.

### 변경 전

```java
    @Relationship
    @Builder.Default
    private List<CharacterRelationship> relationships = new ArrayList<>();
```

### 변경 후

```java
    @Relationship(type = "RELATED_TO", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    private List<CharacterRelationship> relationships = new ArrayList<>();
```

## Outcome

- **상태**: ✅ 해결됨
- **빌드 결과**: `BUILD SUCCESSFUL`
- **검증 방법**: `./gradlew build` 컴파일 확인 완료.
