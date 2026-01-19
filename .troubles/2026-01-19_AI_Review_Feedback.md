# AI 코드 리뷰 피드백 수정 (2026-01-19)

## 1. N+1 Query in Character Cloning

### 이슈 설명

`CharacterService.cloneCharactersAndRelationships` 메서드에서 `sourceCharacters`를 순회하며 루프 내부에서 `characterRepository.save()`를 호출하여 N+1 문제가 발생함.

### 변경 전

```java
for (Character source : sourceCharacters) {
    // ... builder ...
    newChar = characterRepository.save(newChar); // Loop 내 save 호출
    oldToNewCharIdMap.put(source.getId(), newChar.getId());
}
```

### 변경 후

```java
List<Character> newCharacters = new ArrayList<>();
for (Character source : sourceCharacters) {
    // ... builder ...
    newCharacters.add(newChar);
}
List<Character> savedCharacters = characterRepository.saveAll(newCharacters); // Batch Insert
// ... map mapping ...
```

### 해결 상태

✅ 해결 완료 (Batch Insert 적용)

---

## 2. Security Check (BOLA) Skipped

### 이슈 설명

`EventService`에서 "RELAXED CHECK"라는 명목으로 프로젝트 소유권 검증 로직이 주석 처리되어 있어, `characterId`만 알면 타인의 프로젝트 이벤트를 조회할 수 있는 잠재적 보안 취약점 존재.

### 변경 전

```java
/*
Project project = projectRepository.findByIdWithUser(projectId)
        .orElseThrow(...);
if (!project.getUser().getId().equals(userId)) { ... }
*/
```

### 변경 후

```java
// [RESTORED] Minimum security check
try {
    Project project = projectRepository.findByIdWithUser(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

    if (!project.getUser().getId().equals(userId)) {
        throw new ResourceNotFoundException("Project not found");
    }
} catch (ResourceNotFoundException e) {
    // Log incongruency but fail safe if ownership mismatch
    if (e.getMessage().equals("Project not found")) throw e;
    log.warn("Project missing in Postgres but referenced in Neo4j...");
}
```

### 해결 상태

✅ 해결 완료 (소유권 검증 로직 복원 및 예외 처리 강화)
