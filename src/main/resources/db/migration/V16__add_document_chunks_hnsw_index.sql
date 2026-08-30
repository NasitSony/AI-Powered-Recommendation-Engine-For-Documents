CREATE INDEX IF NOT EXISTS idx_document_chunks_embedding_hnsw
ON document_chunks
USING hnsw (embedding vector_l2_ops)
WITH (
    m = 16,
    ef_construction = 64
);
