package com.veriprotocol.springAI.core;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


import com.veriprotocol.springAI.controller.api.dto.DocumentStatusDto;
import com.veriprotocol.springAI.persistence.ChunkSearchDao;
import com.veriprotocol.springAI.persistence.DocumentChunkWriteDao;
import com.veriprotocol.springAI.persistence.DocumentEntity;
import com.veriprotocol.springAI.persistence.DocumentReadDao;
import com.veriprotocol.springAI.persistence.DocumentRepository;
import com.veriprotocol.springAI.persistence.DocumentStatus;
import com.veriprotocol.springAI.persistence.DocumentWriteDao;
import com.veriprotocol.springAI.persistence.PgVector;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DocumentService{

	private final EmbeddingModel embeddingModel;
    private final DocumentRepository docRepo; // optional
    private final DocumentChunkWriteDao chunkWriteDao;
    private final ChunkSearchDao chunkSearchDao;
    private final IngestProducer ingestProducer;
    private final DocumentWriteDao documentWriteDao;
    private final DocumentReadDao documentReadDao;




    public DocumentService(EmbeddingModel embeddingModel,
            DocumentRepository docRepo,
            DocumentChunkWriteDao chunkWriteDao,
            ChunkSearchDao chunkSearchDao,
            IngestProducer ingestProducer,
            DocumentWriteDao documentWriteDao, DocumentReadDao documentReadDao) {
            this.embeddingModel = embeddingModel;
            this.docRepo = docRepo;
            this.chunkWriteDao = chunkWriteDao;
            this.chunkSearchDao = chunkSearchDao;
            this.ingestProducer = ingestProducer;
            this.documentWriteDao = documentWriteDao;
            this.documentReadDao = documentReadDao;
    }

    //@Transactional
    public void addDocument(String id, String text) {

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("document text is null/blank");
        }

        if (text.contains("FAILME")) {
            throw new RuntimeException("forced failure");
        }

        long totalStart = System.nanoTime();

        // 1. Delete existing chunks
        long t0 = System.nanoTime();
        chunkWriteDao.deleteByDocId(id);
        long deleteMs = (System.nanoTime() - t0) / 1_000_000;

        // 2. Chunking
        t0 = System.nanoTime();
        List<String> chunks = TextChunker.chunk(text, 2000);
        long chunkingMs = (System.nanoTime() - t0) / 1_000_000;

        Instant now = Instant.now();

        long embeddingNs = 0;
        long dbWriteNs = 0;

        // 3. Embed + write
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);

            long embedStart = System.nanoTime();
            String vec = PgVector.toLiteral(embeddingModel.embed(chunkText));
            embeddingNs += System.nanoTime() - embedStart;

            long writeStart = System.nanoTime();
            chunkWriteDao.upsert(id, i, chunkText, now, vec);
            dbWriteNs += System.nanoTime() - writeStart;
        }

        long totalMs = (System.nanoTime() - totalStart) / 1_000_000;

        log.info(
                "metric=ingest_stage docId={} chunks={} delete_ms={} chunking_ms={} embedding_ms={} chunk_write_ms={} add_document_total_ms={}",
                id,
                chunks.size(),
                deleteMs,
                chunkingMs,
                embeddingNs / 1_000_000,
                dbWriteNs / 1_000_000,
                totalMs
        );
    }

    @Transactional

    public String createPending(String requestId, String text) {

        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException(
                    "requestId (Idempotency-Key) must not be null/blank");
        }

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be null/blank");
        }

        String hash =
                org.apache.commons.codec.digest.DigestUtils.sha256Hex(text);

        String candidateId = java.util.UUID.randomUUID().toString();

        String insertedId =
                documentWriteDao.insertPendingIfAbsent(
                        candidateId,
                        requestId,
                        text,
                        hash
                );

        // We won the race and created the logical request.
        if (insertedId != null) {

            final String docId = insertedId;

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                ingestProducer.send(docId);
                            } catch (Exception ex) {
                                log.error(
                                        "Kafka publish failed docId={}",
                                        docId,
                                        ex
                                );

                                markPublishFailed(
                                        docId,
                                        safeMsg(ex)
                                );
                            }
                        }
                    }
            );

            return docId;
        }

        // Another concurrent request already created this requestId.
        DocumentEntity existing =
                docRepo.findByRequestId(requestId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "requestId conflicted but existing row was not found: "
                                                + requestId
                                )
                        );

        if (!hash.equals(existing.getContentHash())) {
            throw new IdempotencyConflictException(
                    "requestId already exists with different content"
            );
        }

        return existing.getId();
    }
    private void markPublishFailed(String docId, String err) {
        docRepo.updateLastError(docId, "PUBLISH_FAILED: " + err);
    }

    public List<ChunkSearchDao.ChunkHit> semanticSearchChunks(String query, int k) {
        String qVec = PgVector.toLiteral(embeddingModel.embed(query));
        return chunkSearchDao.searchTopK(qVec, k);
    }

    public boolean claimProcessingLease(String docId, String workerId) {
       return documentWriteDao.claimProcessingLease(docId, workerId);
    }

    public void markReadyDb(String docId) {
        int updated = documentWriteDao.markReady(docId);

        if (updated != 1) {
            throw new IllegalStateException(
                    "Invalid READY transition for docId=" + docId
            );
        }
    }


    public int markRetryCycleExhausted(String docId, String msg) {
        return documentWriteDao.markRetryCycleExhausted(docId, msg);
    }
   // public void markError(String docId, String msg) {
      //  documentWriteDao.updateStatus(docId, DocumentStatus.ERROR);
    //}

    public void markFailedDb(String docId, String msg) {
    	  documentWriteDao.markFailed(docId, msg);
    	  log.error("✅ markFailed rowsUpdated={} docId={}", docId, msg);
    }

    public Optional<DocumentStatusDto> getStatus(String id) {
        return documentReadDao.findStatusById(id);
    }

    public List<DocumentStatusDto> listByStatus(String status, int limit) {
        return documentReadDao.listByStatus(status, limit);
    }

    private static String safeMsg(Throwable t) {
        if (t == null) return "unknown";
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) return t.getClass().getSimpleName();
        // Avoid huge DB error strings
        msg = msg.replaceAll("\\s+", " ").trim();
        return (msg.length() > 500) ? msg.substring(0, 500) : msg;
    }
}
