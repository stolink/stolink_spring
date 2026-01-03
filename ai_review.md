## 🤖 AI 코드 리뷰 (Gemini Flash)

### 요약
캐릭터 상세 조회 기능 및 AI 이미지 생성 프롬프트 조립 로직이 추가되었습니다. Neo4j의 JSON 문자열 필드를 DTO 레벨에서 파싱하여 반환하도록 개선되었으며, 이미지 생성 요청 시 설정(Setting) 정보를 포함하여 프롬프트를 정교화했습니다.

### 🔴 치명적 (2건)
**src/main/java/com/stolink/backend/domain/character/repository/CharacterRepository.java:18, 23** - Neo4j 프로퍼티 명칭 불일치
- 문제: `Character` 노드 엔티티(`Character.java`)에는 `projectId` 필드에 `@Property` 어노테이션이 없습니다. SDN(Spring Data Neo4j) 기본 전략상 DB에도 `projectId`로 저장되지만, 커스텀 `@Query`에서는 `project_id` (snake_case)를 사용하고 있어 쿼리 결과가 매칭되지 않습니다.
- 개선: 쿼리의 프로퍼티 명을 엔티티 필드명과 일치시키거나, 엔티티 필드에 `@Property("project_id")`를 명시해야 합니다.
```java
@Query("MATCH (c:Character {projectId: $projectId}) ...") // project_id -> projectId
```

**src/main/java/com/stolink/backend/domain/character/service/CharacterService.java:198, 202** - 하드코딩된 Cypher 쿼리 내 프로퍼티 불일치
- 문제: `session.executeWrite` 내의 원시 Cypher 쿼리에서도 `project_id`를 사용하고 있습니다. 위 Repository 이슈와 동일하게 데이터가 삭제되지 않거나 생성된 데이터의 매핑이 깨질 위험이 큽니다.
- 개선: 프로젝트 내의 프로퍼티 명명 규칙을 하나로 통일(camelCase 권장)하고 모든 쿼리를 수정하세요.

### ⚠️ 경고 (2건)
**src/main/java/com/stolink/backend/domain/character/dto/CharacterResponse.java:21** - DTO 내 ObjectMapper 직접 생성 및 사용
> DTO 내부에서 `new ObjectMapper()`를 static으로 생성하여 파싱 로직을 수행하는 것은 안티패턴입니다. Spring context에 등록된 `ObjectMapper`의 설정(JavaTimeModule 등)을 따르지 않게 되며, 매번 파싱을 수행하므로 성능 저하를 유발합니다. JSON 데이터는 DB 계층에서 `Map`이나 `JsonNode`로 받아오거나, 서비스 레이어에서 변환하여 DTO에 전달하는 것이 좋습니다.

**src/main/java/com/stolink/backend/domain/character/service/CharacterService.java:380** - 서비스 레이어 내 과도한 문자열 파싱 로직
> `generatePrompt` 메서드에서 `appearanceJson`을 직접 파싱하고 루프를 돌며 프롬프트를 생성하고 있습니다. 서비스 레이어의 책임이 과중해지며, JSON 구조 변경 시 서비스 코드까지 수정해야 합니다. 프롬프트 생성 전용 컴포넌트를 분리하거나, 이미 파싱된 객체를 인자로 받도록 개선이 필요합니다.

### 💡 제안
- `CharacterService.getCharacterById` 메서드에 `@Transactional(readOnly = true)`를 추가하여 트랜잭션 읽기 전용 최적화를 적용하세요.
- `CharacterResponse.CharacterRelationshipResponse`의 `mapType` 메서드에서 `switch` 문을 사용할 때, 도메인 내에 `RelationshipType` Enum을 정의하여 관리하는 것이 유지보수에 유리합니다.
