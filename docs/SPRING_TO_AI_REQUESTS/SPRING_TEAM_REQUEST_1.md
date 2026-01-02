# Spring Team Request #1 (To. AI Team)

> **Date**: 2026-01-02
> **From**: Spring Team
> **Subject**: Preparation for Integration & Testing

---

Based on the recent discussions and integration guidelines, we request the following preparations from the AI Team:

## 1. 🔍 Queue Monitoring (Test Phase)
- We will be implementing the **Manual Global Merge Trigger** (`POST /api/project/{projectId}/merge`).
- Please monitor the `global_merge_queue` for incoming messages with `message_type: "GLOBAL_MERGE"`.
- Verify that your consumer correctly processes these messages and sends a callback to `http://stolink-backend:8080/api/ai-callback` with `message_type: "GLOBAL_MERGE_RESULT"`.

## 2. 🧪 Semantic Chunking Validation
- We are preparing the "Long Text Scenario" data as requested.
- Once we trigger the analysis with this data, please verify:
  - **Segmentation**: Are sections split reasonably at scene boundaries?
  - **Embeddings**: Are 1024-dim vectors generated correctly for each section?

## 3. 📝 Payload Validation
- We are updating our `AnalysisTaskDTO` to include `message_type: "DOCUMENT_ANALYSIS"`.
- Please ensure your Pydantic model (`DocumentAnalysisMessage`) strictness allows for any potential extra fields if we add them in the future (currently we strictly follow the schema).

---

We will notify you once the deployment of our updated `AIController` is complete.
