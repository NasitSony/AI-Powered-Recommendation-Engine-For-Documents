-- Ensure pgvector is available
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS document_chunks (
  doc_id     TEXT NOT NULL,
  chunk_id   INT  NOT NULL,
  chunk_text TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  embedding  VECTOR(1536) NOT NULL,
  PRIMARY KEY (doc_id, chunk_id)
);

CREATE INDEX IF NOT EXISTS idx_document_chunks_doc_id ON document_chunks (doc_id);
