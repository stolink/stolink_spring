# AI Backend Handoff - Answers to Spring Team Questions

**Date**: 2026-01-04  
**From**: AI Backend Team  
**To**: Spring Backend Team  
**Reference**: [AI_HANDOFF_QUESTIONS.md](./AI_HANDOFF_QUESTIONS.md)

---

## 🔴 Critical Questions - Answers

### Q1.1: JPA Entity Synchronization

**Answer**:

1. **Optimistic Locking (`@Version`)**: 
   - **Recommendation**: Remove `@Version` from entities that AI writes to (`CharacterEntity`, `EventEntity`, `SettingEntity`).
   - AI uses `ON CONFLICT DO UPDATE` (UPSERT), which will fail if version check is enforced.
   - If version control is required, delegate ALL writes to Spring (AI sends data via callback only).

2. **JPA Cache Issues**:
   - **Solution**: Call `entityManager.clear()` in the callback handler before reading AI-written data.
   - Alternative: Use `@Transactional(readOnly = true)` + `EntityManager.refresh()` for specific entities.

```java
@Transactional
public void handleAICallback(DocumentAnalysisCallback callback) {
    entityManager.clear(); // Clear L1 cache
    
    // Now read from DB - guaranteed fresh data
    List<CharacterEntity> characters = characterRepository.findByProjectId(callback.getProjectId());
}
```

---

### Q1.2: ID Generation Strategy

**Answer**: **AI generates all IDs.**

| Entity | ID Generator | Format |
|--------|--------------|--------|
| Characters | AI | `UUID.randomUUID()` |
| Events | AI | `UUID.randomUUID()` |
| Settings | AI | `UUID.randomUUID()` |

**Spring Action Required**:
- Change `@GeneratedValue(strategy = GenerationType.UUID)` to just `@Id` (no generation).
- Or, keep `@GeneratedValue` but ensure INSERT uses `merge()` which respects existing IDs.

```java
// Option A: Remove auto-generation
@Id
@Column(updatable = false, nullable = false)
private UUID id;

// Option B: Keep but use merge()
// In repository: characterRepository.save() will work if ID already set
```

**Note**: AI generates IDs at extraction time. IDs are included in the callback payload AND already present in DB when callback arrives.

---

### Q1.3: Callback Timing & Data Completeness

**Answer**:

1. **Guarantee**: 
   - ✅ **YES**, when callback status is `COMPLETED`, all data is in DB.
   - The callback is sent AFTER the last batch is persisted.

2. **Partial Failure**:
   - If AI fails mid-batch, callback status = `FAILED` with error details.
   - Already-persisted batches **remain in DB** (partial data).
   - Spring should query DB to see what was saved before failure.

3. **Callback Never Arrives**:
   - Implement timeout monitoring on Spring side.
   - Query DB with `document_id` to check if any data was written.
   - Add `created_at` / `updated_at` columns to AI-written tables for tracking.

```sql
-- Check if AI wrote any data for a document
SELECT COUNT(*) FROM events WHERE document_id = 'uuid' AND created_at > NOW() - INTERVAL '1 hour';
```

**Recommended Solution**:
```java
@Scheduled(fixedDelay = 300000) // Every 5 minutes
public void checkStaleAnalysis() {
    List<Document> stale = documentRepository.findByStatusAndUpdatedAtBefore(
        AnalysisStatus.PROCESSING, 
        LocalDateTime.now().minusMinutes(30)
    );
    
    for (Document doc : stale) {
        // Check if AI wrote any data
        long eventCount = eventRepository.countByDocumentId(doc.getId());
        if (eventCount > 0) {
            doc.setStatus(AnalysisStatus.PARTIAL_COMPLETE);
        } else {
            doc.setStatus(AnalysisStatus.FAILED);
        }
        documentRepository.save(doc);
    }
}
```

---

### Q2.1: JPA Entity Mapping - Required Schema

**Confirmed Fields to Add**:

```java
// EventEntity.java
@Column(name = "chapter")
private Integer chapter = 0;

@Column(name = "sequence_order")
private Integer sequenceOrder = 0;

@Column(name = "participants", columnDefinition = "TEXT")
private String participants; // JSON array as string

@Column(name = "location_ref")
private String locationRef;

// CharacterEntity.java
@Column(name = "aliases_json", columnDefinition = "TEXT")
private String aliasesJson; // JSON array as string
```

