package com.veriprotocol.springAI.persistance;


import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository


public class DocumentWriteDao {

	private final JdbcTemplate jdbcTemplate;

    public DocumentWriteDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(String id, String text, Instant createdAt, String embeddingLiteral) {
        jdbcTemplate.update("""
            insert into documents (id, text, created_at, embedding)
            values (?, ?, ?, ?::vector)
        """, id, text, Timestamp.from(createdAt), embeddingLiteral);
    }

    public void upsert(String id, String text, Instant createdAt, String embeddingLiteral) {
        jdbcTemplate.update("""
            insert into documents (id, text, created_at, embedding)
            values (?, ?, ?, ?::vector)
            on conflict (id) do update set
                text = excluded.text,
                created_at = excluded.created_at,
                embedding = excluded.embedding
        """, id, text, Timestamp.from(createdAt), embeddingLiteral);
    }

    public int updateStatus(String docId, DocumentStatus status) {
        return jdbcTemplate.update("""
            UPDATE documents
            SET status = ?
            WHERE id = ?
        """, status.name(), docId);
    }
    
    public int updateProcessigStatus(String docId, DocumentStatus status) {
        return jdbcTemplate.update("""
            UPDATE documents
            SET status = ?
            WHERE id = ? AND status = 'PENDING'
        """, status.name(), docId);
    }

    public int updateStatusAndError(String docId, DocumentStatus status, String msg) {
        return jdbcTemplate.update("""
                UPDATE documents
                SET status = ?,
                last_error = ?,
                updated_at = NOW()
                WHERE id = ?
            """, status.name(), msg, docId);
     }

}
