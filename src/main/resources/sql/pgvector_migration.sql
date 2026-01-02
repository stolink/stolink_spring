-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Migrate sections table embedding column
-- 1. Check if column exists and has data (optional safety check omitted for simplicity)
-- 2. Alter column type. Note: This clears existing data if casting fails, but we use explicit casting just in case it works (it won't for text->vector without specific format).
--    Since we decided to NULLify incompatible data, we can just drop and recreate or alter with USING.
--    However, strict migration:

ALTER TABLE sections
ALTER COLUMN embedding TYPE vector (1024) USING (embedding::jsonb)::vector (1024);
-- Try to cast if compatible (array of 1024 floats)

-- If the above fails due to incompatible data, one might need:
-- ALTER TABLE sections DROP COLUMN embedding;
-- ALTER TABLE sections ADD COLUMN embedding vector(1024);

-- Create index for semantic search
CREATE INDEX IF NOT EXISTS idx_sections_embedding ON sections USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);