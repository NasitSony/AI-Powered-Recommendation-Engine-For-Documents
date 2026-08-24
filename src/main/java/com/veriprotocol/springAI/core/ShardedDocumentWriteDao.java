package com.veriprotocol.springAI.sharding;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ShardedDocumentWriteDao {

    private final ShardJdbcRouter shardJdbcRouter;

    public ShardedDocumentWriteDao(
            ShardJdbcRouter shardJdbcRouter) {
        this.shardJdbcRouter = shardJdbcRouter;
    }

    public String insertPendingIfAbsent(
            String id,
            String tenantId,
            String requestId,
            String text,
            String contentHash) {

        JdbcTemplate jdbc =
                shardJdbcRouter.jdbcFor(tenantId);

        var ids = jdbc.queryForList("""
            INSERT INTO documents (
                id,
                tenant_id,
                request_id,
                text,
                content_hash,
                status,
                retry_count,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, 'PENDING', 0, now(), now())
            ON CONFLICT (tenant_id, request_id) DO NOTHING
            RETURNING id
            """,
                String.class,
                id,
                tenantId,
                requestId,
                text,
                contentHash
        );

        return ids.isEmpty() ? null : ids.get(0);
    }

    public Optional<ExistingDocument> findByTenantAndRequestId(
            String tenantId,
            String requestId) {

        JdbcTemplate jdbc =
                shardJdbcRouter.jdbcFor(tenantId);

        var rows = jdbc.query("""
            SELECT id, content_hash
            FROM documents
            WHERE tenant_id = ?
              AND request_id = ?
            """,
                (rs, rowNum) -> new ExistingDocument(
                        rs.getString("id"),
                        rs.getString("content_hash")
                ),
                tenantId,
                requestId
        );

        return rows.stream().findFirst();
    }

    public int updateLastError(
            String tenantId,
            String docId,
            String error) {

        JdbcTemplate jdbc =
                shardJdbcRouter.jdbcFor(tenantId);

        return jdbc.update("""
        UPDATE documents
        SET last_error = ?,
            updated_at = now()
        WHERE tenant_id = ?
          AND id = ?
        """,
                error,
                tenantId,
                docId
        );
    }

    public record ExistingDocument(
            String id,
            String contentHash) {}
}