**Migration Script** (Flyway):
```sql
-- V2026_01_04__add_ai_fields.sql

ALTER TABLE events ADD COLUMN IF NOT EXISTS chapter INT DEFAULT 0;
ALTER TABLE events ADD COLUMN IF NOT EXISTS sequence_order INT DEFAULT 0;
ALTER TABLE events ADD COLUMN IF NOT EXISTS participants TEXT;
ALTER TABLE events ADD COLUMN IF NOT EXISTS location_ref VARCHAR(255);

ALTER TABLE characters ADD COLUMN IF NOT EXISTS aliases_json TEXT;
```

---

## 🟠 Medium Priority - Answers

### Q3.1: DTO Compatibility - Validation Field

**Answer**: Add `validation` field to DTO.

```java
public class DocumentAnalysisCallbackDTO {
    // Existing fields...
    
    @JsonProperty("plot_integration")
    private PlotIntegration plotIntegration;
    
    @JsonProperty("consistency_report")
    private ConsistencyReport consistencyReport;
    
    // NEW FIELD
    @JsonProperty("validation")
    private ValidationResult validation;
}

// New class
public class ValidationResult {
    private Integer score;
    private String action; // "approve", "retry", "manual_review"
    private List<String> issues;
}
```

---

### Q4.1: Neo4j Relationship Creation Responsibility

**Answer**: **Hybrid Approach (Option C)**

| Relationship Type | Creator | Reason |
|-------------------|---------|--------|
| `(:Character)-[:APPEARS_IN]->(:Event)` | AI | Extracted from `event.participants` |
| `(:Character)-[:KNOWS]->(:Character)` | AI | Extracted from relationship analysis |
| `(:Character)-[:ENEMY_OF]->(:Character)` | AI | Same as above |
| `(:Event)-[:HAPPENS_AT]->(:Setting)` | AI | Extracted from `event.location_ref` |
| **Business Logic Relationships** | Spring | User-defined, manual edits |

**AI Creates These Relationships**:
```cypher
// Character appears in Event
MATCH (c:Character {id: $char_id}), (e:Event {id: $event_id})
MERGE (c)-[:APPEARS_IN]->(e)

// Character relationships  
MATCH (c1:Character {id: $source_id}), (c2:Character {id: $target_id})
MERGE (c1)-[:KNOWS {relation_type: $type}]->(c2)
```

**Spring Owns**:
- User-initiated relationship edits
- Cross-project relationships
- Admin overrides

---

### Q4.2: Neo4j UUID Compatibility

**Answer**: ✅ **Compatible with configuration**

AI writes UUID as String:
```cypher
CREATE (:Character {id: "550e8400-e29b-41d4-a716-446655440000"})
```

Spring reads as UUID:
```java
@Node("Character")
public class Character {
    @Id
    private String id; // Use String, not UUID
    
    // OR convert manually
    public UUID getIdAsUuid() {
        return UUID.fromString(this.id);
    }
}
```

**Recommendation**: Use `String` type for Neo4j `id` field, convert to `UUID` in service layer if needed.

---

## 🟢 Low Priority - Confirmed

### Q5: Redis Dependency
**Confirmed**: No Spring changes needed. DevOps adds Redis to k8s/docker-compose.

### Q6: RAG Event Extraction
**Confirmed**: Internal AI improvement. Spring benefits from better `prev_event_id` linking.

---

## 📋 Updated Action Items

| Priority | Task | Owner | Answer/Status |
|----------|------|-------|---------------|
| 🔴 | Remove `@Version` from AI-written entities OR delegate writes to Spring | Spring Team | **Choose one** |
| 🔴 | Adjust `@Id` strategy (no auto-generation for AI entities) | Spring Team | **Use `@Id` only** |
| 🔴 | Add `entityManager.clear()` in callback handler | Spring Team | **Required** |
| 🔴 | Add migration for new columns | Spring Team | **Script provided above** |
| 🔴 | Add `validation` to DTO | Spring Team | **Code provided above** |
| 🟠 | Use `String` for Neo4j `id` field | Spring Team | **Recommended** |
| 🟢 | Deploy Redis | DevOps | **docker-compose updated** |

---

## 📞 Contact

For additional questions, contact the AI Backend team or schedule a sync meeting.
