package com.veriprotocol.springAI.sharding;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ShardJdbcRouter {

    private final ShardRouter shardRouter;

    private final JdbcTemplate shard0NodeA;
    private final JdbcTemplate shard0NodeB;

    private final JdbcTemplate shard1NodeA;
    private final JdbcTemplate shard1NodeB;

    private volatile JdbcTemplate shard0Current;
    private volatile JdbcTemplate shard1Current;

    public ShardJdbcRouter(
            ShardRouter shardRouter,
            @Qualifier("shard0NodeAJdbcTemplate") JdbcTemplate shard0NodeA,
            @Qualifier("shard0NodeBJdbcTemplate") JdbcTemplate shard0NodeB,
            @Qualifier("shard1NodeAJdbcTemplate") JdbcTemplate shard1NodeA,
            @Qualifier("shard1NodeBJdbcTemplate") JdbcTemplate shard1NodeB) {

        this.shardRouter = shardRouter;
        this.shard0NodeA = shard0NodeA;
        this.shard0NodeB = shard0NodeB;
        this.shard1NodeA = shard1NodeA;
        this.shard1NodeB = shard1NodeB;

        this.shard0Current = shard0NodeA;
        this.shard1Current = shard1NodeA;
    }

    public JdbcTemplate jdbcFor(String tenantId) {

        return switch (shardRouter.shardFor(tenantId)) {

            case 0 -> {
                if (isWritable(shard0Current)) {
                    yield shard0Current;
                }

                shard0Current = resolveWritable(
                        "shard-0",
                        shard0NodeA,
                        shard0NodeB
                );

                yield shard0Current;
            }

            case 1 -> {
                if (isWritable(shard1Current)) {
                    yield shard1Current;
                }

                shard1Current = resolveWritable(
                        "shard-1",
                        shard1NodeA,
                        shard1NodeB
                );

                yield shard1Current;
            }

            default ->
                    throw new IllegalStateException("Unexpected shard");
        };
    }
    private JdbcTemplate resolveWritable(
            String shardName,
            JdbcTemplate nodeA,
            JdbcTemplate nodeB) {

        if (isWritable(nodeA)) {
            return nodeA;
        }

        if (isWritable(nodeB)) {
            return nodeB;
        }

        throw new IllegalStateException(
                "No writable primary available for " + shardName
        );
    }

    private boolean isWritable(JdbcTemplate jdbc) {

        try {
            Boolean inRecovery =
                    jdbc.queryForObject(
                            "SELECT pg_is_in_recovery()",
                            Boolean.class
                    );

            return Boolean.FALSE.equals(inRecovery);

        } catch (DataAccessException ex) {
            return false;
        }
    }

    public JdbcTemplate shard0() {
        return resolveWritable(
                "shard-0",
                shard0NodeA,
                shard0NodeB
        );
    }

    public JdbcTemplate shard1() {
        return resolveWritable(
                "shard-1",
                shard1NodeA,
                shard1NodeB
        );
    }
}