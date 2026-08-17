package com.veriprotocol.springAI.persistence;

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


   /* public boolean claimProcessingLease(String docId, String workerId) {
      var rows = jdbcTemplate.queryForList("""
        INSERT INTO documents (id, status, worker_id, processing_started_at, retry_count, updated_at)
        VALUES (?, 'PROCESSING', ?, now(), 0, now())
        ON CONFLICT (id) DO UPDATE
        SET status = 'PROCESSING',
            worker_id = EXCLUDED.worker_id,
            processing_started_at = now(),
            updated_at = now()
        WHERE documents.status IN ('PENDING', 'FAILED')
        RETURNING id
    """, docId, workerId);

    return !rows.isEmpty();
    }*/


    public boolean claimProcessingLease(String docId, String workerId) {

        var rows = jdbcTemplate.queryForList("""
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
        WHERE id = ?
          AND (
                status IN ('PENDING', 'FAILED')
                OR
                (status = 'PROCESSING' AND worker_id = ?)
              )
        RETURNING id
        """,
                String.class,
                workerId,
                docId,
                workerId
        );

        return !rows.isEmpty();
    }

    public void markReady(String docId) {
        jdbcTemplate.update("""
           UPDATE documents
           SET status = 'READY',
              last_error = NULL,
              next_retry_at = NULL,
              processing_started_at = NULL,
              worker_id = NULL,
              updated_at = now()
           WHERE id = ?
          """, docId);
   }





    public void markFailed(String docId, String errorMessage) {
        String msg = truncate(errorMessage, 800); // adjust if your column allows more

        // read current retry_count (or assume 0 if missing)
        jdbcTemplate.update("""
              UPDATE documents
              SET status = 'FAILED',
                  last_error = ?,
                  next_retry_at = now() + make_interval(secs => LEAST(600, 10 * (2 ^ LEAST(retry_count, 6)))::int),
                  processing_started_at = NULL,
                  worker_id = NULL,
                  updated_at = now()
                  WHERE id = ?
                """, msg, docId);
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



    public int resetFailedToPending(String docId) {
        return jdbcTemplate.update("""
            UPDATE documents
            SET status = 'PENDING',
              last_error = NULL,
              next_retry_at = NULL,
              processing_started_at = NULL,
              worker_id = NULL,
              updated_at = now()
            WHERE id = ?
            AND status = 'FAILED'
            AND (next_retry_at IS NULL OR next_retry_at <= now())
           """, docId);
    }


    public int forceToPending(String docId) {
        return jdbcTemplate.update("""
            UPDATE documents
            SET status = 'PENDING',
                last_error = NULL,
                processing_started_at = NULL,
                worker_id = NULL,
                next_retry_at = NULL,
                updated_at = now()
            WHERE id = ?
              AND status IN ('FAILED')
        """, docId);
    }


    public void incrementRetryAndRequeue(String docId) {
    jdbcTemplate.update("""
        UPDATE documents
        SET retry_count = retry_count + 1,
            status = 'PENDING',
            next_retry_at = NULL,
            updated_at = now()
        WHERE id = ?
          AND status = 'FAILED'
          AND (next_retry_at IS NULL OR next_retry_at <= now())
    """, docId);
}
    
    public int claimPendingForRepublish(String docId, int cooldownSeconds) {
        return jdbcTemplate.update("""
            UPDATE documents
            SET updated_at = now()
            WHERE id = ?
              AND status = 'PENDING'
              AND updated_at <= now() - (? || ' seconds')::interval
        """, docId, cooldownSeconds);
    }

    public int markRetryCycleExhausted(String docId, String errorMessage) {
        String msg = truncate(errorMessage, 800);

        Integer retryCount = jdbcTemplate.queryForObject("""
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
        WHERE id = ?
        RETURNING retry_count
        """,
                Integer.class,
                msg,
                docId
        );

        return retryCount == null ? 0 : retryCount;
    }

}
