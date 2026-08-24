package com.veriprotocol.springAI.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.veriprotocol.springAI.sharding.ShardJdbcRouter;

@Repository


public class DocumentWriteDao {

    private final JdbcTemplate jdbcTemplate;
    private final ShardJdbcRouter shardJdbcRouter;

    public DocumentWriteDao(
            JdbcTemplate jdbcTemplate,
            ShardJdbcRouter shardJdbcRouter) {

        this.jdbcTemplate = jdbcTemplate;
        this.shardJdbcRouter = shardJdbcRouter;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
			return null;
		}
        return s.length() <= max ? s : s.substring(0, max);
    }


    public void insertPending(String id, String text, Instant createdAt, String contentHash) {
        jdbcTemplate.update("""
            INSERT INTO documents (id, text, created_at, updated_at, status, content_hash)
            VALUES (?, ?, ?, now(), 'PENDING', ?)
        """, id, text, Timestamp.from(createdAt), contentHash);
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

    public int updateProcessingStatus(String docId, DocumentStatus status) {
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




    public int markStuckProcessingAsFailed(int minutes) {
        return jdbcTemplate.update("""
            UPDATE documents
            SET status = 'FAILED',
                last_error = 'stuck PROCESSING (lease expired)',
                retry_count = retry_count + 1,
                next_retry_at = now() + interval '30 seconds',
                processing_started_at = NULL,
                worker_id = NULL,
                updated_at = now()
            WHERE status = 'PROCESSING'
              AND processing_started_at < now() - (? || ' minutes')::interval
        """, minutes);
    }
    


    public int forceToPending(
            String tenantId,
            String docId) {

        return jdbcTemplate.update("""
        UPDATE documents
        SET status = 'PENDING',
            last_error = NULL,
            processing_started_at = NULL,
            worker_id = NULL,
            next_retry_at = NULL,
            updated_at = now()
        WHERE tenant_id = ?
          AND id = ?
          AND status = 'FAILED'
        """,
                tenantId,
                docId
        );
    }

    public int claimPendingForRepublish(
            String tenantId,
            String docId,
            int cooldownSeconds) {

        return jdbcTemplate.update("""
        UPDATE documents
        SET updated_at = now()
        WHERE tenant_id = ?
          AND id = ?
          AND status = 'PENDING'
          AND updated_at <= now() - (? || ' seconds')::interval
        """,
                tenantId,
                docId,
                cooldownSeconds
        );
    }

    public List<String> findOldPendingDocIdsForTenant(
            String tenantId,
            int limit,
            int minAgeSeconds) {

        return jdbcTemplate.queryForList("""
        SELECT id
        FROM documents
        WHERE tenant_id = ?
          AND status = 'PENDING'
          AND updated_at <= now() - (? || ' seconds')::interval
        ORDER BY updated_at ASC
        LIMIT ?
        """,
                String.class,
                tenantId,
                minAgeSeconds,
                limit
        );
    }


    public int markRetryCycleExhausted(
            String tenantId,
            String docId,
            String errorMessage) {

        JdbcTemplate jdbc =
                shardJdbcRouter.jdbcFor(tenantId);

        String msg = truncate(errorMessage, 800);

        Integer retryCount = jdbc.queryForObject("""
        UPDATE documents
        SET status = 'FAILED',
            retry_count = retry_count + 1,
            last_error = ?,
            next_retry_at =
                now() + make_interval(
                    secs => LEAST(
                        600,
                        10 * (2 ^ LEAST(retry_count + 1, 6))
                    )::int
                ),
            processing_started_at = NULL,
            worker_id = NULL,
            updated_at = now()
        WHERE tenant_id = ?
          AND id = ?
          AND status = 'PROCESSING'
        RETURNING retry_count
        """,
                Integer.class,
                msg,
                tenantId,
                docId
        );

        return retryCount == null ? 0 : retryCount;
    }

}
