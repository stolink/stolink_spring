# AI Backend Handoff - Spring Team Questions & Action Items

**Date**: 2026-01-04  
**Reviewer**: Spring Backend Team  
**Reference**: [SPRING_TEAM_HANDOFF.md](./SPRING_TEAM_HANDOFF.md)

This document outlines questions and action items for the Spring Backend team following the AI Backend architecture update.

---

## 🔴 Critical Questions

### 1. Direct DB Write - Transaction & Data Integrity

> **Context**: AI server now writes directly to PostgreSQL and Neo4j during analysis.

#### Q1.1: JPA Entity Synchronization
- **Issue**: If AI directly executes SQL UPSERT, how do we prevent conflicts with Spring's JPA entities?
  - Does Spring's `CharacterEntity` use `@Version` (Optimistic Locking)? If yes, AI's direct SQL writes may cause version conflicts.
  - Could JPA cache (1st/2nd level) cause stale data reads when Spring queries AI-written data?

**Proposed Solution**:
- Clear EntityManager cache after callback reception?
- Disable versioning on entities that AI writes to?

#### Q1.2: ID Generation Strategy
- **Question**: Who generates the `id` for `characters`, `events`, `settings`?
  - If AI generates UUIDs, we need to ensure Spring's `@GeneratedValue` doesn't interfere
  - If Spring generates, we need to send `id` in the RabbitMQ message

**Current Spring Setup**:
```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;
```

**Action Required**: Clarify ID generation responsibility.

#### Q1.3: Callback Timing & Data Completeness
> "Data appears in DB **before** callback arrives"

- **Question**: Can we **guarantee** all data is in DB when callback arrives?
- What `status` does callback send if AI fails mid-batch? (e.g., `PARTIAL_FAILURE`?)
- How to handle "callback never arrives" scenario? (DB has data, but Spring doesn't know analysis completed)

**Proposed Solution**:
- Implement health check: query DB periodically if callback timeout exceeds threshold?
- Add `last_updated_at` column to track AI write timestamps?

---

### 2. Database Schema Changes

#### Required Migrations

```sql
-- characters table
ALTER TABLE characters ADD COLUMN IF NOT EXISTS aliases_json TEXT;

-- events table  
ALTER TABLE events ADD COLUMN IF NOT EXISTS chapter INT DEFAULT 0;
ALTER TABLE events ADD COLUMN IF NOT EXISTS sequence_order INT DEFAULT 0;
ALTER TABLE events ADD COLUMN IF NOT EXISTS participants TEXT; -- JSON array
ALTER TABLE events ADD COLUMN IF NOT EXISTS location_ref VARCHAR(255);
```

#### Q2.1: JPA Entity Mapping
**Action Required**:
1. Check if `EventEntity.java` has corresponding fields
2. Add missing fields with proper JPA annotations:

```java
@Column(name = "chapter")
private Integer chapter;

@Column(name = "sequence_order")
private Integer sequenceOrder;

@Type(JsonType.class) // or @Convert
@Column(name = "participants", columnDefinition = "TEXT")
private List<String> participants;

@Column(name = "location_ref")
private String locationRef;
```

3. Create Flyway/Liquibase migration script

**Status**: `[ ]` Pending verification of current `EventEntity` schema

---

## 🟠 Medium Priority Questions

### 3. Callback Payload Structure Updates

New fields in `DocumentAnalysisCallback`:
- `plot_integration` (Object)
- `consistency_report` (Object)
- `validation` (Object) ← **NEW**

#### Q3.1: DTO Compatibility
**Action Required**:
1. Verify `DocumentAnalysisCallbackDTO.java` has:
   - ✅ `plot_integration` (already added)
   - ✅ `consistency_report` (already added)
   - ❓ `validation` (need to add?)

2. Check `AICallbackService.java` handles all three fields

**Status**: `[ ]` Pending code review

---

### 4. Neo4j Direct Writes

AI creates `:Character` and `:Event` nodes directly.

#### Q4.1: Relationship Creation Responsibility
**Question**: Who creates relationships?
- `(:Character)-[:APPEARS_IN]->(:Event)`
- `(:Character)-[:KNOWS]->(:Character)`
- `(:Character)-[:ENEMY_OF]->(:Character)`

**Options**:
- A) AI creates all nodes + relationships → Spring only reads
- B) AI creates nodes, Spring creates relationships from callback data
- C) Hybrid: AI creates some, Spring creates others

**Action Required**: Clarify responsibility matrix with AI team

#### Q4.2: UUID Compatibility
**Question**: AI writes `id` as String UUID. Does Neo4j Bolt driver auto-convert to Spring's `UUID` type?

**Test Case**:
```cypher
// AI writes
CREATE (:Character {id: "550e8400-e29b-41d4-a716-446655440000"})

// Spring reads
@Node("Character")
public class Character {
    @Id
    private UUID id; // Does this work?
}
```

**Status**: `[ ]` Need integration test

---

## 🟢 Low Priority / Informational

### 5. Redis Dependency

**Impact on Spring**: None (internal to AI server)

**Action Required**: DevOps to add Redis container to deployment environment

```yaml
redis:
  image: redis:7-alpine
  ports:
    - "6379:6379"
```

---

### 6. Enhanced Event Extraction (RAG)

AI now uses vector search to retrieve past events when analyzing later chapters.

**Impact on Spring**: None (internal AI improvement)

**Expected Benefit**: Better event causality linking (`prev_event_id`)

---

## 📋 Action Items Summary

| Priority | Task | Owner | Status |
|----------|------|-------|--------|
| 🔴 **Critical** | Verify PostgreSQL schema has required columns | Spring Team | `[ ]` |
| 🔴 **Critical** | Add JPA fields to `EventEntity.java` | Spring Team | `[ ]` |
| 🔴 **Critical** | Create DB migration script (Flyway) | Spring Team | `[ ]` |
| 🔴 **Critical** | Clarify ID generation strategy with AI team | Both Teams | `[ ]` |
| 🔴 **Critical** | Test JPA cache + AI direct write scenario | Spring Team | `[ ]` |
| 🟠 Medium | Add `validation` field to `DocumentAnalysisCallbackDTO` | Spring Team | `[ ]` |
| 🟠 Medium | Define Neo4j relationship creation responsibility | Both Teams | `[ ]` |
| 🟢 Low | Request Redis deployment | DevOps | `[ ]` |
| 🟢 Low | Test Neo4j UUID String ↔ Java UUID conversion | Spring Team | `[ ]` |

---

## 🤝 Next Steps

1. **Code Review Session** (Sprint Team)
   - Review `EventEntity.java`, `CharacterEntity.java`
   - Review `DocumentAnalysisCallbackDTO.java`
   - Review `AICallbackService.java`

2. **Q&A with AI Team** (Both Teams)
   - Schedule sync meeting to clarify critical questions
   - Focus on ID generation, transaction handling, relationship responsibility

3. **Integration Testing** (QA + Both Teams)
   - Test "AI writes DB → Spring reads" flow
   - Test "Callback arrives late" scenario
   - Test "Callback never arrives" scenario

---

## 📝 Notes

- Original handoff doc: [SPRING_TEAM_HANDOFF.md](./SPRING_TEAM_HANDOFF.md)
- This document should be updated as questions are answered
- Mark items as `[x]` when completed
