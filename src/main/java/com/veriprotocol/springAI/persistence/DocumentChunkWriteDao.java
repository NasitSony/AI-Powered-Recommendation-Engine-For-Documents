package com.veriprotocol.springAI.persistence;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import org.springframework.jdbc.core.JdbcTemplate;
import com.veriprotocol.springAI.sharding.ShardJdbcRouter;

@Repository
public class DocumentChunkWriteDao {

    private final ShardJdbcRouter shardJdbcRouter;

    public DocumentChunkWriteDao(
            ShardJdbcRouter shardJdbcRouter) {
        this.shardJdbcRouter = shardJdbcRouter;
    }

    public void deleteByDocId(
            String tenantId,
            String docId) {

        JdbcTemplate jdbc =
                shardJdbcRouter.jdbcFor(tenantId);

        jdbc.update("""
        DELETE FROM document_chunks
        WHERE tenant_id = ?
          AND doc_id = ?
        """,
                tenantId,
                docId
        );
    }

    public void upsert(
            String tenantId,
            String docId,
            int chunkId,
            String chunkText,
            Instant createdAt,
            String vectorLiteral) {

        JdbcTemplate jdbc =
                shardJdbcRouter.jdbcFor(tenantId);

        String sql = """
        INSERT INTO document_chunks (
            tenant_id,
            doc_id,
            chunk_id,
            chunk_text,
            created_at,
            embedding
        )
        VALUES (?, ?, ?, ?, ?, CAST(? AS vector))
        ON CONFLICT (doc_id, chunk_id)
        DO UPDATE SET
            tenant_id = EXCLUDED.tenant_id,
            chunk_text = EXCLUDED.chunk_text,
            created_at = EXCLUDED.created_at,
            embedding = EXCLUDED.embedding
        """;

        jdbc.update(
                sql,
                tenantId,
                docId,
                chunkId,
                chunkText,
                Timestamp.from(createdAt),
                vectorLiteral
        );
    }
}
