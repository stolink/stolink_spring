# AI Backend Answers - Follow-up Questions

**Date**: 2026-01-04  
**From**: AI Backend Team  
**To**: Spring Backend Team  
**Reference**: [AI_HANDOFF_FOLLOWUP.md](../SPRING_QUESTIONS/AI_HANDOFF_FOLLOWUP.md)

---

## 🟠 High Priority Answers

### Q1: Concurrency & Conflict Resolution

**Recommended Strategy**: **Option A - Lock editing during analysis**

**Rationale**:
- AI analysis is a batch process (few minutes to hours)
- User edits during analysis will be overwritten anyway
- Simplest to implement and easiest to explain to users

**Implementation**:

```java
// DocumentEntity.java
@Column(name = "analysis_locked")
private Boolean analysisLocked = false;

// UI: Check before allowing edit
if (document.isAnalysisLocked()) {
    throw new AnalysisInProgressException("Cannot edit while analysis is running");
}

// AI Callback: Unlock after completion
@Transactional
public void handleCallback(DocumentAnalysisCallback callback) {
    Document doc = documentRepository.findById(callback.getDocumentId());
    doc.setAnalysisLocked(false);
    documentRepository.save(doc);
}
```

**Who wins if lock fails?** 
- AI's UPSERT uses `updated_at = NOW()`, so **AI wins on timestamp**.
- If user edit is critical, Spring should re-apply user changes after callback using audit log.

**Alternative (Last-Write-Wins)**:
If locking is too restrictive, implement a simple audit mechanism:
```java
// After AI callback, restore user edits from audit log if any
List<CharacterEdit> userEdits = auditLogRepository.findByDocumentIdAndCreatedAfter(
    docId, analysisStartTime);
for (CharacterEdit edit : userEdits) {
    characterRepository.save(edit.reapply());
}
```

---

### Q2: Partial Data Cleanup Strategy

**Answer**:

1. **Should we delete partial data on `FAILED`?**
   - **No, do not auto-delete.**
   - Partial data is useful for debugging and can be manually reviewed.
   - Add a `status` column to track completeness.

2. **Retry behavior**:
   - **Option B: UPSERT over existing data**
   - AI does NOT delete existing data before retry.
   - New extraction results are merged with existing (same ID = update, new ID = insert).

**Recommended Implementation**:

```java
// Mark incomplete analysis
@Transactional
public void handleFailedCallback(DocumentAnalysisCallback callback) {
    Document doc = documentRepository.findById(callback.getDocumentId());
    doc.setAnalysisStatus(AnalysisStatus.PARTIAL_FAILURE);
    doc.setErrorMessage(callback.getError().getMessage());
    documentRepository.save(doc);
    
    // Do NOT delete characters/events - they may be useful
}

// On retry, AI will UPSERT:
// - Existing characters get updated (merged)
// - New characters get inserted
// - No duplicates because AI uses same IDs for same entities
```

**Cleanup (Optional)**:
If user explicitly requests "fresh start":
```java
public void resetAnalysis(UUID documentId) {
    characterRepository.deleteByDocumentId(documentId);
    eventRepository.deleteByDocumentId(documentId);
    settingRepository.deleteByDocumentId(documentId);
    // Now ready for fresh analysis
}
```

---

### Q3: PostgreSQL vs Neo4j - Source of Truth

**Answer**:

| Data Type | Source of Truth | Reason |
|-----------|-----------------|--------|
| **Entities** (Characters, Events, Settings) | **PostgreSQL** | Relational data, ACID transactions, JPA integration |
| **Graph Relationships** (KNOWS, ENEMY_OF) | **Neo4j** | Graph traversal, path queries, relationship properties |

**Dual-Storage Rationale**:
- **PostgreSQL**: Primary storage for structured data, full-text search, reporting
- **Neo4j**: Visual graph queries, character network analysis, relationship strength

**Sync Mechanism**:
```
AI writes → PostgreSQL (primary)
         → Neo4j (secondary, for graph visualization)
                  
Spring reads ← PostgreSQL (for CRUD operations)
            ← Neo4j (for graph queries only)
```

**No PostgreSQL junction table needed** for relationships:
- `character_relationships` table is **NOT required**
- Relationships are stored **only in Neo4j**
- If you need relationship data in PostgreSQL, query Neo4j and cache results

**Example**:
```java
// Get character details → PostgreSQL
CharacterEntity character = characterRepository.findById(characterId);

// Get character relationships → Neo4j
List<Relationship> relationships = neo4jRepository.findRelationshipsByCharacterId(characterId);

// Return combined DTO
return new CharacterDetailDTO(character, relationships);
```

---

## 🟢 Medium Priority Answers

### Q4: Missing `document_id` in Events Schema

**Answer**: **Yes, add `document_id`**

