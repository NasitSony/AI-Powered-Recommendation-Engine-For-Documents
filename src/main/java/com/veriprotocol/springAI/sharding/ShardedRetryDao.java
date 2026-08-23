package com.veriprotocol.springAI.sharding;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.veriprotocol.springAI.persistence.DocumentReadDao;

@Repository
public class ShardedRetryDao {

    private final ShardJdbcRouter shardJdbcRouter;

    public ShardedRetryDao(
            ShardJdbcRouter shardJdbcRouter) {
        this.shardJdbcRouter = shardJdbcRouter;
    }

    public List<DocumentReadDao.RetryableDoc> findRetryableFailedDocs(
            int limit,
            int maxRetries) {

        List<DocumentReadDao.RetryableDoc> result =
                new ArrayList<>();

        scanShard(
                shardJdbcRouter.shard0(),
                result,
                limit,
                maxRetries
        );

        if (result.size() < limit) {
            scanShard(
                    shardJdbcRouter.shard1(),
                    result,
                    limit - result.size(),
                    maxRetries
            );
        }

        return result;
    }

    private void scanShard(
            JdbcTemplate jdbc,
            List<DocumentReadDao.RetryableDoc> result,
            int limit,
            int maxRetries) {

        if (limit <= 0) {
            return;
        }

        result.addAll(
                jdbc.query("""
                    SELECT tenant_id, id
                    FROM documents
                    WHERE status = 'FAILED'
                      AND retry_count < ?
                      AND (
                            next_retry_at IS NULL
                            OR next_retry_at <= now()
                          )
                    ORDER BY updated_at ASC
                    LIMIT ?
                    """,
                        (rs, rowNum) ->
                                new DocumentReadDao.RetryableDoc(
                                        rs.getString("tenant_id"),
                                        rs.getString("id")
                                ),
                        maxRetries,
                        limit
                )
        );
    }

    public int resetFailedToPending(
            String tenantId,
            String docId) {

        JdbcTemplate jdbc =
                shardJdbcRouter.jdbcFor(tenantId);

        return jdbc.update("""
            UPDATE documents
            SET status = 'PENDING',
                last_error = NULL,
                next_retry_at = NULL,
                processing_started_at = NULL,
                worker_id = NULL,
                updated_at = now()
            WHERE tenant_id = ?
              AND id = ?
              AND status = 'FAILED'
              AND (
                    next_retry_at IS NULL
                    OR next_retry_at <= now()
                  )
            """,
                tenantId,
                docId
        );
    }
}