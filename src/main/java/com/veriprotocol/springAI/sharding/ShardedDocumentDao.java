package com.veriprotocol.springAI.sharding;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ShardedDocumentDao {

    private final ShardJdbcRouter shardJdbcRouter;

    public ShardedDocumentDao(ShardJdbcRouter shardJdbcRouter) {
        this.shardJdbcRouter = shardJdbcRouter;
    }

    public String insertTestDocument(
            String tenantId,
            String requestId,
            String text) {

        String id = UUID.randomUUID().toString();

        JdbcTemplate jdbc = shardJdbcRouter.jdbcFor(tenantId);

        jdbc.update("""
            INSERT INTO documents (
                id,
                tenant_id,
                request_id,
                text,
                status,
                retry_count,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, 'PENDING', 0, now(), now())
            """,
                id,
                tenantId,
                requestId,
                text
        );

        return id;
    }
}