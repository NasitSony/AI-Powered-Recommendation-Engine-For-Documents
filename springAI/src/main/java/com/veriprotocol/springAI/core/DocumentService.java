package com.veriprotocol.springAI.core;
import com.veriprotocol.springAI.core.IngestProducer;


import java.time.Instant;
import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veriprotocol.springAI.persistance.ChunkSearchDao;
import com.veriprotocol.springAI.persistance.DocumentChunkWriteDao;
import com.veriprotocol.springAI.persistance.DocumentEntity;
import com.veriprotocol.springAI.persistance.DocumentRepository;
import com.veriprotocol.springAI.persistance.DocumentStatus;
import com.veriprotocol.springAI.persistance.PgVector;

@Service
public class DocumentService {
	
	private final EmbeddingModel embeddingModel;
    private final DocumentRepository docRepo; // optional
    private final DocumentChunkWriteDao chunkWriteDao;
    private final ChunkSearchDao chunkSearchDao;
    private final IngestProducer ingestProducer;

	
    public DocumentService(EmbeddingModel embeddingModel,
            DocumentRepository docRepo,
            DocumentChunkWriteDao chunkWriteDao,
            ChunkSearchDao chunkSearchDao,
            IngestProducer ingestProducer) {
            this.embeddingModel = embeddingModel;
            this.docRepo = docRepo;
            this.chunkWriteDao = chunkWriteDao;
            this.chunkSearchDao = chunkSearchDao;
            this.ingestProducer = ingestProducer;
    }
    
    @Transactional
    public void addDocument(String id, String text) {
        // Optional: store whole doc row (useful metadata)
        String docVec = PgVector.toLiteral(embeddingModel.embed(text));
        docRepo.save(new DocumentEntity(id, text, Instant.now(), docVec));

        // Chunk + embed + store
        chunkWriteDao.deleteByDocId(id);

        List<String> chunks = TextChunker.chunk(text, 2000);
        Instant now = Instant.now();

        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            String vec = PgVector.toLiteral(embeddingModel.embed(chunkText));
            chunkWriteDao.upsert(id, i, chunkText, now, vec);
        }
    }
    
    
    @Transactional
    public String createPending(String id, String text) {
        
        String hash = org.apache.commons.codec.digest.DigestUtils.sha256Hex(text);

        DocumentEntity existing = docRepo.findById(id).orElse(null);
        if (existing != null
                && hash.equals(existing.getContentHash())
                && existing.getStatus() == DocumentStatus.READY) {
            return existing.getId();
        }

        DocumentEntity doc = (existing != null) ? existing : new DocumentEntity(id, text);
        doc.setText(text);
        doc.setContentHash(hash);
        doc.setStatus(DocumentStatus.PENDING);
        doc.setLastError(null);
        doc.setEmbedding(null);

        docRepo.save(doc);

        ingestProducer.send(doc.getId(), hash);
        return doc.getId();
    }

    
    public List<ChunkSearchDao.ChunkHit> semanticSearchChunks(String query, int k) {
        String qVec = PgVector.toLiteral(embeddingModel.embed(query));
        return chunkSearchDao.searchTopK(qVec, k);
    }
    

}
