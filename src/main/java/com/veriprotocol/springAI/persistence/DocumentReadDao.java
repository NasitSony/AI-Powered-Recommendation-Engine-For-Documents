package com.veriprotocol.springAI.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.veriprotocol.springAI.controller.api.dto.DocumentStatusDto;

@Repository
public class DocumentReadDao {

    private final JdbcTemplate jdbcTemplate;

    public DocumentReadDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<DocumentStatusDto> MAPPER = new RowMapper<>() {
        @Override
        public DocumentStatusDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new DocumentStatusDto(
                    rs.getString("id"),
                    rs.getString("status"),
                    rs.getInt("retry_count"),
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("updated_at", OffsetDateTime.class),
                    rs.getString("last_error"),
                    rs.getString("worker_id"),
                    rs.getObject("processing_started_at", OffsetDateTime.class),
                    rs.getObject("next_retry_at", OffsetDateTime.class)
            );
        }
    };


    // Internal worker lookup.
// Safe because document IDs are globally unique and this is not user-facing.
    public Optional<DocumentStatusDto> findStatusById(String id) {

        var list = jdbcTemplate.query("""
        SELECT id, status,
               COALESCE(retry_count, 0) AS retry_count,
               created_at, updated_at,
               last_error, worker_id,
               processing_started_at, next_retry_at
        FROM documents
        WHERE id = ?
        """,
                MAPPER,
                id
        );

        return list.stream().findFirst();
    }
    public Optional<DocumentStatusDto> findStatusByTenantAndId(
            String tenantId,
            String id) {
        var list = jdbcTemplate.query("""
            SELECT id, status,
                   COALESCE(retry_count, 0) AS retry_count,
                   created_at, updated_at,
                   last_error, worker_id,
                   processing_started_at, next_retry_at
            FROM documents
            WHERE tenant_id = ?
              AND id = ?
            """,
                        MAPPER,
                        tenantId,
                        id
                );

        return list.stream().findFirst();
    }

    public List<String> findRetryableFailedDocIds(int limit, int maxRetries) {
        return jdbcTemplate.queryForList("""
            SELECT id
            FROM documents
            WHERE status = 'FAILED'
              AND retry_count < ?
              AND (next_retry_at IS NULL OR next_retry_at <= now())
            ORDER BY updated_at ASC
            LIMIT ?
        """, String.class, maxRetries, limit);
    }

    public int resetFailedToPending(String docId) {
        return jdbcTemplate.update("""
            UPDATE documents
            SET status = 'PENDING',
                last_error = NULL,
                processing_started_at = NULL,
                worker_id = NULL,
                updated_at = now()
            WHERE id = ?
              AND status = 'FAILED'
        """, docId);
    }

    public record DocPayload(String id, String tenantId, String text, String contentHash) {}

    public DocPayload loadDocPayload(String id) {
        return jdbcTemplate.queryForObject("""
            SELECT id, tenant_id, text, content_hash
                        FROM documents
                        WHERE id = ?
        """, (rs, rowNum) -> new DocPayload(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("text"),
                rs.getString("content_hash")
        ), id);
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

    public List<DocumentStatusDto> listByTenantAndStatus(
            String tenantId,
            String status,
            int limit) {
    	

        int safeLimit = Math.max(1, Math.min(limit, 200));

        String sql = """
            SELECT id, status,
                   COALESCE(retry_count, 0) AS retry_count,
                   created_at, updated_at,
                   last_error, worker_id,
                   processing_started_at, next_retry_at
            FROM documents
            WHERE tenant_id = ?
              AND status = ?
            ORDER BY updated_at DESC
            LIMIT %d
            """.formatted(safeLimit);

                return jdbcTemplate.query(
                        sql,
                        MAPPER,
                        tenantId,
                        status
                );
    }

    public int countByStatus(String status) {
        Integer n = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM documents WHERE status = ?
        """, Integer.class, status);
        return n == null ? 0 : n;
    }

    
    public List<String> findPendingDocIds(int limit) {
        return jdbcTemplate.queryForList("""
            SELECT id
            FROM documents
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT ?
            """, String.class, limit);
    }

    
    public int touchPending(String id) {
        return jdbcTemplate.update("""
            UPDATE documents
            SET updated_at = now()
            WHERE id = ? AND status = 'PENDING'
            """, id);
    }

    
    public List<String> findOldPendingDocIds(int limit, int minAgeSeconds) {
        return jdbcTemplate.queryForList("""
            SELECT id
            FROM documents
            WHERE status = 'PENDING'
              AND updated_at <= now() - (? || ' seconds')::interval
            ORDER BY updated_at ASC
            LIMIT ?
        """, String.class, minAgeSeconds, limit);
    }




}

