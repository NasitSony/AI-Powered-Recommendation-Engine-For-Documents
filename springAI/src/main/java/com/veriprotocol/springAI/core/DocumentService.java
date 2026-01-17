package com.veriprotocol.springAI.core;

import java.time.Instant;
import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veriprotocol.springAI.persistance.ChunkSearchDao;
import com.veriprotocol.springAI.persistance.DocumentChunkWriteDao;
import com.veriprotocol.springAI.persistance.DocumentEntity;
import com.veriprotocol.springAI.persistance.DocumentRepository;
import com.veriprotocol.springAI.persistance.PgVector;

@Service
public class DocumentService {
	
	private final EmbeddingModel embeddingModel;
    private final DocumentRepository docRepo; // optional
    private final DocumentChunkWriteDao chunkWriteDao;
    private final ChunkSearchDao chunkSearchDao;

	
    public DocumentService(EmbeddingModel embeddingModel,
            DocumentRepository docRepo,
            DocumentChunkWriteDao chunkWriteDao,
            ChunkSearchDao chunkSearchDao) {
            this.embeddingModel = embeddingModel;
            this.docRepo = docRepo;
            this.chunkWriteDao = chunkWriteDao;
            this.chunkSearchDao = chunkSearchDao;
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
    
    public List<ChunkSearchDao.ChunkHit> semanticSearchChunks(String query, int k) {
        String qVec = PgVector.toLiteral(embeddingModel.embed(query));
        return chunkSearchDao.searchTopK(qVec, k);
    }
    

}
