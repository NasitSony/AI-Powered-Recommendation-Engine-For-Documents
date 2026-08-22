package com.veriprotocol.springAI.sharding;

import org.springframework.stereotype.Component;

@Component
public class ShardRouter {

    private static final int SHARD_COUNT = 2;

    public int shardFor(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException(
                    "tenantId must not be null/blank"
            );
        }

        return Math.floorMod(
                tenantId.hashCode(),
                SHARD_COUNT
        );
    }

    public String shardNameFor(String tenantId) {
        return "shard-" + shardFor(tenantId);
    }
}