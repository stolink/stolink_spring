# Spring Team Follow-up Questions

**Date**: 2026-01-04  
**From**: Spring Backend Team  
**To**: AI Backend Team  
**Reference**: [AI_HANDOFF_ANSWERS.md](../AI_ANSWERS/AI_HANDOFF_ANSWERS.md)

Thank you for the comprehensive answers to our initial questions. Based on your responses, we have identified a few additional clarifications needed before implementation.

---

## 🟠 High Priority Follow-ups

### Q1: Concurrency & Conflict Resolution

**Scenario**: AI is writing to DB during analysis while user edits the same character via UI.

**Example Timeline**:
```
T1: User opens character "Jean Valjean" in UI
T2: AI analysis starts processing Chapter 5
T3: User updates "Jean Valjean" description → Spring writes to DB
T4: AI finishes Chapter 5 analysis → writes to same character row
```

**Questions**:
1. **Who wins?** Does AI's UPSERT overwrite user edits?
2. **Should we lock editing during analysis?** 
   - Option A: Disable UI editing when `document.status = PROCESSING`
   - Option B: Implement last-write-wins with audit log
   - Option C: AI only writes if `updated_at` timestamp matches expected value

**Proposed Solution**:
```java
// Option: Add "edit lock" field
@Column(name = "ai_locked")
private Boolean aiLocked = false; // Set to true during AI analysis
```

**Request**: Please advise recommended conflict strategy.

---

### Q2: Partial Data Cleanup Strategy

From your answer Q1.3:
> "Already-persisted batches **remain in DB** (partial data)."

**Questions**:
1. **Should we delete partial data on `FAILED` status?**
   - Concern: Partial data may confuse users (e.g., only 3 of 10 characters extracted)
   
2. **Retry behavior**: If user retries analysis, does AI:
   - A) Delete all existing data first, then start fresh?
   - B) UPSERT over existing data (append new, update existing)?
   - C) Ignore existing data completely?

**Current Spring Assumption**: 
We assume retry = fresh start (delete + re-analyze). Please confirm.

---

### Q3: PostgreSQL vs Neo4j - Source of Truth

Both PostgreSQL and Neo4j store character/event data:

| Data | PostgreSQL | Neo4j |
|------|------------|-------|
| Characters | `characters` table | `:Character` nodes |
| Events | `events` table | `:Event` nodes |
| Relationships | JOIN tables? | `:KNOWS`, `:ENEMY_OF` edges |

**Questions**:
1. **Primary database**: Which is the source of truth?
   - Spring reads from PostgreSQL or Neo4j?
   - If different, how is sync guaranteed?

2. **Relationship storage**:
   - Are character relationships (`KNOWS`, `ENEMY_OF`) **only** in Neo4j?
   - Or also in a PostgreSQL junction table (`character_relationships`)?

**Current Spring Schema**:
```sql
-- Do we need this table?
CREATE TABLE character_relationships (
    id UUID PRIMARY KEY,
    source_character_id UUID REFERENCES characters(id),
    target_character_id UUID REFERENCES characters(id),
    relation_type VARCHAR(50), -- "KNOWS", "ENEMY_OF", etc.
    created_at TIMESTAMP DEFAULT NOW()
);
```

**Request**: Clarify dual-storage rationale and sync mechanism.

---

## 🟢 Medium Priority Follow-ups

### Q4: Missing `document_id` in Events Schema

Your provided migration:
```sql
ALTER TABLE events ADD COLUMN IF NOT EXISTS chapter INT DEFAULT 0;
ALTER TABLE events ADD COLUMN IF NOT EXISTS sequence_order INT DEFAULT 0;
ALTER TABLE events ADD COLUMN IF NOT EXISTS participants TEXT;
ALTER TABLE events ADD COLUMN IF NOT EXISTS location_ref VARCHAR(255);
```

**Missing Field**: `document_id`

**Question**: 
- Events should be traceable to their source document, right?
- Should we add:
  ```sql
  ALTER TABLE events ADD COLUMN IF NOT EXISTS document_id UUID REFERENCES documents(id);
  ```

**Use Case**: "Show all events extracted from Document XYZ"

---

### Q5: Callback Retry Logic

**Question**: If callback fails (e.g., Spring server is down), does AI:
- A) Retry N times with exponential backoff?
- B) Send to Dead Letter Queue (DLQ)?
- C) Give up and mark job as failed internally?

**Current Spring Assumption**: 
We assume AI retries. Should we implement idempotent callback handling?

```java
@Transactional
public void handleCallback(DocumentAnalysisCallback callback) {
    // Check if already processed
    if (callbackLogRepository.existsByJobId(callback.getJobId())) {
        log.warn("Duplicate callback ignored: {}", callback.getJobId());
        return;
    }
    
    // Process...
    callbackLogRepository.save(new CallbackLog(callback.getJobId()));
}
```

---

## 🟢 Low Priority Follow-ups

### Q6: `participants` JSON Format

You specified:
```java
@Column(name = "participants", columnDefinition = "TEXT")
private String participants; // JSON array as string
```

**Question**: What is the exact JSON structure?

**Option A** (ID array):
```json
["uuid-1", "uuid-2", "uuid-3"]
```

**Option B** (Object array):
```json
[
  {"character_id": "uuid-1", "role": "protagonist"},
  {"character_id": "uuid-2", "role": "antagonist"}
]
```

**Request**: Provide example JSON for parsing.

---

### Q7: `validation.action` Enum Values

Your DTO:
```java
private String action; // "approve", "retry", "manual_review"
```

**Question**: What should Spring do for each action?

| Action | Spring Behavior |
|--------|-----------------|
| `approve` | Mark analysis as complete? |
| `retry` | Auto-trigger re-analysis? |
| `manual_review` | Notify admin + block publishing? |

**Request**: Clarify expected Spring workflow for each action.

---

## 📋 Summary

| Priority | Question | Blocking Implementation? |
|----------|----------|-------------------------|
| 🟠 High | Q1: Concurrency strategy | ⚠️ Yes (affects UI) |
| 🟠 High | Q2: Partial data cleanup | ⚠️ Yes (affects retry logic) |
| 🟠 High | Q3: PostgreSQL vs Neo4j source of truth | ⚠️ Yes (affects service layer) |
| 🟢 Medium | Q4: `document_id` in events | No (can add later) |
| 🟢 Medium | Q5: Callback retry | No (defensive coding) |
| 🟢 Low | Q6: `participants` JSON format | No (can infer from sample) |
| 🟢 Low | Q7: `validation.action` workflow | No (can default to "manual") |

---

## 📞 Next Steps

Please prioritize answers to **Q1-Q3** as they are blocking our implementation decisions.

We are ready to proceed with:
- ✅ Migration script execution (pending Q3, Q4 answers)
- ✅ Entity updates (pending Q6 answer)
- ✅ Callback handler updates (pending Q5, Q7 answers)

Thank you for your continued collaboration!
