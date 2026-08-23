package com.veriprotocol.springAI.sharding;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.veriprotocol.springAI.controller.api.dto.DocumentStatusDto;
import com.veriprotocol.springAI.persistence.DocumentReadDao;

@Repository
public class ShardedIngestDao {

    private final ShardJdbcRouter shardJdbcRouter;

    public ShardedIngestDao(ShardJdbcRouter shardJdbcRouter) {
        this.shardJdbcRouter = shardJdbcRouter;
    }

    public Optional<DocumentStatusDto> findStatus(
            String tenantId,
            String docId) {

        JdbcTemplate jdbc = shardJdbcRouter.jdbcFor(tenantId);

        var rows = jdbc.query("""
            SELECT id, status,
                   COALESCE(retry_count, 0) AS retry_count,
                   created_at, updated_at,
                   last_error, worker_id,
                   processing_started_at, next_retry_at
            FROM documents
            WHERE tenant_id = ?
              AND id = ?
            """,
                (rs, rowNum) -> new DocumentStatusDto(
                        rs.getString("id"),
                        rs.getString("status"),
                        rs.getInt("retry_count"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class),
                        rs.getString("last_error"),
                        rs.getString("worker_id"),
                        rs.getObject("processing_started_at", OffsetDateTime.class),
                        rs.getObject("next_retry_at", OffsetDateTime.class)
                ),
                tenantId,
                docId
        );

        return rows.stream().findFirst();
    }

    public boolean claimProcessingLease(
            String tenantId,
            String docId,
            String workerId) {

        JdbcTemplate jdbc = shardJdbcRouter.jdbcFor(tenantId);

        var rows = jdbc.queryForList("""
            UPDATE documents
            SET status = 'PROCESSING',
                worker_id = ?,
                processing_started_at =
                    CASE
                        WHEN status = 'PROCESSING'
                        THEN processing_started_at
                        ELSE now()
                    END,
                updated_at = now()
            WHERE tenant_id = ?
              AND id = ?
              AND (
                    status IN ('PENDING', 'FAILED')
                    OR
                    (status = 'PROCESSING' AND worker_id = ?)
                  )
            RETURNING id
            """,
                String.class,
                workerId,
                tenantId,
                docId,
                workerId
        );

        return !rows.isEmpty();
    }

    public DocumentReadDao.DocPayload loadDocPayload(
            String tenantId,
            String docId) {

        JdbcTemplate jdbc = shardJdbcRouter.jdbcFor(tenantId);

        return jdbc.queryForObject("""
            SELECT id, tenant_id, text, content_hash
            FROM documents
            WHERE tenant_id = ?
              AND id = ?
            """,
                (rs, rowNum) -> new DocumentReadDao.DocPayload(
                        rs.getString("id"),
                        rs.getString("tenant_id"),
                        rs.getString("text"),
                        rs.getString("content_hash")
                ),
                tenantId,
                docId
        );
    }

    public int markReady(
            String tenantId,
            String docId) {

        JdbcTemplate jdbc = shardJdbcRouter.jdbcFor(tenantId);

        return jdbc.update("""
            UPDATE documents
            SET status = 'READY',
                last_error = NULL,
                next_retry_at = NULL,
                processing_started_at = NULL,
                worker_id = NULL,
                updated_at = now()
            WHERE tenant_id = ?
              AND id = ?
              AND status = 'PROCESSING'
            """,
                tenantId,
                docId
        );
    }
}