Updated migration:
```sql
ALTER TABLE events ADD COLUMN IF NOT EXISTS document_id UUID REFERENCES documents(id);
ALTER TABLE events ADD COLUMN IF NOT EXISTS chapter INT DEFAULT 0;
ALTER TABLE events ADD COLUMN IF NOT EXISTS sequence_order INT DEFAULT 0;
ALTER TABLE events ADD COLUMN IF NOT EXISTS participants TEXT;
ALTER TABLE events ADD COLUMN IF NOT EXISTS location_ref VARCHAR(255);

-- Add index for document queries
CREATE INDEX IF NOT EXISTS idx_events_document_id ON events(document_id);
```

**AI Behavior**: AI already sends `document_id` in the event data. This field will be populated automatically.

---

### Q5: Callback Retry Logic

**Answer**: **Option A - Retry with exponential backoff**

**Current AI Implementation**:
```python
# httpx retry with tenacity
@retry(
    stop=stop_after_attempt(5),  # Max 5 attempts
    wait=wait_exponential(multiplier=1, min=2, max=60)  # 2s, 4s, 8s, 16s, 32s
)
async def _send_callback(self, url: str, callback: dict):
    response = await self._http_client.post(url, json=callback)
    response.raise_for_status()
```

**After all retries fail**:
- Callback is logged in AI server logs
- Message is NOT sent to DLQ (no DLQ configured)
- Data remains in DB (already persisted)

**Recommended Spring Implementation**: Yes, implement idempotent handling:
```java
@Transactional
public void handleCallback(DocumentAnalysisCallback callback) {
    String jobId = callback.getJobId();
    
    if (callbackLogRepository.existsByJobId(jobId)) {
        log.warn("Duplicate callback ignored: {}", jobId);
        return;
    }
    
    // Process callback...
    
    callbackLogRepository.save(new CallbackLog(jobId, LocalDateTime.now()));
}
```

---

## 🟢 Low Priority Answers

### Q6: `participants` JSON Format

**Answer**: **Option A - Name array (not UUID)**

**Example**:
```json
["장발장", "자베르", "코제트"]
```

**Reason**: 
- Event extraction happens before character ID assignment
- LLM extracts character **names** from text, not UUIDs
- Spring should match names to character IDs if needed

**Parsing Example**:
```java
@Column(name = "participants", columnDefinition = "TEXT")
private String participants;

public List<String> getParticipantsList() {
    if (participants == null || participants.isBlank()) {
        return Collections.emptyList();
    }
    return objectMapper.readValue(participants, new TypeReference<List<String>>() {});
}
```

---

### Q7: `validation.action` Enum Values

**Answer**:

| Action | Meaning | Recommended Spring Behavior |
|--------|---------|----------------------------|
| `approve` | AI validated data as high quality | Mark analysis as `COMPLETED`, data is ready for user |
| `retry` | Data quality below threshold | Auto-trigger re-analysis (increment retry counter) |
| `manual_review` | Ambiguous result, needs human check | Set status = `PENDING_REVIEW`, notify content moderator |

**Implementation Example**:
```java
@Transactional
public void handleCallback(DocumentAnalysisCallback callback) {
    ValidationResult validation = callback.getValidation();
    Document doc = documentRepository.findById(callback.getDocumentId());
    
    switch (validation.getAction()) {
        case "approve":
            doc.setStatus(AnalysisStatus.COMPLETED);
            break;
            
        case "retry":
            if (doc.getRetryCount() < 3) {
                doc.setRetryCount(doc.getRetryCount() + 1);
                rabbitTemplate.convertAndSend("document_analysis_queue", createRetryMessage(doc));
                doc.setStatus(AnalysisStatus.RETRYING);
            } else {
                doc.setStatus(AnalysisStatus.FAILED);
                doc.setErrorMessage("Max retries exceeded");
            }
            break;
            
        case "manual_review":
            doc.setStatus(AnalysisStatus.PENDING_REVIEW);
            notificationService.notifyModerators(doc.getId());
            break;
    }
    
    documentRepository.save(doc);
}
```

---

## 📋 Summary

| Question | Answer |
|----------|--------|
| Q1: Concurrency | **Lock UI editing during analysis** (Option A) |
| Q2: Partial cleanup | **Keep partial data**, retry uses UPSERT (Option B) |
| Q3: Source of truth | **PostgreSQL for entities, Neo4j for relationships only** |
| Q4: document_id | **Yes, add it** (migration script provided) |
| Q5: Callback retry | **AI retries 5 times**, Spring should be idempotent |
| Q6: participants format | **JSON array of names**: `["장발장", "자베르"]` |
| Q7: validation actions | **approve=complete, retry=reanalyze, manual_review=notify admin** |

---

**All blocking questions (Q1-Q3) have been answered. You may proceed with implementation.**
