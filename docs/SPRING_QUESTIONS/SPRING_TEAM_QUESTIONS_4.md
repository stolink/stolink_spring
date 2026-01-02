# Questions for Integration Guide #2

## 1. Callback URL Mismatch
- **Current Implementation**: `AIController` constructs the callback URL as `callbackBaseUrl + "/analysis/callback"` (which resolves to `/api/internal/ai/analysis/callback`).
- **Integration Guide**: Requires the callback to be sent to `/api/ai-callback` to handle the new `DOCUMENT_ANALYSIS_RESULT` message type.
- **Question**: Should we modify `AIController` to send `.../api/ai-callback` instead? And should we ensure `callbackBaseUrl` uses the docker container name `stolink-backend` instead of `host.docker.internal` for better reliability within the shared network?

## 2. Trigger Payload Structure
- **Current Implementation**: `AIController` sends `AnalysisTaskDTO`.
- **Integration Guide**: Describes a payload with `message_type: "DOCUMENT_ANALYSIS"`.
- **Question**: Does `AnalysisTaskDTO` need to be updated to include `message_type`, or will the `RabbitMQProducerService` handle this wrapping? We need to ensure the payload defined in `Scenario A` is exactly what we publish to RabbitMQ.

## 3. Global Merge Trigger
- **Question**: Where is the functionality to trigger "Scenario B (Global Merge)"? Is it automatic after document anlaysis (as hinted in `AICallbackService`), or do we need to implement a manual trigger endpoint in `AIController`?
