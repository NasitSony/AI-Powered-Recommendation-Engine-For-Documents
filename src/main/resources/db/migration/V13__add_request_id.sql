ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS request_id TEXT;
