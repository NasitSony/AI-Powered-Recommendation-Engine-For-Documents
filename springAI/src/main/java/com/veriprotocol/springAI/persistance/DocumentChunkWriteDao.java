package com.veriprotocol.springAI.persistance;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentChunkWriteDao {
	
	private final JdbcTemplate jdbc;

    public DocumentChunkWriteDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
    
    public void deleteByDocId(String docId) {
        jdbc.update("DELETE FROM document_chunks WHERE doc_id = ?", docId);
    }
    
    public void upsert(String docId, int chunkId, String chunkText, Instant createdAt, String vectorLiteral) {
        String sql = """
            INSERT INTO document_chunks (doc_id, chunk_id, chunk_text, created_at, embedding)
            VALUES (?, ?, ?, ?, CAST(? AS vector))
            ON CONFLICT (doc_id, chunk_id)
            DO UPDATE SET chunk_text = EXCLUDED.chunk_text,
                          created_at = EXCLUDED.created_at,
                          embedding = EXCLUDED.embedding
        """;

        jdbc.update(sql,
                docId,
                chunkId,
                chunkText,
                Timestamp.from(createdAt),
                vectorLiteral
        );
    }

}
