-- AI Handoff Integration Migration
-- Date: 2026-01-04
-- Description: Add fields required for AI Backend direct DB writes

-- Documents table: Add analysis_locked field for concurrency control
ALTER TABLE documents
ADD COLUMN IF NOT EXISTS analysis_locked BOOLEAN DEFAULT false;

-- Events table: Add fields for AI extraction
-- Note: AI may create this table directly, but we ensure columns exist
ALTER TABLE events ADD COLUMN IF NOT EXISTS document_id UUID;

ALTER TABLE events ADD COLUMN IF NOT EXISTS chapter INT DEFAULT 0;

ALTER TABLE events
ADD COLUMN IF NOT EXISTS sequence_order INT DEFAULT 0;

ALTER TABLE events ADD COLUMN IF NOT EXISTS participants TEXT;

ALTER TABLE events
ADD COLUMN IF NOT EXISTS location_ref VARCHAR(255);

-- Add foreign key if events table exists and has document_id
-- ALTER TABLE events ADD CONSTRAINT fk_events_document_id
--     FOREIGN KEY (document_id) REFERENCES documents(id);

-- Create index for efficient document queries
CREATE INDEX IF NOT EXISTS idx_events_document_id ON events (document_id);

-- Callback logs table: For idempotent callback processing
CREATE TABLE IF NOT EXISTS callback_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    job_id VARCHAR(255) NOT NULL UNIQUE,
    message_type VARCHAR(50),
    status VARCHAR(20),
    processed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    document_id UUID,
    project_id UUID
);

CREATE INDEX IF NOT EXISTS idx_callback_logs_job_id ON callback_logs (job_id);