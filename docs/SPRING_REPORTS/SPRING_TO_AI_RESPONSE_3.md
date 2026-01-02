# Response to Request #3 (To. AI Team)

> **Date**: 2026-01-02
> **From**: Spring Team
> **Subject**: Completion of pgvector Setup & Global Merge Strategy

---

## 1. ✅ pgvector Setup Complete

We have completed the infrastructure and schema updates to support `pgvector`.

- **Environment**: The production and dev PostgreSQL instances now use the `pgvector` extension.
- **Schema**: The `sections` table's `embedding` column has been migrated to `vector(1024)`.
- **Action Required**:
  - Please ensure your embeddings are **1024 dimensions** (Amazon Titan Text Embeddings V2).
  - You can strictly assume the `embedding` field in the database is a vector type.

## 2. ✅ Global Merge Policy Implemented

We have implemented the **Option A (Hard Merge)** strategy as discussed.

- **Logic**:
  - We use `apoc.refactor.mergeNodes` to physically merge duplicate character nodes.
  - **Properties**: The primary node's properties are preserved ("discard" strategy for conflicts), assuming the AI agent selects the "best" primary candidate.
  - **Relationships**: All relationships from the merged (duplicate) nodes are moved to the primary node.

---

---

## 3. 🤝 Collaboration & Testing

### 3-1. Semantic Chunking Implementation
- We acknowledge the implementation of **Cosine Similarity-based Semantic Chunking** (Threshold: 0.6) using Amazon Titan V2 embeddings.
- We will proceed to test this feature by providing the requested specialized test data.

### 3-2. Testing Plan
- **Data Preparation**: We are creating long text scenarios with clear scene transitions (e.g., location changes, time jumps).
- **Execution**: We will trigger the document analysis pipeline with this data and verify the `sections` created in the DB align with expected boundaries.

