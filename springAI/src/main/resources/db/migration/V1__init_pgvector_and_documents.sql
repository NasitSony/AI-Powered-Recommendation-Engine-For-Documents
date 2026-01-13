CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documents (
  id TEXT PRIMARY KEY,
  text TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  embedding vector(1536) NOT NULL
);

-- Helpful index for text search fallback later (optional)
-- CREATE INDEX documents_text_gin ON documents USING gin (to_tsvector('english', text));

-- For v0.2 we can do exact search (no ANN).
-- In v0.3 you’ll add HNSW/IVFFlat indexes for speed:
-- CREATE INDEX documents_embedding_hnsw ON documents USING hnsw (embedding vector_cosine_ops);
