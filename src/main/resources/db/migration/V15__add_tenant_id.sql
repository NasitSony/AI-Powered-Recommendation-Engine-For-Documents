ALTER TABLE documents
    ADD COLUMN tenant_id TEXT;

ALTER TABLE document_chunks
    ADD COLUMN tenant_id TEXT;

-- Backfill existing development data.
UPDATE documents
SET tenant_id = 'default'
WHERE tenant_id IS NULL;

UPDATE document_chunks c
SET tenant_id = d.tenant_id
FROM documents d
WHERE c.doc_id = d.id
  AND c.tenant_id IS NULL;

ALTER TABLE documents
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE document_chunks
    ALTER COLUMN tenant_id SET NOT NULL;

-- request_id should now be unique only inside a tenant.
DROP INDEX IF EXISTS ux_documents_request_id;

CREATE UNIQUE INDEX ux_documents_tenant_request_id
    ON documents (tenant_id, request_id);

CREATE INDEX idx_documents_tenant_id
    ON documents (tenant_id);

CREATE INDEX idx_document_chunks_tenant_doc
    ON document_chunks (tenant_id, doc_id);