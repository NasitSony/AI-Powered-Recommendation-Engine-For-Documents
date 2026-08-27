package com.veriprotocol.springAI.core;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.veriprotocol.springAI.sharding.ShardedIngestDao;
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
import com.veriprotocol.springAI.sharding.ShardedDocumentWriteDao;

import com.veriprotocol.springAI.cache.SearchCache;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;

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
    private final ShardedDocumentWriteDao shardedDocumentWriteDao;
    private final ShardedIngestDao shardedIngestDao;

    private final SearchCache searchCache;
    private final ObjectMapper objectMapper;


    public DocumentService(EmbeddingModel embeddingModel,
            DocumentRepository docRepo,
            DocumentChunkWriteDao chunkWriteDao,
            ChunkSearchDao chunkSearchDao,
            IngestProducer ingestProducer,
            DocumentWriteDao documentWriteDao, DocumentReadDao documentReadDao, ShardedDocumentWriteDao shardedDocumentWriteDao, ShardedIngestDao shardedIngestDao,
                           SearchCache searchCache,
                           ObjectMapper objectMapper) {
            this.embeddingModel = embeddingModel;
            this.docRepo = docRepo;
            this.chunkWriteDao = chunkWriteDao;
            this.chunkSearchDao = chunkSearchDao;
            this.ingestProducer = ingestProducer;
            this.documentWriteDao = documentWriteDao;
            this.documentReadDao = documentReadDao;
            this.shardedDocumentWriteDao = shardedDocumentWriteDao;
            this.shardedIngestDao = shardedIngestDao;
            this.searchCache = searchCache;
            this.objectMapper = objectMapper;
    }

    //@Transactional
    public void addDocument(String tenantId, String id, String text) {

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("document text is null/blank");
        }

        if (text.contains("FAILME")) {
            throw new RuntimeException("forced failure");
        }

        long totalStart = System.nanoTime();

        // 1. Delete existing chunks
        long t0 = System.nanoTime();
        chunkWriteDao.deleteByDocId(tenantId, id);
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
            chunkWriteDao.upsert(tenantId, id, i, chunkText, now, vec);
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

    public Optional<DocumentStatusDto> getStatusInternal(
            String tenantId,
            String docId) {

        return shardedIngestDao.findStatus(
                tenantId,
                docId
        );
    }

    public boolean claimProcessingLease(
            String tenantId,
            String docId,
            String workerId) {

        return shardedIngestDao.claimProcessingLease(
                tenantId,
                docId,
                workerId
        );
    }

    public DocumentReadDao.DocPayload loadDocPayload(
            String tenantId,
            String docId) {

        return shardedIngestDao.loadDocPayload(
                tenantId,
                docId
        );
    }

    public void markReadyDb(
            String tenantId,
            String docId) {

        int updated =
                shardedIngestDao.markReady(
                        tenantId,
                        docId
                );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Invalid READY transition tenantId="
                            + tenantId
                            + " docId="
                            + docId
            );
        }
    }

    @Transactional

    public String createPending(
            String tenantId,
            String requestId,
            String text) {

        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null/blank");
        }

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
                shardedDocumentWriteDao.insertPendingIfAbsent(
                        candidateId,
                        tenantId,
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
                                ingestProducer.send(tenantId, docId);
                            } catch (Exception ex) {
                                log.error(
                                        "Kafka publish failed docId={}",
                                        docId,
                                        ex
                                );

                                markPublishFailed(
                                        tenantId,
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

                ShardedDocumentWriteDao.ExistingDocument existing =
                shardedDocumentWriteDao
                        .findByTenantAndRequestId(
                                tenantId,
                                requestId
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "requestId conflicted but existing row was not found: "
                                                + requestId
                                )
                        );

        if (!hash.equals(existing.contentHash())) {
            throw new IdempotencyConflictException(
                    "requestId already exists with different content for tenantId="
                            + tenantId
            );
        }

        return existing.id();
    }
    private void markPublishFailed(
            String tenantId,
            String docId,
            String err) {

        shardedDocumentWriteDao.updateLastError(
                tenantId,
                docId,
                "PUBLISH_FAILED: " + err
        );
    }

    public void invalidateSearchCache(String tenantId) {

        try {

            searchCache.invalidateTenant(tenantId);

            log.info(
                    "metric=search_cache_invalidate tenantId={}",
                    tenantId
            );

        } catch (Exception e) {

            // Redis remains an optimization.
            log.warn(
                    "Redis cache invalidation failed tenantId={} error={}",
                    tenantId,
                    e.getMessage()
            );
        }
    }

    public List<ChunkSearchDao.ChunkHit> semanticSearchChunks(
            String tenantId,
            String query,
            int k) {

        String queryHash =
                org.apache.commons.codec.digest.DigestUtils.sha256Hex(
                        query.trim().toLowerCase()
                );

        String cacheKey =
                "search:"
                        + tenantId
                        + ":"
                        + queryHash
                        + ":"
                        + k;
        boolean cacheAvailable = true;

        // 1. Try Redis first
        try {
            Optional<String> cached = searchCache.get(cacheKey);

            if (cached.isPresent()) {
                log.info(
                        "metric=search_cache_hit tenantId={} key={}",
                        tenantId,
                        cacheKey
                );

                return objectMapper.readValue(
                        cached.get(),
                        new TypeReference<List<ChunkSearchDao.ChunkHit>>() {}
                );
            }

            log.info(
                    "metric=search_cache_miss tenantId={} key={}",
                    tenantId,
                    cacheKey
            );

        } catch (Exception e) {

            cacheAvailable = false;

            log.warn(
                    "Redis cache read failed tenantId={} key={} error={}",
                    tenantId,
                    cacheKey,
                    e.getMessage()
            );
        }

        // 2. Cache miss / Redis unavailable:
        // do the normal expensive path
        String qVec = PgVector.toLiteral(
                embeddingModel.embed(query)
        );

        List<ChunkSearchDao.ChunkHit> hits =
                chunkSearchDao.searchTopK(
                        tenantId,
                        qVec,
                        k
                );

        // 3. Best-effort cache populate
        if (cacheAvailable) {

            try {

                String json =
                        objectMapper.writeValueAsString(hits);

                searchCache.put(
                        cacheKey,
                        json,
                        Duration.ofSeconds(60)
                );

            } catch (Exception e) {

                log.warn(
                        "Redis cache write failed tenantId={} key={} error={}",
                        tenantId,
                        cacheKey,
                        e.getMessage()
                );
            }
        }
        return hits;
    }





    public int markRetryCycleExhausted(
            String tenantId,
            String docId,
            String msg) {

        return documentWriteDao.markRetryCycleExhausted(
                tenantId,
                docId,
                msg
        );
    }
   // public void markError(String docId, String msg) {
      //  documentWriteDao.updateStatus(docId, DocumentStatus.ERROR);
    //}

    public Optional<DocumentStatusDto> getStatus(
            String tenantId,
            String id) {

        return documentReadDao.findStatusByTenantAndId(
                tenantId,
                id
        );
    }

    public List<DocumentStatusDto> listByStatus(
            String tenantId,
            String status,
            int limit) {

        return documentReadDao.listByTenantAndStatus(
                tenantId,
                status,
                limit
        );
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
