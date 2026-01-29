package com.veriprotocol.springAI.core;


import java.time.Instant;
import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import com.veriprotocol.springAI.persistance.DocumentChunkWriteDao;

@Service
public class EmbeddingService {


	private final EmbeddingModel embeddingModel;
   // private final DocumentRepository documentRepository;      // <- you must have this (JPA repo or DAO wrapper)
  //  private final TextChunker textChunker;                    // <- your chunking utility/service
    private final DocumentChunkWriteDao documentChunkWriteDao; // <- DAO that inserts chunks + vectors


 // You can tune this; start conservative for embeddings
    private static final int DEFAULT_MAX_CHARS = 1200;


    public EmbeddingService(EmbeddingModel embeddingModel,
            DocumentChunkWriteDao documentChunkWriteDao) {
            this.embeddingModel = embeddingModel;
            this.documentChunkWriteDao = documentChunkWriteDao;
    }


  /*  public EmbeddingService(
            EmbeddingModel embeddingModel,
            DocumentRepository documentRepository,
            TextChunker textChunker,
            DocumentChunkWriteDao documentChunkWriteDao
    ) {
        this.embeddingModel = embeddingModel;
        this.documentRepository = documentRepository;
        this.textChunker = textChunker;
        this.documentChunkWriteDao = documentChunkWriteDao;
    *}

    /*public float[] embed(String text) {
        EmbeddingResponse resp = embeddingModel.embedForResponse(Arrays.asList(text));
        float[] out = resp.getResults().get(0).getOutput();
        float[] v = new float[out.length];
        for (int i = 0; i < out.length; i++) v[i] = out[i];
        return v;
    }*/

    /** Embed a single text into float[] */
    public float[] embed(String text) {
        EmbeddingResponse resp = embeddingModel.embedForResponse(List.of(text));
        return resp.getResults().get(0).getOutput();
    }


    /** Convert float[] -> pgvector literal like: [0.1,0.2,...] */
    public static String toPgVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
				sb.append(',');
			}
            sb.append(Float.toString(v[i]));
        }
        sb.append(']');
        return sb.toString();
    }


    public void processDocument(String docId, String content) {
        List<String> chunks = TextChunker.chunk(content, DEFAULT_MAX_CHARS);
        Instant now = Instant.now();

        for (int chunkId = 0; chunkId < chunks.size(); chunkId++) {
            String chunkText = chunks.get(chunkId);

            float[] emb = embed(chunkText);
            String vectorLiteral = toPgVectorLiteral(emb);

            documentChunkWriteDao.upsert(docId, chunkId, chunkText, now, vectorLiteral);
        }
    }

}
