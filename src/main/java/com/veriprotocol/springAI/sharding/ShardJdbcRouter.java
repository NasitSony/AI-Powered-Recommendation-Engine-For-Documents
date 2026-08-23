package com.veriprotocol.springAI.sharding;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ShardJdbcRouter {

    private final ShardRouter shardRouter;
    private final JdbcTemplate shard0;
    private final JdbcTemplate shard1;

    public ShardJdbcRouter(
            ShardRouter shardRouter,
            @Qualifier("shard0JdbcTemplate") JdbcTemplate shard0,
            @Qualifier("shard1JdbcTemplate") JdbcTemplate shard1) {

        this.shardRouter = shardRouter;
        this.shard0 = shard0;
        this.shard1 = shard1;
    }

    public JdbcTemplate jdbcFor(String tenantId) {
        return switch (shardRouter.shardFor(tenantId)) {
            case 0 -> shard0;
            case 1 -> shard1;
            default -> throw new IllegalStateException("Unexpected shard");
        };
    }

    public JdbcTemplate shard0() {
        return shard0;
    }

    public JdbcTemplate shard1() {
        return shard1;
    }
}