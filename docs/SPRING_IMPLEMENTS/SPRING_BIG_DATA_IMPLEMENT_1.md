# Spring Big Data & Optimization Implementation #1

> **Date**: 2026-01-02
> **Context**: AI Team Request #3 - pgvector & Global Merge

---

## 1. 🐘 pgvector 도입 (Vector Similarity Search)

### 1-1. Infrastructure Update
- **Docker Image**: `postgres:16.11-alpine` -> `pgvector/pgvector:pg16`
- **Dependencies**: Added `org.hibernate.orm:hibernate-vector:6.4.1.Final`
- **Configuration**:
  - `docker-compose.local.yml`: Updated postgres service image.
  - `build.gradle`: Added hibernate-vector dependency.

### 1-2. Schema Migration (`sections`)
- **Column Change**: `embedding` (TEXT/JSON) -> `embedding` (vector(1024))
- **Migration Script**: `src/main/resources/sql/pgvector_migration.sql`
  ```sql
  CREATE EXTENSION IF NOT EXISTS vector;
  ALTER TABLE sections ALTER COLUMN embedding TYPE vector(1024) USING (embedding::jsonb)::vector(1024);
  CREATE INDEX idx_sections_embedding ON sections USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
  ```

### 1-3. Application Logic
- **Entity**: `Section.java` uses `@JdbcTypeCode(SqlTypes.VECTOR)` mapping for `float[] embedding`.
- **Service**: `AICallbackService` converts `List<Double>` (from JSON/DTO) to `float[]` before saving.

---

## 2. 🌐 Global Merge Strategy Implementation

### 2-1. Policy: Option A (Hard Merge)
- **Objective**: Physically merge duplicate nodes in Neo4j to maintain a single source of truth.
- **Tools**: Using `apoc.refactor.mergeNodes` procedure.

### 2-2. Implementation Details
- **Repository**: `CharacterRepository.mergeNodes(primaryId, mergedId)`
  - Query:
    ```cypher
    MATCH (p:Character {id: $primaryId})
    MATCH (m:Character {id: $mergedId})
    CALL apoc.refactor.mergeNodes([p, m], {properties: 'discard', mergeRels: true}) YIELD node
    RETURN node
    ```
  - **Properties**: `properties: 'discard'` keeps the primary node's properties (prioritizing the 'intelligent' choice made by the AI agent during the analysis phase).
  - **Relationships**: `mergeRels: true` moves relationships from the merged node to the primary node.

- **Service Flow (`AICallbackService`)**:
  1. Receive `GlobalMergeCallbackDTO` containing `mergedIds` list for a `primaryId`.
  2. Update simple list fields (Aliases) via Java logic (Union).
  3. Call `mergeNodes` for each duplicate character ID to perform the graph merge.

---

## 3. 🧪 Future Work (Pending)
- **Semantic Chunking Test Data**: Need to extract scenario-based text data for AI team tuning.
